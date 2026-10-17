import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart' show debugPrint;
import 'package:dio/dio.dart';

/// 阿里云 OSS 直传（STS 临时凭证 + V1 签名）。
///
/// 移植自 Android `util/OSSUpload.kt` —— 那边用的是 `aliyun-oss` SDK，
/// Flutter 没有对应的 SDK，所以这里**手写签名**。
///
/// ## 流程（两步）
/// 1. `POST /v6/upload/ossUploadPrepare` 拿 STS 凭证 + **服务端分配的对象名**
/// 2. `PUT https://{endPoint}/{bucket}/{object}` 直传，成功后服务端回调
///
/// ## 签名（OSS V1）
/// ```
/// signature = base64( HMAC-SHA1(accessKeySecret, StringToSign) )
///
/// StringToSign =
///   "PUT\n"
///   + Content-MD5 + "\n"          // 图片 md5 的 **base64**（不是 hex！）
///   + Content-Type + "\n"
///   + Date + "\n"                 // RFC1123 GMT，OSS 校验 ±15 分钟
///   + CanonicalizedOSSHeaders     // 所有 x-oss-* 头，**按名字字典序**，每行 "key:value\n"
///   + "/{bucket}/{object}"        // 注意 bucket 前**有斜杠**
/// ```
///
/// ## 三个容易错的地方
/// 1. `Content-MD5` 是 **base64(md5(bytes))** —— 不是 md5 的十六进制串 ✗
/// 2. `CanonicalizedOSSHeaders` **只含 `x-oss-*`**（含 `x-oss-security-token`），
///    按名字**字典序**，值里多层空格要压成一个
/// 3. `x-oss-callback` 的 JSON 里 `callbackBody` 是**嵌套的字符串**（要转义）
class OssUpload {
  OssUpload._();

  /// 上传一张图，返回服务端分配的 **objectName**（= `uploadFileName`）。
  ///
  /// [prepare] 是 `ossUploadPrepare` 返回的 `uploadPrepareInfo` + 单项 `fileInfo`。
  /// [versionCode] 必须与请求头 `X-App-Code` 一致（服务端会校验）。
  static Future<String> put({
    required String endPoint,
    required String bucket,
    required String objectName,
    required String accessKeyId,
    required String accessKeySecret,
    required String securityToken,
    required List<int> bytes,
    required String mime,
    required String versionCode,
  }) async {
    final host = endPoint.replaceFirst(RegExp(r'^https?://'), '');

    // ① Content-MD5 = base64(md5(bytes)) —— 注意是 base64，不是 hex
    final md5Digest = md5.convert(bytes);
    final contentMd5 = base64Encode(md5Digest.bytes);

    // ② x-oss-callback：base64(JSON)，callbackBody 里是嵌套字符串
    final callbackJson = '{'
        '"callbackBodyType":"application/json",'
        '"callbackHost":"api.coolapk.com",'
        '"callbackUrl":"https://api.coolapk.com/v6/callback/'
        'mobileOssUploadSuccessCallback?checkArticleCoverResolution=0'
        '&versionCode=$versionCode",'
        '"callbackBody":"{\\"bucket\\":$bucket,'
        '\\"object\\":\${object},\\"hasProcess\\":\${x:var1}}"'
        '}';
    final ossCallback = base64Encode(utf8.encode(callbackJson));

    // ③ 固定值（Android 里写死的 `{"x:var1":"false"}` 的 base64）
    const ossCallbackVar = 'eyJ4OnZhcjEiOiJmYWxzZSJ9';

    // ④ Date 必须是真实的 GMT
    final date = _httpDate(DateTime.now().toUtc());

    // ⑤ CanonicalizedOSSHeaders：只含 x-oss-*，字典序
    final ossHeaders = <String, String>{
      'x-oss-callback': ossCallback,
      'x-oss-callback-var': ossCallbackVar,
      'x-oss-security-token': securityToken,
    };
    final names = ossHeaders.keys.toList()..sort();
    final canonical = names
        .map((k) => '$k:${_normalizeSpaces(ossHeaders[k]!)}\n')
        .join();

    // ⑥ StringToSign
    final stringToSign = 'PUT\n'
        '$contentMd5\n'
        '$mime\n'
        '$date\n'
        '$canonical'
        '/$bucket/$objectName';

    // ⑦ 签名
    final hmac = Hmac(sha1, utf8.encode(accessKeySecret));
    final signature = base64Encode(hmac.convert(utf8.encode(stringToSign)).bytes);

    // ⑧ 发请求
    final dio = Dio(BaseOptions(
      connectTimeout: const Duration(minutes: 5),
      receiveTimeout: const Duration(minutes: 5),
    ));
    // ★ URL 必须是 `https://{bucket}.{host}/{objectName}`
    //   （Android 的 `aliyun-oss` SDK 就是这么拼的：
    //    `OSSClient(context, endPoint, ...)` + `PutObjectRequest(bucket, object, uri)`）
    //   之前 PUT 到 `https://$host` 少了 bucket 前缀和对象路径 ✗ —— 会 403/404
    final putUrl = 'https://$bucket.$host/$objectName';
    debugPrint('OssUpload: PUT $putUrl (${bytes.length} B)');

    final res = await dio.put(
      putUrl,
      data: bytes,
      options: Options(
        headers: {
          'Content-Type': mime,
          'Content-MD5': contentMd5,
          'Date': date,
          'x-oss-security-token': securityToken,
          'x-oss-callback': ossCallback,
          'x-oss-callback-var': ossCallbackVar,
          'Authorization': 'OSS $accessKeyId:$signature',
          'Content-Length': bytes.length,
        },
      ),
    );

    if (res.statusCode == null || res.statusCode! < 200 || res.statusCode! >= 300) {
      throw Exception('OSS 上传失败：HTTP ${res.statusCode} / ${res.data}');
    }
    // OSS 的 callback 成功时返回的是服务端回调的结果（JSON），这里只关心 2xx
    debugPrint('OssUpload: OK ${res.statusCode} ${res.data}');
    return objectName;
  }

  /// OSS 要求 `x-oss-*` 头的值里连续空白压成一个空格
  static String _normalizeSpaces(String v) =>
      v.replaceAll(RegExp(r'\s+'), ' ').trim();

  /// RFC1123 GMT，例如 `Fri, 17 Oct 2026 12:00:00 GMT`
  static String _httpDate(DateTime utc) {
    const wd = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    const mo = [
      'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
      'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
    ];
    String two(int n) => n.toString().padLeft(2, '0');
    return '${wd[utc.weekday - 1]}, ${two(utc.day)} ${mo[utc.month - 1]} '
        '${utc.year} ${two(utc.hour)}:${two(utc.minute)}:${two(utc.second)} GMT';
  }
}

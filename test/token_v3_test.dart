import 'dart:convert';

import 'package:c001apk_flutter/utils/token_util.dart';
import 'package:flutter_test/flutter_test.dart';

/// X-App-Token v3 的 golden 向量测试。
///
/// ## 为什么这个测试重要
/// 服务端对 `X-App-Token` 是**严格校验**的（见 `_rev/win_ref/README.md` §2.3）：
/// | 送的东西 | 响应 |
/// |---|---|
/// | 有效 token | 本人数据 |
/// | 伪造 / 缺失 | `请求未验证` |
/// | token 与 `X-App-Code` 不一致 | **HTTP 403** |
///
/// 所以签名错一个字节，整个登录态就全废，而且**报错信息不会告诉你哪里错**。
/// 这里用固定输入把输出钉死，任何偏差都会在 CI 上立刻暴露。
///
/// ## golden 值的来源
/// 由规范实现 `_rev/check_v3_port.py`（内部调用 `_rev/v3_token.py`）在固定
/// 输入下打印，并与线上 Kotlin `TokenDeviceUtils.getTokenV3` 语义一致：
/// ```
/// DEVICE = lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4...MVFR
/// TS     = 1789000000
/// VC     = 2607021
/// OFF    = 212
/// B2_LEN = 96
/// MS     = f1903a6dc7a6c49dfb211484f788f6ab
/// SALT   = NmFhMWY5NDAvZjE5MDNhNm
/// MKEY   = 800b170d9fefeffa3d41b70f00e5ab27
/// BCRYPT = $2y$04$NmFhMWY5NDAvZjE5MDNhNex5sXVmIJMKe9qg8gl9yK9TBuj7LWidK
/// TOKEN  = v3JDJ5JDA0JE5tRmhNV1k1TkRBdlpqRTVNRE5oTmV4NXNYVm1JSk1LZTlxZzhnbDl5SzlUQnVqN0xXaWRL
/// ```
void main() {
  /// 与 `_rev/check_v3_port.py` 完全相同的固定输入
  const device =
      'lVDMjRWN2IzYjVTN3MmN2EDOiNWZjdjNhJTNkNTO3EWR4UzQzIkQ1EzN1YTMERTQFdjQ0cTMCF0'
      'QxYjNEJzM4AyOpEDMONEKzAzNuUjLw4iNx8FMxEjWKBFI7ATMxolSQByOzVHbQVmbPByOzVHbQVmbP'
      'ByOgsDI7AyO3c2Xa9WaThFbxZTb0pXQplVW3FWMIJWWrZHZBdFNol1NMVFR';
  const ts = 1789000000;
  const vc = '2607021';

  const goldenToken =
      'v3JDJ5JDA0JE5tRmhNV1k1TkRBdlpqRTVNRE5oTmV4NXNYVm1JSk1LZTlxZzhnbDl5SzlUQnVqN0xXaWRL';

  group('X-App-Token v3', () {
    test('golden 向量逐字节一致', () {
      final token = TokenUtils.getTokenV3(device, versionCode: vc, timestamp: ts);
      expect(token, goldenToken);
    });

    test('token 以 v3 开头，且是 80 字符 base64', () {
      final token = TokenUtils.getTokenV3(device, versionCode: vc, timestamp: ts);
      expect(token.startsWith('v3'), isTrue);
      // bcrypt 哈希是 60 字节 → base64 后 80 字符（无填充）
      expect(token.length, 2 + 80);
      // base64 解码回来应以 $2y$04$ 开头（bcrypt 的标识）
      final decoded = utf8.decode(base64Decode(token.substring(2)));
      expect(decoded.startsWith(r'$2y$04$'), isTrue);
      expect(decoded.length, 60);
    });

    test('时间戳不同则 token 不同（确认 t 真的参与了运算）', () {
      final a = TokenUtils.getTokenV3(device, versionCode: vc, timestamp: ts);
      final b = TokenUtils.getTokenV3(device, versionCode: vc, timestamp: ts + 1);
      expect(a == b, isFalse);
    });

    test('versionCode 不同则 token 不同（铁律 2：必须与 X-App-Code 一致）', () {
      final a = TokenUtils.getTokenV3(device, versionCode: vc, timestamp: ts);
      final b = TokenUtils.getTokenV3(device, versionCode: '2607022', timestamp: ts);
      expect(a == b, isFalse);
    });

    test('device 不同则 token 不同（铁律 2：必须与 X-App-Device 一致）', () {
      final a = TokenUtils.getTokenV3(device, versionCode: vc, timestamp: ts);
      final b = TokenUtils.getTokenV3('${device}x', versionCode: vc, timestamp: ts);
      expect(a == b, isFalse);
    });
  });

  group('X-App-Device 编解码', () {
    // 与 _rev/win_ref/README.md §2.2 描述一致：
    //   encode: UTF-8 → base64 → 整体反转 → 去掉 \r\n 和 '='
    //   decode: 反转 → 补 '=' → base64 解码 → UTF-8
    const plain = 'DUl8Stv1zMwbBXYCvIwaKZIfmakbFbgBhDgb; ; ; ; OnePlus; OnePlus; PJZ110; PJZ110_16.0.5.703(CN01); 832D661CAB';

    test('encode 后不含 = 与换行', () async {
      final encoded = await TokenUtils.encodeDevice(plain);
      expect(encoded.contains('='), isFalse);
      expect(encoded.contains('\n'), isFalse);
      expect(encoded.contains('\r'), isFalse);
    });

    test('encode → decode 往返一致', () async {
      final encoded = await TokenUtils.encodeDevice(plain);
      expect(TokenUtils.decodeDevice(encoded), plain);
    });
  });
}

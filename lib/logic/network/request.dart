import 'dart:async';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:dio/io.dart' show IOHttpClientAdapter;

import '../../logic/network/interceptor.dart';
import '../../utils/ssl_verify.dart';

class Request {
  static final Request _instance = Request._internal();
  static late final Dio dio;
  factory Request() => _instance;

  Request._internal() {
    dio = Dio()
      ..options.connectTimeout = const Duration(milliseconds: 5000)
      ..options.receiveTimeout = const Duration(milliseconds: 5000)
      ..interceptors.add(ApiInterceptor());

    // ★ SSL 证书校验（对齐 Android `util/SslVerify.kt`）
    //
    // Dio 默认的 `HttpClient` 用系统根做校验。这里换成自己的 adapter：
    // - 「校验 SSL 证书」开着 → 只信**内置的 122 张 CA**
    //   （抓包证书 / 私有根 / 被替换的 CA 一律拒绝）；
    // - 关掉 → 系统默认；
    // - 「网络传输调试模式」开着 → 彻底放行（方便抓包）。
    //
    // `SecurityContext` 是启动时 `SslVerify.init()` 预先读好的 ——
    // 这个回调是**同步**的，里面不能 `await rootBundle`。
    dio.httpClientAdapter = IOHttpClientAdapter(
      onHttpClientCreate: (_) {
        final client = HttpClient(context: SslVerify.contextForClient());
        if (SslVerify.debugMode) {
          // 调试模式：连域名不匹配也放行（Android 那边是装了全放行的
          // TrustManager + hostnameVerifier）
          client.badCertificateCallback = (cert, host, port) => true;
        }
        return client;
      },
    );
  }

  Future<Response<dynamic>> get(
    String url, {
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    extra,
  }) async {
    return await dio.get(
      url,
      queryParameters: queryParameters,
      options: options,
      cancelToken: cancelToken,
    );
  }

  Future<Response<dynamic>> post(
    String url, {
    Object? data,
    Map<String, dynamic>? queryParameters,
    Options? options,
    CancelToken? cancelToken,
    extra,
  }) async {
    return await dio.post(
      url,
      data: data,
      queryParameters: queryParameters,
      options: options,
      cancelToken: cancelToken,
    );
  }
}

// ignore_for_file: non_constant_identifier_names

import 'dart:io';

import 'package:dio/dio.dart';
import 'package:get/get.dart' hide Response;
import "package:flutter/foundation.dart";

import '../../constants/constants.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/model/check_info/check.dart';
import '../../utils/extensions.dart';
import '../../utils/global_data.dart';
import '../../utils/storage_util.dart';

class MainController extends GetxController {
  /// 启动自检：① 跟随官方最新版本号 ② 校验登录态。
  ///
  /// **顺序不能反** —— 对齐 Android `MainActivity.genData()` →
  /// `MainViewModel.fetchAppInfo()`：它拉完官方版本号后，在同一个协程里才调
  /// `getCheckLoginInfo()`。因为 versionCode 参与 v3 token 运算，且必须与
  /// `X-App-Code` 自洽，所以要先把它校准好，登录态检查才带得上正确的头。
  Future<void> initAndCheckLogin() async {
    await followOfficialVersion();
    await checkLoginInfo();
  }

  /// 把客户端版本号对齐到**官方酷安 App 的最新版本**。
  ///
  /// 对应 Android `MainViewModel.fetchAppInfo(id)`：
  /// `GET /v6/apk/detail?id=com.coolapk.market&installed=1`
  /// → 取 `data.apkversionname` / `data.apkversioncode`。
  ///
  /// 为什么需要：`X-App-Token`(v3) 的取窗与盐都用到 versionCode，
  /// 且必须与 `X-App-Code` 一致；客户端版本太旧服务端会拒。
  /// 写死版本号会随时间失效，跟着官方走才耐用。
  ///
  /// 空值不覆盖 —— Android 注释：「空值不覆盖，避免生成无效 token」。
  Future<void> followOfficialVersion() async {
    try {
      final response = await NetworkRepo.apkDetail('com.coolapk.market');
      if (response.statusCode != HttpStatus.ok || response.data == null) {
        return;
      }
      final data = (response.data as Map)['data'];
      if (data is! Map) return;
      final name = data['apkversionname']?.toString();
      final code = data['apkversioncode']?.toString();
      if (name != null && name.isNotEmpty) GStorage.setVersionName(name);
      if (code != null && code.isNotEmpty) GStorage.setVersionCode(code);
      // API_VERSION 固定跟随常量（Android 同此）
      GStorage.setApiVersion(Constants.API_VERSION);
      // device 与 UA 必须配套
      GStorage.fullSetUserAgent();
      debugPrint('followOfficialVersion: $name($code)');
    } catch (e) {
      // 拿不到就沿用当前值，不能因此影响启动
      debugPrint('followOfficialVersion failed: $e');
    }
  }

  Future<void> checkLoginInfo() async {
    Response response = await NetworkRepo.checkLoginInfo();
    try {
      if (response.statusCode == HttpStatus.ok) {
        CheckInfo responseData = CheckInfo.fromJson(response.data);
        if (!responseData.message.isNullOrEmpty) {
          debugPrint(response.data['message']);
          if (response.data['message'] == '登录信息有误') {
            GStorage.setUid('');
            GStorage.setUsername('');
            GStorage.setToken('');
            GStorage.setIsLogin(false);
          }
        } else {
          if (responseData.data != null) {
            GStorage.setUid(responseData.data!.uid.orEmpty);
            GStorage.setUsername(
                Uri.encodeComponent(responseData.data!.username.orEmpty));
            GStorage.setToken(responseData.data!.token.orEmpty);
            GStorage.setIsLogin(true);
          } else {
            debugPrint('null');
          }
        }
      } else {
        debugPrint('statusCode: ${response.statusCode}');
      }
    } catch (e) {
      debugPrint('failed to get token: ${e.toString()}');
    }

    try {
      String? SESSID = response.headers['Set-Cookie']?[0];
      if (SESSID != null) {
        GlobalData().SESSID = SESSID.substring(0, SESSID.indexOf(';'));
      }
    } catch (e) {
      debugPrint('failed to get SESSID: ${e.toString()}');
    }
  }
}

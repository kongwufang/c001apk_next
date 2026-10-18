import 'dart:io';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:path_provider/path_provider.dart';

import '../constants/constants.dart';
import '../logic/model/fav_history/fav_history.dart';
import '../utils/device_util.dart';
import '../utils/global_data.dart';
import '../utils/token_util.dart';
import 'package:uuid/uuid.dart';

class GStorage {
  static late final Box<dynamic> searchHistory;
  static late final Box<dynamic> historyFeed;
  static late final Box<dynamic> favFeed;
  static late final Box<dynamic> blackList;
  static late final Box<dynamic> settings;

  /// 离线缓存存档。对应 Android 的 Room `OfflineCacheDatabase`，
  /// 一条存档 = 一个 Map，按帖子 id（`fid`）作 key。
  static late final Box<dynamic> offlineCache;

  static ThemeMode getThemeMode() {
    switch (settings.get(SettingsBoxKey.selectedTheme, defaultValue: 0)) {
      case 0:
        return ThemeMode.system;

      case 1:
        return ThemeMode.light;

      case 2:
        return ThemeMode.dark;

      default:
        return ThemeMode.system;
    }
  }

  static Brightness getBrightness() {
    switch (settings.get(SettingsBoxKey.selectedTheme, defaultValue: 0)) {
      case 0:
        if (PlatformDispatcher.instance.platformBrightness ==
            Brightness.light) {
          return Brightness.dark;
        } else {
          return Brightness.light;
        }

      case 1:
        return Brightness.dark;

      case 2:
        return Brightness.light;

      default:
        return Brightness.dark;
    }
  }

  static void onDeleteFeed(String id, {bool isHistory = true}) {
    if (isHistory) {
      historyFeed.delete(id);
    } else {
      favFeed.delete(id);
    }
  }

  static bool checkFav(String id) {
    return favFeed.get(id) != null;
  }

  static bool checkHistory(String id) {
    return historyFeed.get(id) != null;
  }

  static bool checkUser(String uid) {
    List<String> userBlackList =
        blackList.get(BlackListBoxKey.userBlackList, defaultValue: <String>[]);
    return userBlackList.contains(_toString(uid));
  }

  static bool checkTopic(String topic) {
    List<String> topicBlackList =
        blackList.get(BlackListBoxKey.topicBlackList, defaultValue: <String>[]);
    return topicBlackList
            .firstWhereOrNull((keyword) => topic.contains(keyword)) !=
        null;
  }

  static String _toString(dynamic value) {
    return value is String ? value : value.toString();
  }

  static void onBlock(
    dynamic value, {
    bool isUser = true,
    bool isDelete = false,
    bool needToast = false,
  }) {
    List<String> dataList = blackList.get(
      isUser ? BlackListBoxKey.userBlackList : BlackListBoxKey.topicBlackList,
      defaultValue: <String>[],
    );
    if (!isDelete && dataList.contains(_toString(value))) {
      if (needToast) {
        SmartDialog.showToast('已存在');
      }
      return;
    }
    if (isDelete) {
      dataList = dataList.where((data) => data != _toString(value)).toList();
    } else {
      dataList.insert(0, _toString(value));
    }
    blackList.put(
      isUser ? BlackListBoxKey.userBlackList : BlackListBoxKey.topicBlackList,
      dataList,
    );
  }

  static Future<void> init() async {
    final Directory dir = await getApplicationSupportDirectory();
    final String path = dir.path;
    await Hive.initFlutter('$path/hive');
    regAdapter();
    searchHistory = await Hive.openBox('searchHistory');
    historyFeed = await Hive.openBox('historyFeed');
    favFeed = await Hive.openBox('favFeed');
    blackList = await Hive.openBox('blackList');
    settings = await Hive.openBox('settings');
    // 离线缓存（对应 Android 的 Room `OfflineCacheDatabase`）：
    // 一条存档存成一个 Map，按帖子 id 作 key。
    offlineCache = await Hive.openBox('offlineCache');

    if (installTime.isEmpty) {
      setInstallTime(DateTime.now().millisecondsSinceEpoch.toString());
      await regenerateParams();
    }

    // ★ 存量参数迁移（详见 migrateParams 的注释）——必须在 Request() 之前跑完，
    //   否则首个请求会带着旧参数发出去。
    await migrateParams();

    GlobalData().isLogin = isLogin;
    GlobalData().uid = uid;
    GlobalData().username = username;
    GlobalData().token = token;
    // 第二凭据也从本地恢复（首次启动时为空，等 checkLoginInfo 换到新的）
    GlobalData().refreshToken =
        settings.get(SettingsBoxKey.refreshToken, defaultValue: '');
  }

  /// 机型参数版本号。
  ///
  /// 改动「默认参数」时 **+1**，老安装会在下次启动自动重建 ——
  /// 对应 Android `TokenDeviceUtils.FINGERPRINT_VERSION` 的做法。
  ///
  /// ## 为什么必须有这个
  /// [init] 只在**首次运行**生成参数并持久化，以后一直用存下来的那份。
  /// 于是升级客户端时，存量的旧参数会**永久覆盖新常量**，
  /// 表现为「代码改了但请求还是被服务端拒」。
  ///
  /// 实测踩到（读 `%APPDATA%\Integral\c001apk-flutter\hive\settings.hive` 得到）：
  /// ```
  /// versionNamev = 13.4.1        ← 旧版本号
  /// versionCode  = 2312121       ← 旧版本号
  /// apiVersion   = (空)          ← 空头
  /// xAppDevice   = sxWduByOBVkR2... ← 随机 MAC 拼出来的畸形设备串
  /// ```
  /// 结果：首页返回 **「请求方式错误(1)」**。
  ///
  /// v2 的期望值：版本号 `16.6.4 / 2609291 / API16`，
  /// 设备串用 [Constants.DEFAULT_DEVICE_CODE]（首字段 szlmId 是服务端**已注册**的值；
  /// 随机假值会拿到 `-419 检测到当前版本存在异常`）。
  ///
  /// v3：**配色表从 19 项改成 20 项**（补「深紫」、把「樱花」挪到第 2），
  /// 需要把存量 `staticColor` 按颜色身份重映射（见下面的 colorIndexRemap）。
  static const PARAMS_VERSION = 3;
  static const _paramsVersionKey = 'paramsVersion';

  /// 把持久化的机型参数迁到当前期望值（幂等）。
  static Future<void> migrateParams() async {
    final stored = settings.get(_paramsVersionKey, defaultValue: 0) as int;
    if (stored >= PARAMS_VERSION) return;

    setVersionName(Constants.VERSION_NAME);
    setVersionCode(Constants.VERSION_CODE);
    setApiVersion(Constants.API_VERSION);
    setManufacturer(Constants.DEFAULT_MANUFACTURER);
    setBrand(Constants.DEFAULT_BRAND);
    setModel(Constants.DEFAULT_MODEL);
    setBuildNumber(Constants.DEFAULT_BUILDNUMBER);
    setSdkInt(Constants.SDK_INT);
    setAndroidVersion(Constants.DEFAULT_ANDROID_VERSION);
    fullSetUserAgent();

    // 设备串换回**已验证可用**的那份（原来的随机串会被服务端拒）。
    // 若用户已经取到真的 szlmId，只把它注入首字段、其余保留
    // —— 对齐 Android `TokenDeviceUtils.migrateSzlmId`。
    var device = Constants.DEFAULT_DEVICE_CODE;
    final id = szlmId;
    if (id.isNotEmpty) {
      try {
        final parts = TokenUtils.decodeDevice(device).split(';');
        if (parts.length >= 8) {
          parts[0] = id;
          device = await TokenUtils.encodeDevice(parts.join(';'));
        }
      } catch (_) {
        // 解不出结构就用默认设备串
      }
    }
    setXAppDevice(device);

    // ── 配色下标迁移（v3）──
    //
    // `staticColor` 存的是 [Constants.seedColors] 的**下标**。上游那张表是 19 项
    // （缺「深紫」、「樱花」在末位），而 Android 是 20 项且「樱花」排第 2。
    // 直接换表会让老用户已选的配色变成另一个颜色，所以按**颜色身份**重映射。
    final storedColor =
        settings.get(SettingsBoxKey.staticColor, defaultValue: 0) as int;
    if (storedColor >= 0 && storedColor < Constants.colorIndexRemap.length) {
      settings.put(
          SettingsBoxKey.staticColor, Constants.colorIndexRemap[storedColor]);
    }

    settings.put(_paramsVersionKey, PARAMS_VERSION);
  }

  static void regAdapter() {
    Hive.registerAdapter(FavHistoryItemAdapter());
  }

  static Future<void> close() async {
    searchHistory.compact();
    searchHistory.close();
    historyFeed.compact();
    historyFeed.close();
    favFeed.compact();
    favFeed.close();
    blackList.compact();
    blackList.close();
    settings.compact();
    settings.close();
  }

  static void fullSetUserAgent() {
    setUserAgent(
        'Dalvik/2.1.0 (Linux; U; Android $androidVersion; $model $buildNumber) (#Build; $brand; $model; $buildNumber; $androidVersion) +CoolMarket/$versionName-$versionCode-${Constants.MODE}');
  }

  /// 重建设备串。
  ///
  /// ★ 对齐 Android `TokenDeviceUtils.buildDeviceCode`：以**已验证可用**的
  /// [Constants.DEFAULT_DEVICE_CODE] 为骨架，**只替换** 5 个字段：
  /// `[0]=szlmId`、`[4]=厂商`、`[5]=品牌`、`[6]=型号`、`[7]=版本号`；
  /// **MAC（[3]）与尾部 hex（[8]）一律沿用骨架里的值**。
  ///
  /// 为什么不能整串新造（上游原实现是那样）：
  /// Android `Constants.DEFAULT_DEVICE_CODE` 的注释与 `_rev/win_ref/README.md`
  /// 都记录了实测结论 —— 连这些结构性字段一起新造的整串会被风控要求人机验证
  /// （`err_request_captcha_v2`），详情页等接口直接加载失败；
  /// 只换 szlmId + 机型则 `main/indexV8`、`user/profile`、`search`、
  /// `feed/replyList` 均正常。
  static Future<void> fullSetXAppDevice() async {
    final parts =
        TokenUtils.decodeDevice(Constants.DEFAULT_DEVICE_CODE).split(';');
    if (parts.length < 9) {
      // 骨架异常就退回原值，不要造出更糟的串
      setXAppDevice(Constants.DEFAULT_DEVICE_CODE);
      return;
    }
    parts[0] = szlmId.isEmpty ? DeviceUtil.randHexString(32) : szlmId;
    parts[4] = ' $manufacturer';
    parts[5] = ' $brand';
    parts[6] = ' $model';
    parts[7] = ' $buildNumber';
    setXAppDevice(await TokenUtils.encodeDevice(parts.join(';')));
  }

  static Future<void> regenerateParams() async {
    setVersionName(Constants.VERSION_NAME);
    setApiVersion(Constants.API_VERSION);
    setVersionCode(Constants.VERSION_CODE);
    setManufacturer(DeviceUtil.randomManufacturer());
    setBrand(DeviceUtil.randomBrand());
    setModel(DeviceUtil.randomDeviceModel());
    setBuildNumber(DeviceUtil.randHexString(32));
    setSdkInt(DeviceUtil.randomSdkInt());
    setAndroidVersion(DeviceUtil.randomAndroidVersionRelease());
    fullSetUserAgent();
    await fullSetXAppDevice();
  }

  static bool get useMaterial {
    return settings.get(SettingsBoxKey.useMaterial, defaultValue: true);
  }

  static void setUseMaterial(bool value) {
    settings.put(SettingsBoxKey.useMaterial, value);
  }

  /// 纯黑主题（对齐 Android `blackDarkTheme`）：深色模式下用纯黑底
  static bool get blackDarkTheme {
    return settings.get(SettingsBoxKey.blackDarkTheme, defaultValue: false);
  }

  static void setBlackDarkTheme(bool value) {
    settings.put(SettingsBoxKey.blackDarkTheme, value);
  }

  /// 深色模式压暗图片（对齐 Android `isColorFilter`）
  static bool get isColorFilter {
    return settings.get(SettingsBoxKey.isColorFilter, defaultValue: true);
  }

  static void setIsColorFilter(bool value) {
    settings.put(SettingsBoxKey.isColorFilter, value);
  }

  /// 转场曲线：`0=linear 1=m2 2=std 3=emph 4=spring`
  ///
  /// 默认 **2 (std)** —— 台账里记的「用户拍板 std + slide + 400ms，权威」。
  /// 注：Android `settings.xml` 里声明的默认值是 `emph` / `300`，
  /// 但那是「选项默认值」，用户 2026-10-06 真机实测后拍板的是 std / 400，
  /// 所以这里取后者（保持现有观感不变），下拉框里仍可切到 emph。
  static int get animCurve {
    return settings.get(SettingsBoxKey.animCurve, defaultValue: 2);
  }

  static void setAnimCurve(int value) {
    settings.put(SettingsBoxKey.animCurve, value);
  }

  /// 转场类型：`0=slide 1=fade 2=none`
  static int get animType {
    return settings.get(SettingsBoxKey.animType, defaultValue: 0);
  }

  static void setAnimType(int value) {
    settings.put(SettingsBoxKey.animType, value);
  }

  /// 转场进入时长（毫秒）：100 / 200 / 300 / 400 / 500
  static int get animDuration {
    return settings.get(SettingsBoxKey.animDuration, defaultValue: 400);
  }

  static void setAnimDuration(int value) {
    settings.put(SettingsBoxKey.animDuration, value);
  }

  /// 首页顶部 tab 的**启用顺序**（元素是 `TabType` 的枚举名）。
  ///
  /// 对齐 Android `HomeMenu` 表（`position` + `isEnable`）：
  /// 默认 7 项「关注 / 应用 / 头条 / 热榜 / 话题 / 数码 / 酷图」。
  ///
  /// 存成「有序的启用名单」而不是「每项带 position + enable」：
  /// - 不在名单里 = 已禁用，一个字段表达两件事；
  /// - 表结构不会随 tab 增减而错位。
  static List<String> get homeMenu {
    final v = settings.get(SettingsBoxKey.homeMenu);
    if (v is List) return v.map((e) => e.toString()).toList();
    return defaultHomeMenu;
  }

  static void setHomeMenu(List<String> value) {
    settings.put(SettingsBoxKey.homeMenu, value);
  }

  /// Android `HomeViewModel.defaultList` 的顺序
  static const List<String> defaultHomeMenu = [
    'FOLLOW', // 关注
    'APP', // 应用（仅 Android）
    'FEED', // 头条
    'HOT', // 热榜
    'TOPIC', // 话题
    'PRODUCT', // 数码
    'COOLPIC', // 酷图
  ];

  /// 校验 SSL 证书（默认开 —— Android `settings.xml` 里 `defaultValue="true"`）
  static bool get sslVerify {
    return settings.get(SettingsBoxKey.sslVerify, defaultValue: true);
  }

  static void setSslVerify(bool value) {
    settings.put(SettingsBoxKey.sslVerify, value);
  }

  /// 网络传输调试模式（默认关）。
  ///
  /// Android 注释：与严格校验**互斥**，开启后彻底跳过校验方便抓包调试。
  static bool get sslDebug {
    return settings.get(SettingsBoxKey.sslDebug, defaultValue: false);
  }

  static void setSslDebug(bool value) {
    settings.put(SettingsBoxKey.sslDebug, value);
  }

  static int get staticColor {
    return settings.get(SettingsBoxKey.staticColor, defaultValue: 0);
  }

  static void setStaticColor(int value) {
    settings.put(SettingsBoxKey.staticColor, value);
  }

  static int get selectedTheme {
    return settings.get(SettingsBoxKey.selectedTheme, defaultValue: 0);
  }

  static void setSelectedTheme(int value) {
    settings.put(SettingsBoxKey.selectedTheme, value);
  }

  static String get szlmId {
    return settings.get(SettingsBoxKey.szlmId, defaultValue: '');
  }

  static void setSzlmId(String value) {
    settings.put(SettingsBoxKey.szlmId, value);
  }

  /// 保存数字联盟 ID，并把设备串的**首字段**换成它。
  ///
  /// 对齐 Android `SzlmIdPrompt.save` + `TokenDeviceUtils.buildDeviceCode`：
  /// **只替换首字段 szlmId**，机型 / MAC / 尾字段一律保留。
  /// 原因（Android `Constants.DEFAULT_DEVICE_CODE` 注释与 `_rev/win_ref/README.md` §2.4）：
  /// - 首字段是服务端认设备的 szlmId，换成**已注册**的值才放行；
  /// - 而把整串连机型/尾字段一起新造，会被风控要求人机验证
  ///   （`err_request_captcha_v2`），详情页等接口直接加载失败。
  static Future<void> saveSzlmId(String duid) async {
    final id = duid.trim();
    if (id.isEmpty) return;
    setSzlmId(id);
    // 首次运行还没有设备串时，用已验证可用的那份当底
    var device = xAppDevice;
    if (device.isEmpty) device = Constants.DEFAULT_DEVICE_CODE;
    try {
      final parts = TokenUtils.decodeDevice(device).split(';');
      if (parts.length >= 8) {
        parts[0] = id;
        setXAppDevice(await TokenUtils.encodeDevice(parts.join(';')));
      }
    } catch (_) {
      // 解不出结构（畸形串）就只存 szlmId，不硬改设备串
    }
  }

  /// SSAID —— 对应 Android 的 `Settings.Secure.ANDROID_ID`。
  ///
  /// 用途：让取号服务端唯一认出这台设备（见 `SzlmIdParams.deviceInfoJson()`）。
  /// Android 是从系统读的真实值；桌面端没有这个概念，于是**首次访问时生成一次
  /// 并持久化**，之后恒定不变 —— 必须稳定，否则服务端每次派生出的指纹都不同，
  /// 会白耗取号额度。
  static String get ssaid {
    final existing = settings.get(SettingsBoxKey.ssaid, defaultValue: '') as String;
    if (existing.isNotEmpty) return existing;
    final fresh = DeviceUtil.randHexString(16).toLowerCase();
    settings.put(SettingsBoxKey.ssaid, fresh);
    return fresh;
  }

  /// 其他屏蔽项（关键字/用户/节点）的配置原文 JSON。
  ///
  /// 对应 Android `PrefManager.spamConfig`。**冷启动没网也要能过滤**，
  /// 所以缓存的是服务端原文，拿到网络结果后再覆盖。
  static String get spamConfig {
    return settings.get(SettingsBoxKey.spamConfig, defaultValue: '') as String;
  }

  static void saveSpamConfig(String raw) {
    settings.put(SettingsBoxKey.spamConfig, raw);
  }

  static double get fontScale {
    return settings.get(SettingsBoxKey.fontScale, defaultValue: 1.0);
  }

  static void setFontScale(double value) {
    settings.put(SettingsBoxKey.fontScale, value);
  }

  static int get followType {
    return settings.get(SettingsBoxKey.followType, defaultValue: 0);
  }

  static void setFollowType(int value) {
    settings.put(SettingsBoxKey.followType, value);
  }

  static int get imageQuality {
    return settings.get(SettingsBoxKey.imageQuality, defaultValue: 0);
  }

  static void setImageQuality(int value) {
    settings.put(SettingsBoxKey.imageQuality, value);
  }

  static bool get imageDim {
    return settings.get(SettingsBoxKey.imageDim, defaultValue: true);
  }

  static void setImageDim(bool value) {
    settings.put(SettingsBoxKey.imageDim, value);
  }

  static bool get openInBrowser {
    return settings.get(SettingsBoxKey.openInBrowser, defaultValue: false);
  }

  static void setOpenInBrowser(bool value) {
    settings.put(SettingsBoxKey.openInBrowser, value);
  }

  static bool get showSquare {
    return settings.get(SettingsBoxKey.showSquare, defaultValue: true);
  }

  static void setShowSquare(bool value) {
    settings.put(SettingsBoxKey.showSquare, value);
  }

  static bool get recordHistory {
    return settings.get(SettingsBoxKey.recordHistory, defaultValue: true);
  }

  static void setRecordHistory(bool value) {
    settings.put(SettingsBoxKey.recordHistory, value);
  }

  static bool get showEmoji {
    return settings.get(SettingsBoxKey.showEmoji, defaultValue: true);
  }

  static void setShowEmoji(bool value) {
    settings.put(SettingsBoxKey.showEmoji, value);
  }

  static bool get checkUpdate {
    return settings.get(SettingsBoxKey.checkUpdate, defaultValue: true);
  }

  static void setCheckUpdate(bool value) {
    settings.put(SettingsBoxKey.checkUpdate, value);
  }

  static String get installTime {
    return settings.get(SettingsBoxKey.installTime, defaultValue: '');
  }

  static void setInstallTime(String value) {
    settings.put(SettingsBoxKey.installTime, value);
  }

  /// 本机随机用户 ID（**只生成一次，之后永远用同一个** ✓）
  ///
  /// 对应 Android `PrefManager.userRandomId`。
  /// 首次访问时用 `Uuid().v4()` 生成并落盘 —— 与 Android 的行为一致 ✓
  ///
  /// ⚠️ **不要**拿它去换 DUID / SZLM ID：那是设备级实名标识，能反查到具体设备 ✗
  /// 这里是纯随机的匿名 ID ✓ 只用于「统计有多少台机器在用」和「判断是不是新装」
  static String get userRandomId {
    var v = settings.get(SettingsBoxKey.userRandomId, defaultValue: '');
    if (v.isEmpty) {
      v = const Uuid().v4().replaceAll('-', '');
      settings.put(SettingsBoxKey.userRandomId, v);
    }
    return v;
  }

  static void setUserRandomId(String value) {
    settings.put(SettingsBoxKey.userRandomId, value);
  }

  /// 是否记录 HTTP 请求日志（**默认关闭** ✓）
  ///
  /// 打开后 `ApiInterceptor` 会把每个请求的方法/URL/状态码/耗时/响应体（截断）
  /// 追加到 `<应用支持目录>/http_logs/YYYYMMDD.log` ✓ 见 `utils/http_log.dart`
  ///
  /// ⚠️ 关着的时候拦截器只做一次 bool 判断 ✓ **零开销** ✓
  static bool get recordHttpLog {
    return settings.get(SettingsBoxKey.recordHttpLog, defaultValue: false);
  }

  static void setRecordHttpLog(bool value) {
    settings.put(SettingsBoxKey.recordHttpLog, value);
  }

  static String get versionName {
    return settings.get(SettingsBoxKey.versionName, defaultValue: '');
  }

  static void setVersionName(String value) {
    settings.put(SettingsBoxKey.versionName, value);
  }

  static String get apiVersion {
    return settings.get(SettingsBoxKey.apiVersion, defaultValue: '');
  }

  static void setApiVersion(String value) {
    settings.put(SettingsBoxKey.apiVersion, value);
  }

  static String get versionCode {
    return settings.get(SettingsBoxKey.versionCode, defaultValue: '');
  }

  static void setVersionCode(String value) {
    settings.put(SettingsBoxKey.versionCode, value);
  }

  static String get manufacturer {
    return settings.get(SettingsBoxKey.manufacturer, defaultValue: '');
  }

  static void setManufacturer(String value) {
    settings.put(SettingsBoxKey.manufacturer, value);
  }

  static String get brand {
    return settings.get(SettingsBoxKey.brand, defaultValue: '');
  }

  static void setBrand(String value) {
    settings.put(SettingsBoxKey.brand, value);
  }

  static String get model {
    return settings.get(SettingsBoxKey.model, defaultValue: '');
  }

  static void setModel(String value) {
    settings.put(SettingsBoxKey.model, value);
  }

  static String get buildNumber {
    return settings.get(SettingsBoxKey.buildNumber, defaultValue: '');
  }

  static void setBuildNumber(String value) {
    settings.put(SettingsBoxKey.buildNumber, value);
  }

  static String get sdkInt {
    return settings.get(SettingsBoxKey.sdkInt, defaultValue: '');
  }

  static void setSdkInt(String value) {
    settings.put(SettingsBoxKey.sdkInt, value);
  }

  static String get androidVersion {
    return settings.get(SettingsBoxKey.androidVersion, defaultValue: '');
  }

  static void setAndroidVersion(String value) {
    settings.put(SettingsBoxKey.androidVersion, value);
  }

  static String get userAgent {
    return settings.get(SettingsBoxKey.userAgent, defaultValue: '');
  }

  static void setUserAgent(String value) {
    settings.put(SettingsBoxKey.userAgent, value);
  }

  static String get xAppDevice {
    return settings.get(SettingsBoxKey.xAppDevice, defaultValue: '');
  }

  static void setXAppDevice(String value) {
    settings.put(SettingsBoxKey.xAppDevice, value);
  }

  static bool get isLogin {
    return settings.get(SettingsBoxKey.isLogin, defaultValue: false);
  }

  static void setIsLogin(bool value) {
    settings.put(SettingsBoxKey.isLogin, value);
    GlobalData().isLogin = value;
    // ⚠️ 这里**不能**调 `GlobalData().refresh()`（`Get.forceAppUpdate()`）。
    //
    //   `setIsLogin` 在**启动阶段**就会被调到（`getLogin()` 那条路径），
    //   此时强制重建整棵树会把正在初始化的控制器销毁，
    //   所有请求随即走进 `Failure` ⇒ **每个页面都显示「服务器异常」** ✗✗
    //   （2026-10-17 实测踩过，回滚）
    //
    //   登录态变化后的刷新要**在 UI 层做**（登录成功回调里、页面 `onInit` 里），
    //   不要在存储层这种「任何时候都可能被调用」的地方触发全局重建。
  }

  static String get uid {
    return settings.get(SettingsBoxKey.uid, defaultValue: '');
  }

  static void setUid(String value) {
    settings.put(SettingsBoxKey.uid, value);
    GlobalData().uid = value;
  }

  static String get username {
    return settings.get(SettingsBoxKey.username, defaultValue: '');
  }

  static void setUsername(String value) {
    settings.put(SettingsBoxKey.username, value);
    GlobalData().username = value;
  }

  static String get token {
    return settings.get(SettingsBoxKey.token, defaultValue: '');
  }

  static void setToken(String value) {
    settings.put(SettingsBoxKey.token, value);
    GlobalData().token = value;
  }

  /// 第二凭据（启动时用 [token] 换来的）。见 `GlobalData.refreshToken` 的注释。
  static String get refreshToken {
    return settings.get(SettingsBoxKey.refreshToken, defaultValue: '');
  }

  static void setRefreshToken(String value) {
    settings.put(SettingsBoxKey.refreshToken, value);
    GlobalData().refreshToken = value;
  }

  static String get userAvatar {
    return settings.get(SettingsBoxKey.userAvatar, defaultValue: '');
  }

  static void setUserAvatar(String value) {
    settings.put(SettingsBoxKey.userAvatar, value);
  }

  static int get exp {
    return settings.get(SettingsBoxKey.exp, defaultValue: 0);
  }

  static void setExp(int value) {
    settings.put(SettingsBoxKey.exp, value);
  }

  static int get nextExp {
    return settings.get(SettingsBoxKey.nextExp, defaultValue: 1);
  }

  static void setNextExp(int value) {
    settings.put(SettingsBoxKey.nextExp, value);
  }

  static int get level {
    return settings.get(SettingsBoxKey.level, defaultValue: 0);
  }

  static void setLevel(int value) {
    settings.put(SettingsBoxKey.level, value);
  }
}

class BlackListBoxKey {
  static const String userBlackList = 'userBlackList',
      topicBlackList = 'topicBlackList';
}

class SettingsBoxKey {
  static const String useMaterial = 'useMaterial',
      staticColor = 'staticColor',
      selectedTheme = 'selectedTheme',
      // 纯黑主题（对齐 Android `blackDarkTheme`）：深色模式下用纯黑底
      blackDarkTheme = 'blackDarkTheme',
      // 深色模式压暗图片（对齐 Android `isColorFilter`）
      isColorFilter = 'isColorFilter',
      // ── 转场动画（对齐 Android `TransitionAnim` 的三项设置）──
      // 曲线：0=linear 1=m2 2=std 3=emph 4=spring
      animCurve = 'animCurve',
      // 类型：0=slide 1=fade 2=none
      animType = 'animType',
      // 时长：100 / 200 / 300 / 400 / 500（毫秒）
      animDuration = 'animDuration',
      // 首页顶部 tab 的顺序与启用状态（对齐 Android `HomeMenu` 表）
      // 存 `List<String>`，元素是 `TabType` 的枚举名；**不在列表里 = 已禁用**
      homeMenu = 'homeMenu',
      // 设置 - 高级 - 校验 SSL 证书（默认开，对齐 Android `defaultValue=true`）
      sslVerify = 'sslVerify',
      // 设置 - 高级 - 网络传输调试模式（默认关；开启后彻底跳过证书校验，便于抓包）
      sslDebug = 'sslDebug',
      szlmId = 'szlmId',
      // 取号用的 SSAID（对应 Android 的 Settings.Secure.ANDROID_ID），首次生成后持久化
      ssaid = 'ssaid',
      fontScale = 'fontScale',
      followType = 'followType',
      imageQuality = 'imageQuality',
      imageDim = 'imageDim',
      openInBrowser = 'openInBrowser',
      showSquare = 'showSquare',
      recordHistory = 'recordHistory',
      showEmoji = 'showEmoji',
      checkUpdate = 'checkUpdate',
      checkCount = 'checkCount',
      installTime = 'installTime',
      versionName = 'versionNamev',
      apiVersion = 'apiVersion',
      versionCode = 'versionCode',
      manufacturer = 'manufacturer',
      brand = 'brand',
      model = 'model',
      buildNumber = 'buildNumber',
      sdkInt = 'sdkInt',
      androidVersion = 'androidVersion',
      userAgent = 'userAgent',
      xAppDevice = 'xAppDevice',
      isLogin = 'isLogin',
      /// 本机随机用户 ID —— 与 Android `PrefManager.userRandomId` 对应 ✓
      /// 只上报匿名 ID，不拿 DUID（那能反查到具体设备）
      userRandomId = 'userRandomId',
      /// 是否记录 HTTP 请求日志（设置 → 高级，**默认关闭** ✓）
      recordHttpLog = 'recordHttpLog',
  refreshToken = 'refreshToken',
      uid = 'uid',
      username = 'username',
      token = 'token',
      userAvatar = 'userAvatar',
      exp = 'exp',
      nextExp = 'nextExp',
      // 其他屏蔽项（关键字/用户/节点）的配置原文 JSON —— 冷启动没网也能先过滤
      spamConfig = 'spamConfig',
      level = 'level';
}

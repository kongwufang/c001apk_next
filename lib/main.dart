import 'dart:io';

import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';
import 'package:window_manager/window_manager.dart';

import 'components/custom_toast.dart';
import 'components/secondary_tap.dart';
import 'constants/constants.dart';
import 'constants/m3e_button.dart';
import 'logic/network/request.dart';
import 'logic/repo/spam_config_repo.dart';
import 'router/app_pages.dart';
import 'utils/global_data.dart';
import 'utils/ssl_verify.dart';
import 'utils/storage_util.dart';
import 'utils/utils.dart';
import 'utils/webview_env.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  if (Utils.isDesktop) {
    await windowManager.ensureInitialized();
    WindowOptions windowOptions = WindowOptions(
      minimumSize: const Size(400, 700),
      center: true,
      skipTaskbar: false,
      title: 'c001apk',
      titleBarStyle:
          Platform.isMacOS ? TitleBarStyle.hidden : TitleBarStyle.normal,
    );
    windowManager.waitUntilReadyToShow(windowOptions, () async {
      await windowManager.show();
      await windowManager.focus();
    });
  }

  await GStorage.init();
  HttpOverrides.global = CustomHttpOverrides();

  // SSL 证书校验：把内置的 122 张 CA 读进 `SecurityContext`。
  //
  // 必须在**第一次网络请求之前**完成 ——
  // Dio 的 `onHttpClientCreate` 回调是同步的，里面没法 `await rootBundle`。
  // 见 `utils/ssl_verify.dart`。
  await SslVerify.init();
  debugPrint('main: SSL 校验=${SslVerify.enabled ? "开" : "关"} '
      '调试模式=${SslVerify.debugMode ? "开" : "关"} '
      '内置CA=${SslVerify.strictContext != null ? "已加载" : "(不可用，退回系统根)"}');

  // 诊断：确认从 Hive 加载出来的登录态（stderr/stdout 用
  // `_rev/win_ref/launch_with_log.ps1` 抓）
  debugPrint('main: 启动 | isLogin=${GlobalData().isLogin} '
      'uid=${GlobalData().uid} '
      'token=${GlobalData().token.isEmpty ? "(空)" : "有"}');

  // Windows 上必须先建 `WebViewEnvironment` 才能正确读 cookie ——
  // 否则 `CookieManager` 返回空列表，**网页登录永远检测不到登录态**。
  // 详见 `utils/webview_env.dart` 的说明。非 Windows 会直接返回。
  await WebViewEnv.init();

  // 其他屏蔽项（关键字 / 用户 / 节点）先用本地缓存初始化 ——
  // **冷启动没网也要能过滤**，随后由 SpamConfigRepo.refresh() 拿网络结果覆盖。
  // 对应 Android `SpamConfigRepo.init { spamConfig.postValue(cached) }`。
  SpamConfigRepo.initFromCache();

  if (Platform.isAndroid || Platform.isIOS) {
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    SystemChrome.setSystemUIOverlayStyle(SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: Colors.transparent,
      systemNavigationBarDividerColor: Colors.transparent,
      systemNavigationBarContrastEnforced: false,
      systemStatusBarContrastEnforced: false,
      statusBarBrightness: GStorage.getBrightness(),
      systemNavigationBarIconBrightness: GStorage.getBrightness(),
    ));
  }

  Request();
  runApp(const C001APKAPP());
}

class C001APKAPP extends StatelessWidget {
  const C001APKAPP({super.key});

  @override
  Widget build(BuildContext context) {
    bool useMaterial =
        GStorage.settings.get(SettingsBoxKey.useMaterial, defaultValue: true);
    int staticColor =
        GStorage.settings.get(SettingsBoxKey.staticColor, defaultValue: 0);
    int selectedTheme =
        GStorage.settings.get(SettingsBoxKey.selectedTheme, defaultValue: 0);
    double fontScale =
        GStorage.settings.get(SettingsBoxKey.fontScale, defaultValue: 1.0);
    return DynamicColorBuilder(builder: (lightDynamic, darkDynamic) {
      ColorScheme? lightColorScheme;
      ColorScheme? darkColorScheme;
      if (lightDynamic != null && darkDynamic != null && useMaterial) {
        lightColorScheme = lightDynamic.harmonized();
        darkColorScheme = darkDynamic.harmonized();
      } else {
        lightColorScheme = ColorScheme.fromSeed(
          seedColor: Constants.seedColors[staticColor],
          brightness: Brightness.light,
        );
        darkColorScheme = ColorScheme.fromSeed(
          seedColor: Constants.seedColors[staticColor],
          brightness: Brightness.dark,
        );
      }

      // ★「纯黑主题」（对齐 Android `blackDarkTheme`）：
      //   深色模式下把各层容器底色压成纯黑，OLED 屏更省电、对比更强。
      //   Android 那边是个 MaterialSwitchPreference，摘要写的是
      //   「当深色主题启用时使用纯黑主题」。
      if (GStorage.blackDarkTheme) {
        darkColorScheme = darkColorScheme.copyWith(
          surface: Colors.black,
          surfaceContainerLowest: Colors.black,
          surfaceContainerLow: Colors.black,
          surfaceContainer: Colors.black,
          surfaceContainerHigh: Colors.black,
          surfaceContainerHighest: Colors.black,
        );
      }

      return GetMaterialApp(
        title: 'c001apk',
        theme: ThemeData(
          colorScheme: selectedTheme == 2 ? darkColorScheme : lightColorScheme,
          useMaterial3: true,
          navigationBarTheme: NavigationBarThemeData(
              surfaceTintColor: (lightDynamic != null && useMaterial)
                  ? lightColorScheme.surfaceTint
                  : lightColorScheme.surfaceContainer),
          snackBarTheme: SnackBarThemeData(
            actionTextColor: lightColorScheme.primary,
            backgroundColor: lightColorScheme.secondaryContainer,
            closeIconColor: lightColorScheme.secondary,
            contentTextStyle: TextStyle(color: lightColorScheme.secondary),
            elevation: 20,
          ),
          pageTransitionsTheme: const PageTransitionsTheme(
            builders: <TargetPlatform, PageTransitionsBuilder>{
              TargetPlatform.android: ZoomPageTransitionsBuilder(
                allowEnterRouteSnapshotting: false,
              ),
            },
          ),
          popupMenuTheme: PopupMenuThemeData(
            surfaceTintColor: lightColorScheme.surfaceTint,
          ),
          cardTheme: CardThemeData(
            surfaceTintColor: lightColorScheme.surfaceTint,
            shadowColor: Colors.transparent,
          ),
          // ★ M3E 按钮的**按下形位**：按下时圆角收缩一档（16dp → 12dp）。
          //
          // 对齐 Android：`themes_m3e.xml` 把 materialButtonStyle /
          // materialButtonOutlinedStyle / borderlessButtonStyle 等 12 个样式
          // 全部指向 `Widget.Material3Expressive.Button*`，
          // 形状取自 `m3expressive_button_shape_state_list`。
          //
          // ⚠️ Flutter 的按钮形状是 `WidgetStateProperty`，只在状态切换时**瞬间**取值，
          //    没有插值 —— 所以这里是「跳变」，不是 Android 那种弹簧连续变化。
          filledButtonTheme: FilledButtonThemeData(
            shape: M3EButtonShape.shape(),
          ),
          outlinedButtonTheme: OutlinedButtonThemeData(
            shape: M3EButtonShape.shape(),
          ),
          elevatedButtonTheme: ElevatedButtonThemeData(
            shape: M3EButtonShape.shape(),
          ),
          textButtonTheme: TextButtonThemeData(
            shape: M3EButtonShape.shape(),
          ),
          dialogTheme: DialogThemeData(
            surfaceTintColor: lightColorScheme.surfaceTint,
          ),
          inputDecorationTheme: InputDecorationTheme(
            fillColor: lightColorScheme.surfaceContainer,
          ),
          progressIndicatorTheme: ProgressIndicatorThemeData(
            refreshBackgroundColor: lightColorScheme.onSecondary,
          ),
        ),
        darkTheme: ThemeData(
          colorScheme: selectedTheme == 1 ? lightColorScheme : darkColorScheme,
          useMaterial3: true,
          navigationBarTheme: NavigationBarThemeData(
              surfaceTintColor: (lightDynamic != null && useMaterial)
                  ? darkColorScheme.surfaceTint
                  : darkColorScheme.surfaceContainer),
          snackBarTheme: SnackBarThemeData(
            actionTextColor: darkColorScheme.primary,
            backgroundColor: darkColorScheme.secondaryContainer,
            closeIconColor: darkColorScheme.secondary,
            contentTextStyle: TextStyle(color: darkColorScheme.secondary),
            elevation: 20,
          ),
          popupMenuTheme: PopupMenuThemeData(
            surfaceTintColor: darkColorScheme.surfaceTint,
          ),
          cardTheme: CardThemeData(
            surfaceTintColor: darkColorScheme.surfaceTint,
            shadowColor: Colors.transparent,
          ),
          // 深色主题同样给 M3E 按钮形位（按下圆角收缩一档）
          filledButtonTheme: FilledButtonThemeData(
            shape: M3EButtonShape.shape(),
          ),
          outlinedButtonTheme: OutlinedButtonThemeData(
            shape: M3EButtonShape.shape(),
          ),
          elevatedButtonTheme: ElevatedButtonThemeData(
            shape: M3EButtonShape.shape(),
          ),
          textButtonTheme: TextButtonThemeData(
            shape: M3EButtonShape.shape(),
          ),
          dialogTheme: DialogThemeData(
            surfaceTintColor: darkColorScheme.surfaceTint,
          ),
          inputDecorationTheme: InputDecorationTheme(
            fillColor: darkColorScheme.surfaceContainer,
          ),
          progressIndicatorTheme: ProgressIndicatorThemeData(
            refreshBackgroundColor: darkColorScheme.onSecondary,
          ),
        ),
        themeMode: GStorage.getThemeMode(),
        getPages: AppPages.getPages,
        initialRoute: '/',
        builder: (BuildContext context, Widget? child) {
          return FlutterSmartDialog(
            toastBuilder: (String msg) => CustomToast(msg: msg),
            child: MediaQuery(
              data: MediaQuery.of(context)
                  .copyWith(textScaler: TextScaler.linear(fontScale)),
              // ★ 桌面端：右键 = 长按（在有长按的控件上）/ 返回（其余地方）。
              //   详见 `components/secondary_tap.dart` 的说明。
              //   内层有 `onSecondaryTap` 时它优先，所以不会误触返回。
              child: SecondaryTapBack(child: child!),
            ),
          );
        },
      );
    });
  }
}

class CustomHttpOverrides extends HttpOverrides {
  @override
  HttpClient createHttpClient(SecurityContext? context) {
    return super.createHttpClient(context)..userAgent = GStorage.userAgent;
  }
}

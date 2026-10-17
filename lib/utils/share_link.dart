import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

// 纯逻辑（零依赖）—— 拆出去是为了让单元测试不必拉进整套插件依赖图
import 'coolapk_link.dart' as link;
import 'utils.dart';

/// 进入应用时识别剪贴板里的**酷安分享链接**并弹「是否打开」。
///
/// 忠实移植 Android `util/ShareLinkPrompter.kt` + `NetWorkUtil.findCoolapkLink`。
///
/// ## 触发口径
/// Android 是「**进入应用**」而不是「冷启动」：
/// 把已 start 的 Activity 数出来，`0 → 1` 才算一次进入 ——
/// 页面之间互跳、从二级页返回都到不了 0，所以不会重复打扰。
///
/// 桌面端只有主窗口，「进入应用」＝ 主页面首次挂载 + 从最小化/后台回到前台。
/// 这里只在**主页面首次挂载**时查一次（够用，且不会烦人）。
///
/// ## 只问一次的几种情况（照抄 Android）
/// - 同一份剪贴板内容**只问一次**（用户点「取消」也不再来烦），换内容才再问；
/// - 应用**自己复制**进去的内容不问（`markSelfCopied` 登记）。
///
/// ## 与 Android 的差异（如实说明）
/// Android 10+ 后台读剪贴板会被系统拦成空串，所以那边要等窗口拿到焦点 +
/// 重试 8 次（≈2s）。桌面端没有这个限制，但为了等窗口就绪，仍然**延迟 400ms** 再读。
class ShareLinkPrompter {
  const ShareLinkPrompter._();

  /// 已问过的剪贴板内容（含用户取消的那次）
  static String? _askedText;

  /// 本应用自己刚复制进去的文本
  static String? _selfCopiedText;

  /// 应用自己写剪贴板时登记一次（见「复制」页），免得切回来转头问自己
  static void markSelfCopied(String text) {
    _selfCopiedText = text;
  }

  /// 在主页面首次挂载时调一次。
  static Future<void> checkOnce() async {
    // 等窗口就绪（桌面端无系统拦截，纯粹是让首帧先出来，别一上来就盖弹窗）
    await Future.delayed(const Duration(milliseconds: 400));
    String text;
    try {
      final data = await Clipboard.getData(Clipboard.kTextPlain);
      text = (data?.text ?? '').trim();
    } catch (_) {
      return;
    }
    if (text.isEmpty || text == _askedText || text == _selfCopiedText) return;
    final link = findCoolapkLink(text);
    if (link == null) return;
    _askedText = text;
    _show(link);
  }

  static void _show(String link) {
    Get.dialog<void>(
      AlertDialog(
        title: const Text('识别到分享链接'),
        content: const Text('是否打开？'),
        actions: [
          TextButton(
            onPressed: () => Get.back(),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () {
              Get.back();
              Utils.onOpenLink(link, null);
            },
            child: const Text('确认'),
          ),
        ],
      ),
    );
  }

  /// 从一段文本里找出一个酷安分享链接。
  ///
  /// 实现在 `utils/coolapk_link.dart`（**纯逻辑、零依赖**）。
  /// 拆出去是为了让单元测试只 import 那个文件 ——
  /// 否则测试会把 `utils.dart` 背后的一整片插件依赖图拉进来，
  /// 实测会**卡死 CI 的 `flutter test`**。
  static String? findCoolapkLink(String text) => link.findCoolapkLink(text);
}

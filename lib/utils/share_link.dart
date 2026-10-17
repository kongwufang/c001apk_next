import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

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

  // ────────────────── 链接识别（照抄 `NetWorkUtil.findCoolapkLink`）──────────────────

  /// 剪贴板文本长度上限（超过就不看，避免拿一大段文章去跑正则）
  static const int _maxLinkTextLength = 4096;

  /// http(s) 分享链接认这几个域名（与 `Utils.onOpenLink` 能处理的一致）。
  /// `m.coolapk.com` 是手机浏览器的移动站，地址栏复制出来的就是它。
  static const Set<String> _linkHosts = {
    'coolapk.com',
    'www.coolapk.com',
    'm.coolapk.com',
    'coolapk1s.com',
    'www.coolapk1s.com',
    'm.coolapk1s.com',
  };

  /// `coolmarket://` 的 host 名单
  static const Set<String> _linkSchemeHosts = {
    'feed',
    'u',
    'apk',
    'live',
    'com.coolapk.market',
    'www.coolapk.com',
    'www.coolapk1s.com',
  };

  /// 带 scheme 的候选子串
  ///
  /// ★ 末尾那串排除字符照抄 Android：中文标点（`）` `、` `，` `。` `；` `】`）
  /// 经常跟着链接一起被复制过来，不排除会被吞进链接里。
  static final RegExp _shareLinkPattern =
      RegExp(r'''(?:https?|coolmarket)://[^\s"'<>）)、，。；;】]+''');

  static final RegExp _whitespace = RegExp(r'\s+');

  /// 句末标点跟着链接一起被复制过来是常态，校验前先削掉
  static const String _trailingPunctuation = '.,。，、)）]】"\'!！';

  /// 从一段文本里找出**一个**酷安分享链接；找不到返回 null。
  ///
  /// 口径照抄 Android：
  /// - 先扫**带 scheme** 的子串（微信/QQ 的分享文案会把链接和说明黏在一起：
  ///   「分享自酷安https://www.coolapk.com/feed/1?s=abc」）；
  /// - 再退回**按空白切词**找裸域名（有些 App 复制时会把 `https://` 吃掉）。
  static String? findCoolapkLink(String text) {
    final raw = text.trim();
    if (raw.isEmpty || raw.length > _maxLinkTextLength) return null;

    for (final m in _shareLinkPattern.allMatches(raw)) {
      final link = _linkOfSchemeUrl(m.group(0)!);
      if (link != null) return link;
    }
    for (final token in raw.split(_whitespace)) {
      final link = _linkOfBareHost(token);
      if (link != null) return link;
    }
    return null;
  }

  static String _trimTrailing(String s) {
    var end = s.length;
    while (end > 0 && _trailingPunctuation.contains(s[end - 1])) {
      end--;
    }
    return s.substring(0, end);
  }

  /// 带 scheme 的候选：域名得是我们能打开的，而且要落到具体路径上
  /// （**只有域名首页不算分享链接**）。
  ///
  /// ★ scheme 和 host **故意不转小写** —— 与 Android 一致：
  /// 那边 `openLink` 是**大小写敏感**的字符串替换，
  /// `HTTPS://WWW.COOLAPK.COM/feed/1` 它一样打不开，这里宽容地认下来
  /// 只会让用户点「确认」后换回一句「不支持的链接」。
  ///
  /// ⚠️ 所以 host **不能用 `Uri.host`** —— Dart 的 `Uri` 会把它**小写化**，
  /// 于是 `https://WWW.COOLAPK.COM/feed/1` 会被误判成合法（Android 不会）。
  /// 这里从原串里手工抠出 authority 段（保留大小写），只把 **path** 交给 `Uri`。
  static String? _linkOfSchemeUrl(String candidate) {
    final text = _trimTrailing(candidate.trim());
    if (text.isEmpty) return null;
    final uri = Uri.tryParse(text);
    if (uri == null) return null;
    final host = _rawHost(text);
    switch (uri.scheme) {
      case 'http':
      case 'https':
        return (_linkHosts.contains(host) && uri.path.length > 1) ? text : null;
      case 'coolmarket':
        return _linkSchemeHosts.contains(host) ? text : null;
      default:
        return null;
    }
  }

  /// 从原串里抠出 `://` 之后的 authority 段并按原样返回（**不小写**）。
  ///
  /// 会去掉 `user:pass@` 与 `:port`。不处理 IPv6 字面量 ——
  /// 分享链接里不可能出现，真出现了也只是被判为「不认」，不会误放行。
  static String _rawHost(String text) {
    final i = text.indexOf('://');
    if (i < 0) return '';
    final rest = text.substring(i + 3);
    var end = rest.length;
    for (final sep in const ['/', '?', '#']) {
      final j = rest.indexOf(sep);
      if (j >= 0 && j < end) end = j;
    }
    var authority = rest.substring(0, end);
    final at = authority.lastIndexOf('@');
    if (at >= 0) authority = authority.substring(at + 1);
    final colon = authority.indexOf(':');
    if (colon >= 0) authority = authority.substring(0, colon);
    return authority;
  }

  /// 裸域名（没有 scheme）：「www.coolapk.com/feed/1」
  static String? _linkOfBareHost(String candidate) {
    final text = _trimTrailing(candidate);
    if (!text.contains('/')) return null;
    final host = text.substring(0, text.indexOf('/'));
    final path = text.substring(text.indexOf('/') + 1);
    return (path.isNotEmpty && _linkHosts.contains(host)) ? text : null;
  }
}

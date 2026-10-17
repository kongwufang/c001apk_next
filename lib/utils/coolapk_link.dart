/// 从一段文本里识别**酷安分享链接**的**纯逻辑**（无 IO / 无插件 / 无 UI）。
///
/// 从 `utils/share_link.dart` 拆出来的 —— 那边还管「读剪贴板 + 弹窗」，
/// 会连带 import `utils.dart`（进而把一大片插件和页面拉进依赖图）。
///
/// ## ★ 为什么要拆
/// `flutter test` 里 `share_link_test.dart` 原本直接 import `share_link.dart`，
/// 于是整个插件依赖图都被拉进来 —— **CI 的 `Test` step 直接卡死**
/// （实测：run 从 09:12 之后 50 分钟没有任何更新，两个 job 都停在那儿）。
///
/// 纯逻辑单独成文件后，测试只 import 这个文件 ⇒ 依赖图只有一个 `dart:core`，
/// **几毫秒跑完，不可能卡**。
///
/// ## 口径（逐条照抄 Android `NetWorkUtil.findCoolapkLink`）
/// 详见各函数的注释。
library;

/// 剪贴板文本长度上限（超过就不看，避免拿一大段文章去跑正则）
const int kMaxLinkTextLength = 4096;

/// http(s) 分享链接认这几个域名（与 `Utils.onOpenLink` 能处理的一致）。
/// `m.coolapk.com` 是手机浏览器的移动站，地址栏复制出来的就是它。
const Set<String> kLinkHosts = {
  'coolapk.com',
  'www.coolapk.com',
  'm.coolapk.com',
  'coolapk1s.com',
  'www.coolapk1s.com',
  'm.coolapk1s.com',
};

/// `coolmarket://` 的 host 名单
const Set<String> kLinkSchemeHosts = {
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
final RegExp _shareLinkPattern =
    RegExp(r'''(?:https?|coolmarket)://[^\s"'<>）)、，。；;】]+''');

final RegExp _whitespace = RegExp(r'\s+');

/// 句末标点跟着链接一起被复制过来是常态，校验前先削掉
const String kTrailingPunctuation = '.,。，、)）]】"\'!！';

/// 从一段文本里找出**一个**酷安分享链接；找不到返回 null。
///
/// 口径照抄 Android：
/// - 先扫**带 scheme** 的子串（微信/QQ 的分享文案会把链接和说明黏在一起：
///   「分享自酷安https://www.coolapk.com/feed/1?s=abc」）；
/// - 再退回**按空白切词**找裸域名（有些 App 复制时会把 `https://` 吃掉）。
String? findCoolapkLink(String text) {
  final raw = text.trim();
  if (raw.isEmpty || raw.length > kMaxLinkTextLength) return null;

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

String _trimTrailing(String s) {
  var end = s.length;
  while (end > 0 && kTrailingPunctuation.contains(s[end - 1])) {
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
String? _linkOfSchemeUrl(String candidate) {
  final text = _trimTrailing(candidate.trim());
  if (text.isEmpty) return null;
  final uri = Uri.tryParse(text);
  if (uri == null) return null;
  final host = _rawHost(text);
  switch (uri.scheme) {
    case 'http':
    case 'https':
      return (kLinkHosts.contains(host) && uri.path.length > 1) ? text : null;
    case 'coolmarket':
      return kLinkSchemeHosts.contains(host) ? text : null;
    default:
      return null;
  }
}

/// 从原串里抠出 `://` 之后的 authority 段并按原样返回（**不小写**）。
///
/// 会去掉 `user:pass@` 与 `:port`。不处理 IPv6 字面量 ——
/// 分享链接里不可能出现，真出现了也只是被判为「不认」，不会误放行。
String _rawHost(String text) {
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
String? _linkOfBareHost(String candidate) {
  final text = _trimTrailing(candidate);
  if (!text.contains('/')) return null;
  final host = text.substring(0, text.indexOf('/'));
  final path = text.substring(text.indexOf('/') + 1);
  return (path.isNotEmpty && kLinkHosts.contains(host)) ? text : null;
}

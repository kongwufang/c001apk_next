/// Markdown 自动识别与预处理。
///
/// 对应 Android `util/MarkdownUtils.kt`（245 行）。
///
/// ## 为什么要「自动识别」
/// 酷安服务端**不支持** Markdown，作者写进正文的 md 语法是**纯文本**存下来的。
/// 所以客户端要自己判断：这段正文到底是普通文本（走 HTML 渲染），
/// 还是作者写了 md（走 Markdown 渲染）。
///
/// 难点在**误报**：正文里出现一个 `*` 或 `#` 很常见，
/// 一旦误判成 md 就会把正常文本渲染乱。所以 Android 的做法是：
/// - **强特征**（代码围栏 / 标题 / 行内代码 / 删除线 / 链接 / 图片 / 加粗）：
///   命中任一就按 md 渲染
/// - **弱特征**（无序列表 / 有序列表 / 引用 / 分隔线 / 表格行）：
///   要**累计 2 个以上**才算，降低误报
/// - 判断前先**剔除酷安话题 `#[xxx]#`**，否则 `#` 会干扰标题/链接判断
///
/// ## 渲染方式
/// 用 `markdown` 包（Dart 官方维护的纯 Dart 解析器）转成 HTML，
/// **复用本工程已有的 `HtmlWidget` 管道**（`html_text.dart`）——
/// 这样表情替换、图片点击、链接跳转等既有能力全部保留，
/// 不必再引一个富文本渲染库。表格与删除线是该包 `ExtensionSet.gitHubFlavored`
/// 的内置扩展。
///
/// ## ⚠️ 顶层函数，不是类
/// 这里全部是**顶层函数**（对应 Android 的 `object MarkdownUtils` 里的方法），
/// 调用时直接用 `isMarkdown(x)` / `toHtml(x)`，**没有** `MarkdownUtil.` 前缀。
///
// ⚠️ `library;` 必须在**所有 import 之前** —— 放 import 之后会报
//    `library_directive_not_first`（Windows 与 Android 会同时挂）。
//    这个坑我在台账里记过一次，这次又踩了，特此加注。
library;

import 'package:markdown/markdown.dart' as md;

// ---------------- 自动检测 ----------------

/// 酷安话题 `#[xxx]#`，先剔除避免干扰标题/链接判断
final _topicPattern = RegExp(r'#\[[^\]\n]*\]#');

// 强特征：任一命中即按 md 渲染
final _fencePattern = RegExp(r'^`{3,}', multiLine: true);
final _headingPattern = RegExp(r'^#{1,6}\s+\S', multiLine: true);
final _inlineCodePattern = RegExp(r'`[^`\n]+`');
final _strikePattern = RegExp(r'~~[^~\n]+~~');
final _linkPattern = RegExp(r'\[[^\]\n]*\]\(\s*[^\s)]+\s*\)');
final _imagePattern = RegExp(r'!\[[^\]\n]*\]\(\s*[^\s)]+\s*\)');
final _boldPattern = RegExp(r'(\*\*[^*\n]+\*\*|__[^_\n]+__)');

/// 服务端已把 md 链接的 URL 换成 `<a>` 标签：
/// `[文字](<a href="...">查看链接</a>)`
final _serverLinkPattern = RegExp(r'\][ \t]*\(\s*<a\b', caseSensitive: false);

// 弱特征：累计 2 个以上才认为是 md，降低误报
final _bulletPattern = RegExp(r'^[-*+]\s+\S', multiLine: true);
final _orderedPattern = RegExp(r'^\d{1,3}\.\s+\S', multiLine: true);
final _quotePattern = RegExp(r'^>\s?\S', multiLine: true);
final _hrPattern = RegExp(r'^ {0,3}(-{3,}|\*{3,}|_{3,})\s*$', multiLine: true);
final _tableRowPattern = RegExp(r'^\|.+\|\s*$', multiLine: true);

int _count(RegExp p, String text) => p.allMatches(text).length;

/// 这段正文是不是 Markdown？对应 Android `MarkdownUtils.isMarkdown`。
bool isMarkdown(String? text) {
  if (text == null || text.trim().isEmpty || text.length < 4) return false;

  final t = text.replaceAll(_topicPattern, '');

  if (_fencePattern.hasMatch(t) ||
      _headingPattern.hasMatch(t) ||
      _inlineCodePattern.hasMatch(t) ||
      _strikePattern.hasMatch(t) ||
      _linkPattern.hasMatch(t) ||
      _imagePattern.hasMatch(t) ||
      _serverLinkPattern.hasMatch(t) ||
      _boldPattern.hasMatch(t)) {
    return true;
  }

  var score = 0;
  if (_count(_bulletPattern, t) >= 2) score++;
  if (_orderedPattern.hasMatch(t)) score++;
  if (_quotePattern.hasMatch(t)) score++;
  if (_hrPattern.hasMatch(t)) score++;
  if (_count(_tableRowPattern, t) >= 2) score++;
  return score >= 2;
}

// ---------------- URL 清洗 ----------------

/// 服务端 URL 自动识别会把正文标点吞进 URL（如 `(url)` 被识别成 `url)`），
/// 去掉未配对的右括号及尾部句读标点。对应 Android `MarkdownUtils.cleanUrl`。
String cleanUrl(String url) {
  var s = url.trim();

  // `String` 本身实现了 `Pattern`，所以可以直接 allMatches
  final open = '('.allMatches(s).length;
  final close = ')'.allMatches(s).length;
  if (close > open) {
    var toDrop = close - open;
    // 从右往左丢掉多余的 ')'，再整体反转回来
    final kept = <String>[];
    for (var i = s.length - 1; i >= 0; i--) {
      final c = s[i];
      if (c == ')' && toDrop > 0) {
        toDrop--;
        continue;
      }
      kept.add(c);
    }
    s = kept.reversed.join();
  }

  while (s.isNotEmpty && '.,;:!?'.contains(s[s.length - 1])) {
    s = s.substring(0, s.length - 1);
  }
  return s;
}

// ---------------- HTML 预处理 ----------------

final _brPattern = RegExp(r'<br\s*/?>', caseSensitive: false);

/// `<a href="url">label</a>`。用三引号原始字符串，
/// 这样 `"` 和 `'` 都能安全地写进字符类里。
final _aTagPattern = RegExp(
  r'''<a\b[^>]*?href\s*=\s*["']([^"']*)["'][^>]*>(.*?)</a>''',
  caseSensitive: false,
  dotAll: true,
);

/// 服务端把 md 链接的 URL 替换成 `<a>查看链接</a>` 后，与外层 md 语法形成嵌套
/// `[外层文字]([查看链接](url))`，降级还原为 `[外层文字](url)`
final _nestedLinkPattern =
    RegExp(r'\[([^\]\n]*)\]\(\[[^\]\n]*\]\(\s*([^)\s]+)\s*\)\)?');

/// 把服务端正文预处理成标准 Markdown。对应 Android `MarkdownUtils.preprocess`：
/// 1. `<br>` → 换行
/// 2. `<a href="url">label</a>` → `[label](url)`（url 先 [cleanUrl]，label 转义）
/// 3. 常见行内 HTML 标签 → md 等价语法（`<b>`→`**`、`<i>`→`*`、`<s>`→`~~`）
/// 4. HTML 实体解码（服务端把 `>` `<` `&` 转义了，
///    不还原会导致引用块 `&gt;`、代码块内容显示错乱）
String preprocess(String text) {
  var t = text.replaceAll(_brPattern, '\n');

  t = t.replaceAllMapped(_aTagPattern, (m) {
    final rawUrl = (m.group(1) ?? '').trim();
    final url = cleanUrl(rawUrl);
    final rawLabel = m.group(2) ?? '';
    // 服务端用 URL 本身当链接文字时，同样去掉被吞进去的尾随标点
    final label = (rawLabel.trim() == rawUrl ? url : rawLabel)
        .replaceAll('\\', '\\\\')
        .replaceAll('[', '\\[')
        .replaceAll(']', '\\]');
    if (url.isEmpty) return label;
    final safeUrl = url
        .replaceAll(' ', '%20')
        .replaceAll('(', '%28')
        .replaceAll(')', '%29');
    return '[$label]($safeUrl)';
  });

  // 常见行内 HTML 标签 → markdown 等价语法
  t = t.replaceAll(RegExp(r'</?(b|strong)>', caseSensitive: false), '**');
  t = t.replaceAll(RegExp(r'</?(i|em)>', caseSensitive: false), '*');
  t = t.replaceAll(RegExp(r'</?(s|strike|del)>', caseSensitive: false), '~~');

  // 服务端 <a> 与外层 md 语法嵌套 → 还原成作者写的 [文字](url)
  t = t.replaceAllMapped(
      _nestedLinkPattern, (m) => '[${m.group(1)}](${cleanUrl(m.group(2) ?? '')})');

  return decodeEntities(t);
}

// ---------------- HTML 实体 ----------------

final _entityPattern =
    RegExp(r'&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,9});');

const _namedEntities = <String, String>{
  'quot': '"',
  'amp': '&',
  'lt': '<',
  'gt': '>',
  'apos': "'",
  'nbsp': '\u00A0',
  'ldquo': '\u201C',
  'rdquo': '\u201D',
  'lsquo': '\u2018',
  'rsquo': '\u2019',
  'hellip': '\u2026',
  'mdash': '\u2014',
  'ndash': '\u2013',
  'middot': '\u00B7',
  'times': '\u00D7',
  'laquo': '\u00AB',
  'raquo': '\u00BB',
  'euro': '\u20AC',
  'pound': '\u00A3',
  'yen': '\u00A5',
  'sect': '\u00A7',
  'para': '\u00B6',
  'deg': '\u00B0',
  'copy': '\u00A9',
  'reg': '\u00AE',
  'trade': '\u2122',
  'bull': '\u2022',
  'dagger': '\u2020',
};

/// 对应 Android `MarkdownUtils.decodeEntities`
String decodeEntities(String text) {
  if (!text.contains('&')) return text;
  return text.replaceAllMapped(_entityPattern, (m) {
    final name = m.group(1) ?? '';
    if (name.startsWith('#')) {
      return _decodeNumericEntity(name) ?? m.group(0)!;
    }
    return _namedEntities[name.toLowerCase()] ?? m.group(0)!;
  });
}

String? _decodeNumericEntity(String name) {
  final isHex = name.length > 1 && (name[1] == 'x' || name[1] == 'X');
  final codePoint = isHex
      ? int.tryParse(name.substring(2), radix: 16)
      : int.tryParse(name.substring(1));
  if (codePoint == null || codePoint < 1 || codePoint > 0x10FFFF) return null;
  return String.fromCharCode(codePoint);
}

// ---------------- 渲染 ----------------

/// 预处理 + 转 HTML。
///
/// 注意：**不要**给 `encodeHtml` 传 false —— 纯文本内容本来就该被转义，
/// 交给 `HtmlWidget` 再解回来；表情替换放在这一步**之后**做
/// （那时 `[表情]` 还是字面量，而 md 链接已经变成 `<a>` 标签，
/// 不会被表情正则误伤）。
String toHtml(String text) {
  return md.markdownToHtml(
    preprocess(text),
    extensionSet: md.ExtensionSet.gitHubFlavored,
  );
}

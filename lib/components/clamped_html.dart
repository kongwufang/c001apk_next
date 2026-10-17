import 'package:flutter/material.dart';
import 'package:html/parser.dart' as html_parser;

/// 列表卡里的**正文折叠**：最多 N 行 + 「查看更多」。
///
/// 对应 Android `item_home_feed.xml` 的 `message`（`maxLines="5"`）
/// + `messageMore`（「查看更多」）。
///
/// ## ★ 为什么不能直接用 maxLines
/// 正文是 `htmlText(...)` 渲染的，底层是 `flutter_widget_from_html_core` 的
/// `HtmlWidget` —— 它产出的是**widget 树**，不是单个 `RichText`，
/// **没有 `maxLines` 参数**。
///
/// 所以这里：
/// 1. 用 `TextPainter` 按同样字号/行高量一遍**纯文本**，判断会不会超过 N 行；
/// 2. 超了就用 `ConstrainedBox + ClipRect` 把高度裁到 N 行；
/// 3. 下面补一个「查看更多」（点了进详情页看全文）。
///
/// 量纯文本是**可靠**的：正文里的 `<a>` / `<b>` 都是行内元素，不改变行数；
/// 表情是 1em 高的行内图，也不改变行数。
///
/// ## 与 Android 的差异（如实说明）
/// Android 用 `TextView` 的 `ellipsize="end"`，第 5 行末尾会有省略号；
/// `HtmlWidget` 的 widget 树没法加省略号，所以这里是**直接裁切**。
/// 视觉上差一个「…」，其余（行数、位置、「查看更多」）都一致。
class ClampedHtml extends StatelessWidget {
  const ClampedHtml({
    super.key,
    required this.child,
    required this.plainText,
    required this.onMore,
    this.maxLines = 5,
    this.fontSize = 16,
    this.height = 1.2,
  });

  /// 完整的富文本正文
  final Widget child;

  /// 用来量行数的纯文本（HTML 已剥掉）
  final String plainText;

  /// 点「查看更多」
  final VoidCallback onMore;

  /// 折叠后的行数（Android `maxLines="5"`）
  final int maxLines;

  /// 正文字号（Android 16sp）
  final double fontSize;

  /// 行高倍数（Android `lineSpacingMultiplier="1.2"`）
  final double height;

  /// 把 HTML 剥成纯文本。
  ///
  /// 正文结构很规整（服务端给的 `message` 基本是纯文本 + `<a class="feed-link-url">`
  /// 包起来的话题标签），用 `html` 包解析后取 `text` 即可。
  /// 解析失败就退回原文 —— 量行数偏一点也比整块不显示好。
  static String stripHtml(String html) {
    if (!html.contains('<')) return html;
    try {
      final text = html_parser.parse(html).documentElement?.text;
      if (text != null && text.isNotEmpty) return text;
    } catch (_) {}
    return html;
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        // 宽度可能无界（放在 Row/横向滚动里时）—— 那就不折叠，避免崩
        if (!constraints.hasBoundedWidth) return child;

        final painter = TextPainter(
          text: TextSpan(
            text: plainText,
            style: TextStyle(fontSize: fontSize, height: height),
          ),
          maxLines: maxLines,
          textDirection: Directionality.of(context),
        )..layout(maxWidth: constraints.maxWidth);
        final truncated = painter.didExceedMaxLines;
        if (!truncated) return child;

        final colorScheme = Theme.of(context).colorScheme;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            ConstrainedBox(
              // 正好 5 行的高度
              constraints: BoxConstraints(maxHeight: maxLines * fontSize * height),
              child: ClipRect(child: child),
            ),
            const SizedBox(height: 6), // Android `messageMore` 的 layout_marginTop="6dp"
            InkWell(
              onTap: onMore,
              borderRadius: BorderRadius.circular(4),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Text(
                  '查看更多',
                  style: TextStyle(
                    // Android：13sp、加粗、colorPrimary
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                    color: colorScheme.primary,
                  ),
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

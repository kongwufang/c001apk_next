import 'package:flutter/material.dart';

/// 个人主页的**签名（bio）**，折叠 2 行 + 「展开 / 收起」。
///
/// 对应 Android `ui/user/UserPagerFragment.kt`：
/// ```kotlin
/// private const val BIO_COLLAPSED_LINES = 2
///
/// private var bioExpanded = false
///
/// // 点「展开」/「收起」
/// bioExpand.setOnClickListener {
///     bioExpanded = !bioExpanded
///     bio.maxLines = if (bioExpanded) Int.MAX_VALUE else BIO_COLLAPSED_LINES
///     bioExpand.text = if (bioExpanded) "收起" else "展开"
/// }
///
/// /** 折叠态下签名确实被截断了才露出「展开」 */
/// private fun refreshBioExpand() {
///     if (bioExpanded) return
///     val layout = bio.layout ?: return
///     val lastLine = layout.lineCount - 1
///     val truncated = layout.lineCount > BIO_COLLAPSED_LINES ||
///         (lastLine >= 0 && layout.getEllipsisCount(lastLine) > 0)
///     bioExpand.visibility = if (truncated) View.VISIBLE else View.GONE
/// }
/// ```
///
/// ## 关键口径：**只有真被截断才显示「展开」**
/// 签名只有一行时不该冒出一个「展开」按钮 —— 那纯属碍眼。
/// Android 是等 `doOnNextLayout` 拿到 `TextLayout` 再判
/// （`lineCount > 2` 或最后一行的省略号数 > 0）。
///
/// Flutter 里用 `TextPainter` **在布局前**就能量出来 —— 更省事，
/// 也不用等一帧（`ClampedHtml` 用的是同一招）。
class ExpandableBio extends StatefulWidget {
  const ExpandableBio({
    super.key,
    required this.text,
    this.collapsedLines = 2,
    this.fontSize,
    this.color,
    this.padding = const EdgeInsets.only(left: 20, top: 4),
  });

  final String text;

  /// 折叠态保留几行（Android `BIO_COLLAPSED_LINES = 2`）
  final int collapsedLines;

  final double? fontSize;
  final Color? color;
  final EdgeInsets padding;

  @override
  State<ExpandableBio> createState() => _ExpandableBioState();
}

class _ExpandableBioState extends State<ExpandableBio> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final style = DefaultTextStyle.of(context).style.merge(
          TextStyle(fontSize: widget.fontSize, color: widget.color),
        );

    return Padding(
      padding: widget.padding,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          LayoutBuilder(
            builder: (context, constraints) {
              // 用 TextPainter 量一下：不限制行数时占几行 / 会不会溢出
              final painter = TextPainter(
                text: TextSpan(text: widget.text, style: style),
                maxLines: null,
                textDirection: Directionality.of(context),
              )..layout(maxWidth: constraints.maxWidth);

              final needsExpand = painter.didExceedMaxLines ||
                  painter.computeLineMetrics().length > widget.collapsedLines;
              painter.dispose();

              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    widget.text,
                    style: style,
                    maxLines: _expanded ? null : widget.collapsedLines,
                    overflow: _expanded
                        ? TextOverflow.visible
                        : TextOverflow.ellipsis,
                  ),
                  // 只有**真被截断**才露「展开」；展开后必定露「收起」
                  if (needsExpand || _expanded)
                    GestureDetector(
                      onTap: () => setState(() => _expanded = !_expanded),
                      child: Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(
                          _expanded ? '收起' : '展开',
                          style: TextStyle(
                            fontSize: 12,
                            color: Theme.of(context).colorScheme.primary,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

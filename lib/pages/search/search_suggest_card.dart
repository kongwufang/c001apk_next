import 'package:flutter/material.dart';

import '../../logic/model/search_suggest.dart';

/// 搜索联想里的一行 —— 对齐 Android `ui/search/SearchSuggestAdapter.kt`。
///
/// ```
/// [👤/🔍]  搜索用户：晚上刷机            ← 「搜索用户：」项没有人形以外的按钮
/// [🔍]     一加 13 上手                 [↖]  ← 普通联想词有「回填」箭头
/// ```
///
/// ## 和 Android 一致的三处细节
/// 1. **首项特殊**：`title` 以「搜索用户：」开头 ⇒ 人形图标 ✓ 且**没有**回填箭头 ✓
/// 2. **关键词高亮**：把 `text` 与 `keyword` 的**最长公共子串**（≥2 字符）染成主题色 ✓
/// 3. **两种点击**：点整行 = 直接搜 ✓ 点右侧箭头 = 只回填输入框 ✓
class SearchSuggestCard extends StatelessWidget {
  const SearchSuggestCard({
    super.key,
    required this.data,
    required this.keyword,
    required this.onTap,
    required this.onFill,
  });

  final SearchSuggestData data;

  /// 当前输入框里的词 —— 用来做高亮 ✓
  final String keyword;

  /// 点整行：直接搜索（`isUser` 表示这是「搜索用户」那一项）
  final void Function(String word, bool isUser) onTap;

  /// 点右侧箭头：只回填
  final void Function(String word) onFill;

  /// 与 Android `SearchSuggestAdapter.USER_PREFIX` 一致 ✓
  static const String userPrefix = '搜索用户：';

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final text = data.title ?? '';
    final isUser = text.startsWith(userPrefix);
    final word = isUser ? text.substring(userPrefix.length) : text;

    return ListTile(
      dense: true,
      leading: Icon(
        isUser ? Icons.account_circle_outlined : Icons.search,
        size: 20,
        color: cs.onSurfaceVariant,
      ),
      title: RichText(
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        text: TextSpan(
          style: TextStyle(fontSize: 15, color: cs.onSurface),
          children: _highlight(text, word.isEmpty ? keyword : word, cs.primary),
        ),
      ),
      trailing: isUser
          ? null
          : IconButton(
              tooltip: '填入输入框',
              visualDensity: VisualDensity.compact,
              icon: Icon(Icons.north_west, size: 18, color: cs.outline),
              onPressed: () => onFill(word),
            ),
      onTap: () => onTap(word, isUser),
    );
  }

  /// 把 [text] 里与 [keyword] 的最长公共子串染成 [color]。
  ///
  /// 公共片段不足 2 个字符就不高亮 ✓（对齐 Android 的同名逻辑 ✓）
  List<TextSpan> _highlight(String text, String keyword, Color color) {
    if (text.isEmpty || keyword.isEmpty) {
      return [TextSpan(text: text)];
    }
    final (start, length) = _longestCommonSubstring(text, keyword);
    if (length < 2) return [TextSpan(text: text)];
    return [
      if (start > 0) TextSpan(text: text.substring(0, start)),
      TextSpan(
        text: text.substring(start, start + length),
        style: TextStyle(color: color, fontWeight: FontWeight.w600),
      ),
      if (start + length < text.length)
        TextSpan(text: text.substring(start + length)),
    ];
  }

  /// 最长公共子串 → (起始下标, 长度)；不区分大小写 ✓
  (int, int) _longestCommonSubstring(String text, String keyword) {
    final t = text.toLowerCase();
    final k = keyword.toLowerCase();
    var bestStart = 0;
    var bestLength = 0;
    final dp = List<int>.filled(t.length + 1, 0);
    for (var i = 1; i <= k.length; i++) {
      var diagonal = 0;
      for (var j = 1; j <= t.length; j++) {
        final temp = dp[j];
        dp[j] = (k[i - 1] == t[j - 1]) ? diagonal + 1 : 0;
        if (dp[j] > bestLength) {
          bestLength = dp[j];
          bestStart = j - dp[j];
        }
        diagonal = temp;
      }
    }
    return (bestStart, bestLength);
  }
}

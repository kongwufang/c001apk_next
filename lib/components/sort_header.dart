import 'package:flutter/material.dart';

import '../pages/topic/topic_page.dart' show TopicSortType;

/// 产品 / 话题「讨论」tab 顶部的**三段式排序**（默认 / 最新 / 热度）。
///
/// 对应 Android `res/layout/item_product_sort_header.xml`
/// + `adapter/ProductSortHeaderAdapter.kt`（`MaterialButtonToggleGroup`，单选必选）。
///
/// ## 布局（照抄 XML）
/// 横向、`padding 15 / 8 / 15 / 8dp`、垂直居中。
///
/// | 元素 | 规格 |
/// |---|---|
/// | `sortTitle` | **14sp 加粗**、`weight = 1` |
/// | 按钮组 | 单选、必选 |
/// | 每个按钮 | **12sp**、内边距 **12 / 4dp**、描边样式（`OutlinedButton`）；选中态换成 tonal 底色 |
///
/// ## ★ 标题为什么是空的
/// Android 的 `ProductSortHeaderAdapter` **从不给 `sortTitle` 设文本** ——
/// XML 里的 `tools:text="全部讨论"` 只是设计期预览。
/// 所以运行时那是一个**空的 weight=1 占位**，作用是把按钮推到**右边**。
/// 这里用一个 `Spacer()` 等价实现。
class SortHeader extends StatelessWidget {
  const SortHeader({
    super.key,
    required this.current,
    required this.onChanged,
  });

  final TopicSortType current;
  final ValueChanged<TopicSortType> onChanged;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(15, 8, 15, 8),
      child: Row(
        children: [
          // 等价于 XML 里那个空的 `sortTitle`（weight=1）：把按钮推到右边
          const Spacer(),
          for (final type in TopicSortType.values)
            Padding(
              padding: const EdgeInsets.only(left: 4),
              child: _button(context, type),
            ),
        ],
      ),
    );
  }

  Widget _button(BuildContext context, TopicSortType type) {
    final colorScheme = Theme.of(context).colorScheme;
    final selected = type == current;
    return Material(
      color: selected ? colorScheme.secondaryContainer : Colors.transparent,
      borderRadius: BorderRadius.circular(8),
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: () => onChanged(type),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(8),
            border: Border.all(
              color: selected
                  ? colorScheme.secondaryContainer
                  : colorScheme.outlineVariant,
            ),
          ),
          child: Text(
            // 文案与 `TopicSortType.label` 一致：默认 / 最新 / 热门
            type.label,
            style: TextStyle(
              fontSize: 12,
              color: selected
                  ? colorScheme.onSecondaryContainer
                  : colorScheme.onSurfaceVariant,
              fontWeight: selected ? FontWeight.bold : FontWeight.normal,
            ),
          ),
        ),
      ),
    );
  }
}

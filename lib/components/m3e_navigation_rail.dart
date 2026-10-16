import 'package:flutter/material.dart';

import '../constants/m3_tokens.dart';

/// 一个导航栏目的地。
class M3ENavDestination {
  const M3ENavDestination({
    required this.icon,
    required this.selectedIcon,
    required this.label,
  });

  final Widget icon;
  final Widget selectedIcon;
  final String label;
}

/// M3E（Material 3 Expressive）导航栏。
///
/// ## 为什么不直接用 Flutter 的 `NavigationRail`
/// Flutter 的 `NavigationRail` 用的是 **M3 基础规格**（折叠宽约 80dp，
/// item 高度也不受控），与 Android 端实测尺寸对不上，且没有暴露 item 尺寸参数。
///
/// ## 尺寸全部来自 material 1.14.0 AAR（不是估的）
/// 解出的 `m3expressive_nav_rail_*` / `m3_comp_nav_rail_*`：
/// ```
/// m3expressive_nav_rail_min_width                    96dp
/// m3expressive_nav_rail_item_min_height              64dp
/// m3expressive_nav_rail_item_spacing                  4dp
/// m3expressive_nav_rail_item_leading_trailing_padding 20dp
/// m3expressive_nav_rail_content_margin_top           44dp
/// m3_comp_nav_rail_item_icon_size                    24dp
/// m3_comp_nav_rail_item_vertical_active_indicator_width   56dp
/// m3_comp_nav_rail_item_vertical_active_indicator_height  32dp
/// m3_comp_nav_rail_item_vertical_icon_label_space     4dp
/// ShapeAppearance.M3.Comp.NavRail.Item.ActiveIndicator.Shape.cornerSize  48dp
/// ```
/// 复核工具：`_rev/win_ref/dump_navrail_dimens.py`
///
/// ★ 这套值与**真机 UI 树实测**（MEmu / 853×640dp 横屏）完全吻合：
/// 容器 96dp、item 64dp、左右各 20dp、指示器 56×32dp。
///
/// ## labelVisibilityMode
/// Android 端是 `labelVisibilityMode="selected"` —— **只有选中项显示文字**，
/// 由 [showLabelOnlyWhenSelected] 控制（默认 true，对齐 Android）。
class M3ENavigationRail extends StatelessWidget {
  const M3ENavigationRail({
    super.key,
    required this.destinations,
    required this.selectedIndex,
    required this.onDestinationSelected,
    this.showLabelOnlyWhenSelected = true,
  });

  final List<M3ENavDestination> destinations;
  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;
  final bool showLabelOnlyWhenSelected;

  /// 容器宽 96dp（`m3expressive_nav_rail_min_width`）
  static const double containerWidth = 96;

  /// item 高 64dp（`m3expressive_nav_rail_item_min_height`）
  static const double itemHeight = 64;

  /// item 间距 4dp（`m3expressive_nav_rail_item_spacing`）
  static const double itemSpacing = 4;

  /// 左右内边距 20dp（`m3expressive_nav_rail_item_leading_trailing_padding`）
  static const double sidePadding = 20;

  /// 顶部留白 44dp（`m3expressive_nav_rail_content_margin_top`）
  static const double topMargin = 44;

  /// 选中指示器 56×32dp
  static const double indicatorWidth = 56;
  static const double indicatorHeight = 32;

  /// 图标 24dp（`m3_comp_nav_rail_item_icon_size`）
  static const double iconSize = 24;

  /// 图标与文字的间距 4dp
  static const double iconLabelSpace = 4;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return SizedBox(
      width: containerWidth,
      child: Material(
        color: colorScheme.surface,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            const SizedBox(height: topMargin),
            for (var i = 0; i < destinations.length; i++) ...[
              if (i > 0) const SizedBox(height: itemSpacing),
              _item(context, i),
            ],
          ],
        ),
      ),
    );
  }

  Widget _item(BuildContext context, int index) {
    final colorScheme = Theme.of(context).colorScheme;
    final dest = destinations[index];
    final selected = index == selectedIndex;
    final showLabel = !showLabelOnlyWhenSelected || selected;

    return SizedBox(
      width: containerWidth,
      height: itemHeight,
      child: InkWell(
        onTap: () => onDestinationSelected(index),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // 选中指示器 56×32dp，圆角 48dp（= 完整胶囊）
            Container(
              width: indicatorWidth,
              height: indicatorHeight,
              decoration: BoxDecoration(
                color: selected ? colorScheme.secondaryContainer : null,
                // cornerSize 48dp 对 32dp 高的容器来说就是胶囊
                borderRadius: BorderRadius.circular(M3Shape.extraExtraLarge),
              ),
              alignment: Alignment.center,
              child: IconTheme(
                data: IconThemeData(
                  size: iconSize,
                  color: selected
                      ? colorScheme.onSecondaryContainer
                      : colorScheme.onSurfaceVariant,
                ),
                child: selected ? dest.selectedIcon : dest.icon,
              ),
            ),
            // labelVisibilityMode="selected"：仅选中项显示文字
            if (showLabel) ...[
              const SizedBox(height: iconLabelSpace),
              Text(
                dest.label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
                  color: selected
                      ? colorScheme.onSurface
                      : colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

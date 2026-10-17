import 'package:flutter/material.dart';

import '../../logic/model/feed/feed_extra.dart';
import '../../utils/utils.dart';
import '../network_image.dart';

/// 动态卡里的**附加信息槽**：点评评分 / 二手商品。
///
/// 对应 Android `view/FeedExtraView.kt`（308 行）。
///
/// ## 三块共用一个槽位
/// Android 注释：
/// > 三块共用一个槽位（放在媒体区之下、热评之上），谁有数据谁显示；都没有就整块隐藏
/// > （官方是三个独立槽位，但实测这三类在数据上基本互斥 —— 一条动态要么是点评、
/// > 要么是二手、要么挂商品清单，拆三个槽位只会让卡片布局多三份空占位）。
///
/// 本工程里**商品清单**（`extra_title` / `extra_url` / `extra_pic`）由
/// `FeedCard._extraUrl()` 单独渲染（`Datum` 已有那三个字段），
/// 这里只负责**点评评分**和**二手**。
///
/// ## 布局（照抄 `FeedExtraView` 的 build* / bind*）
/// ### 点评块
/// | 元素 | 规格 |
/// |---|---|
/// | 块 | 圆角 **12dp**、底色 `surface`、内边距 **10/8/10/8dp** |
/// | 抬头 | **13sp 加粗**，`onSurface`；文案 `"★ 5  10 分"`（两空格连），都空则「点评」 |
/// | 维度行 | 横向，上间距 **6dp** |
/// | chip | **11sp**、`onSurfaceVariant`、圆角 **6dp**、底色 `surface`、内边距 **6/2dp**、右边距 **6dp** |
///
/// ### 二手块
/// | 元素 | 规格 |
/// |---|---|
/// | 块 | 横向、圆角 **12dp**、底色 `surface`、内边距 **10dp**、垂直居中 |
/// | 缩略图 | **56×56dp**，圆角 **8dp**，`cover` |
/// | 标题 | **14sp**、`onSurface`、最多 2 行；有配置则 `"标题 · 配置"` |
/// | 价格 | **15sp 加粗**、`colorPrimary`、上间距 **2dp**；空则「面议」 |
/// | 次要 | **12sp**、`onSurfaceVariant`、上间距 **2dp**；`"地区 · 成色"` |
/// | 来源 | **12sp**、`onSurfaceVariant`、右侧垂直居中（如「闲鱼」） |
///
/// ### 块间距
/// 第一块上间距 **10dp**，后续每块 **6dp**
/// （Android 注释：间距走「块自己的上边距」，根布局不留边距 ——
/// 整块隐藏时高度必须正好为 0，否则下面热评的位置会跟着动）。
///
/// ### 配色为什么用 `surface` 而不是 `surfaceVariant`
/// Android 注释：
/// > 卡片里的热评 / 转发块用的就是 `surface` 这一档，跟它们保持一致；
/// > `variant` 会把块整片压灰，和首页割裂。
class ExtraSlot extends StatelessWidget {
  const ExtraSlot({super.key, required this.extra, this.isFeedContent = false});

  final FeedExtra extra;

  /// 详情页（卡片本身已用 `surfaceContainer`，此时块底色要跟着换）
  final bool isFeedContent;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    // 详情页的卡片底色是 `surfaceContainer`，块用 `surface`；
    // 列表卡底色是 `surfaceContainer`，块同样用 `surface` ——
    // 两处一致，和现成的 `_extraUrl` 保持同一套。
    final blockColor = colorScheme.surface;
    final blocks = <Widget>[
      if (extra.rating != null) _ratingBlock(context, extra.rating!, blockColor),
      if (extra.ershou != null) _ershouBlock(context, extra.ershou!, blockColor),
    ];
    if (blocks.isEmpty) return const SizedBox.shrink();

    return Container(
      padding: EdgeInsets.only(
        left: isFeedContent ? 16 : 10,
        right: isFeedContent ? 16 : 10,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var i = 0; i < blocks.length; i++)
            Padding(
              // 第一块 10dp，后续 6dp（`addBlock`）
              padding: EdgeInsets.only(top: i == 0 ? 10 : 6),
              child: blocks[i],
            ),
        ],
      ),
    );
  }

  /// 点评评分块
  Widget _ratingBlock(
      BuildContext context, FeedRating rating, Color blockColor) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: blockColor,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            rating.header,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.bold,
              color: colorScheme.onSurface,
            ),
          ),
          if (rating.items.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Wrap(
                spacing: 6,
                runSpacing: 6,
                children: [
                  for (final dim in rating.items)
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: blockColor,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        dim.label,
                        style: TextStyle(
                          fontSize: 11,
                          color: colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  /// 二手商品块
  Widget _ershouBlock(
      BuildContext context, FeedErshou ershou, Color blockColor) {
    final colorScheme = Theme.of(context).colorScheme;
    return Material(
      color: blockColor,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: ershou.linkUrl.isEmpty
            ? null
            : () => Utils.onOpenLink(ershou.linkUrl, ershou.title),
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Row(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: SizedBox(
                  width: 56,
                  height: 56,
                  child: ershou.logo.isEmpty
                      ? ColoredBox(color: colorScheme.surfaceContainer)
                      : networkImage(
                          ershou.logo,
                          width: 56,
                          height: 56,
                          fit: BoxFit.cover,
                          borderRadius: null,
                        ),
                ),
              ),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 10),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        ershou.titleText,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 14,
                          color: colorScheme.onSurface,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        ershou.priceText,
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.bold,
                          color: colorScheme.primary,
                        ),
                      ),
                      if (ershou.metaText.isNotEmpty) ...[
                        const SizedBox(height: 2),
                        Text(
                          ershou.metaText,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 12,
                            color: colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              if (ershou.linkSource.isNotEmpty)
                Text(
                  ershou.linkSource,
                  style: TextStyle(
                    fontSize: 12,
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

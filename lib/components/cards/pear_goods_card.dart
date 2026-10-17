import 'package:flutter/material.dart';

import '../../logic/model/feed/datum.dart';
import '../../utils/utils.dart';
import '../network_image.dart';

/// 活动页「线下」tab 的**实体商品卡**（`entityType == 'pear_goods'`）。
///
/// 对应 Android `res/layout/item_pear_goods.xml`
/// + `AppAdapter.PearGoodsViewHolder`（viewType **19**）。
///
/// ## ★ 这类条目**没有 `entityTemplate`**
/// Android 注释：
/// > 活动页「线下」tab 的 pear_goods 实体（**无 entityTemplate**，仅有 `goods_*` 字段）
///
/// 所以分派判据是 **`entityType == 'pear_goods'`**（不是 template）——
/// 与 `feedCover` / `productConfigList` 那两次正好相反，别再搞混。
///
/// ## 字段（全在 `raw`，`Datum` 没声明）
/// `goods_title` / `goods_promo_title` / `goods_promo_price` / `goods_pic` /
/// `goods_tags` / `goods_url` / `goods_buy_url` / `goods_buy_text`
///
/// ## 布局（照抄 XML）
/// | 元素 | 规格 |
/// |---|---|
/// | 卡片 | margin **5dp**、圆角 **12dp**、elevation 0；内边距 **12dp**、横向 |
/// | 整卡点击 | 开 `goods_url ?? goods_buy_url`，标题取 `goods_title ?? title` |
/// | 商品图 | **96×72dp**、`cover` |
/// | 标题 | **15sp 加粗**、最多 **2 行**；空则用 `title` |
/// | 促销副标题 | **12sp** 次要色、上间距 **4dp**、单行（如「2026年9月28日」） |
/// | 标签行 | **12sp** 次要色、上间距 **4dp**、单行（如「北京 · 8人 · 含机酒」） |
/// | 按钮 | **12sp**、描边、内边距 **12/4dp**、左边距 **8dp**、垂直居中；**空则「查看详情」** |
class PearGoodsCard extends StatelessWidget {
  const PearGoodsCard({super.key, required this.data});

  final Datum data;

  String _str(List<String> keys) {
    for (final k in keys) {
      final v = data.raw?[k];
      if (v is String && v.trim().isNotEmpty) return v.trim();
    }
    return '';
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    final title = _str(['goods_title']).isNotEmpty
        ? _str(['goods_title'])
        : (data.title ?? '');
    final promoTitle = _str(['goods_promo_title']);
    final pic = _str(['goods_pic']);
    final tags = _str(['goods_tags']);
    final goodsUrl = _str(['goods_url']);
    final buyUrl = _str(['goods_buy_url']);
    // Android：`goodsBuyText.isNullOrEmpty()` 时兜底成「查看详情」
    final buyText =
        _str(['goods_buy_text']).isNotEmpty ? _str(['goods_buy_text']) : '查看详情';

    final link = goodsUrl.isNotEmpty ? goodsUrl : buyUrl;

    return Container(
      margin: const EdgeInsets.all(5),
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: link.isEmpty ? null : () => Utils.onOpenLink(link, title),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 商品图 96×72
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: SizedBox(
                  width: 96,
                  height: 72,
                  child: pic.isEmpty
                      ? ColoredBox(color: colorScheme.surface)
                      : networkImage(
                          pic,
                          width: 96,
                          height: 72,
                          fit: BoxFit.cover,
                          borderRadius: null,
                        ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    if (promoTitle.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        promoTitle,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 12,
                          color: colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                    if (tags.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        tags,
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
              const SizedBox(width: 8),
              // 右侧按钮：垂直居中
              Center(
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                  decoration: BoxDecoration(
                    border: Border.all(color: colorScheme.outlineVariant),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    buyText,
                    style: TextStyle(
                      fontSize: 12,
                      color: colorScheme.onSurface,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

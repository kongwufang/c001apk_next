import 'package:flutter/material.dart';

import '../../logic/model/feed/datum.dart';
// `orEmpty` / `isNullOrEmpty` 是 `utils/extensions.dart` 里的**扩展**，
// 不是类成员 —— 缺这个 import 时 Dart 报的是
// 「The getter 'orEmpty' isn't defined for the type 'String?'」，
// 看着像字段不存在，其实是扩展没进来。
import '../../utils/extensions.dart';
import '../../utils/utils.dart';

/// 产品页「参数」tab 的**版本配置**卡片（`entityTemplate == 'productConfigList'`）。
///
/// 对应 Android `res/layout/item_product_config_list.xml`
/// + `AppAdapter.ProductConfigListViewHolder`。
///
/// ## ★ 这张卡的数据是「注入」的
/// 服务端下发的这张卡片 **`entities` 恒为空**，真正的内容来自
/// `product/detail` 的顶层 `configRows`。Android 的做法是
/// （`TopicContentViewModel`）：
/// > 参数 tab：把本地 configRows 合并进版本配置卡片
///
/// 本工程由 `TopicContentController.customGetData()` 把 `configRows`
/// 写进这张卡的 `raw`，这里再从 `raw` 读出来。
///
/// ## 布局（照抄 XML）
/// | 元素 | 规格 |
/// |---|---|
/// | 卡片 | margin **5dp**、圆角 **12dp**、elevation 0；内边距 **12dp** |
/// | 标题 | **15sp 加粗**，占满剩余宽度 |
/// | 副标题 | **12sp** 次要色，左右内边距 **6dp**，点击开「配置对比」H5 |
/// | 行容器 | 上间距 **4dp** |
/// | 每行 | 横向居中、上下内边距 **6dp** |
/// | 行标题 | **14sp**，占满剩余宽度，单行省略 |
/// | 行价格 | **14sp** `colorPrimary`，文案 `"¥$price"` |
/// | 「参数」按钮 | **12sp**、描边、内边距 **12/3dp**、左边距 **12dp**，点击开该配置的 H5 |
class ProductConfigListCard extends StatelessWidget {
  const ProductConfigListCard({super.key, required this.data});

  final Datum data;

  /// 版本配置列表（由 `TopicContentController` 注入到 `raw`）
  List<Map<String, dynamic>> get _rows {
    final v = data.raw?['configRows'];
    if (v is! List) return const [];
    return v.whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList();
  }

  /// 按别名链从 `raw` 取字符串（取不到给空串）
  String _strFromRaw(List<String> keys) {
    for (final k in keys) {
      final v = data.raw?[k];
      if (v is String && v.trim().isNotEmpty) return v.trim();
    }
    return '';
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final rows = _rows;
    // ★ `Datum` **没有** `subTitle` 字段（标题只有 `title` / `messageTitle` /
    //   `extraTitle`），副标题是 `sub_title`，只能从 `raw` 取 ——
    //   与 `generic_card` 里 `readField(e.raw, rule.subtitleChain)` 同一套做法。
    final subTitle = _strFromRaw(const ['sub_title', 'subTitle', 'subtitle']);
    // 「配置对比」H5 的地址同理由 raw 取（`Datum.url` 是这张卡自己的链接）
    final compareUrl = _strFromRaw(const ['compare_url', 'compareUrl']);

    return Container(
      margin: const EdgeInsets.all(5),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 抬头：标题 + 「配置对比」
          Row(
            children: [
              Expanded(
                child: Text(
                  data.title.orEmpty,
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              if (subTitle.isNotEmpty)
                InkWell(
                  onTap: () {
                    // iPhone 上 Android 用的是 `data.url`（这张卡自己的「配置对比」H5）；
                    // 若服务端另给了 `compare_url` 就优先用它。
                    final link = compareUrl.isNotEmpty
                        ? compareUrl
                        : (data.url ?? '');
                    if (link.isNotEmpty) {
                      Utils.onOpenLink(link, data.title ?? '');
                    }
                  },
                  borderRadius: BorderRadius.circular(4),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 6),
                    child: Text(
                      subTitle,
                      style: TextStyle(
                        fontSize: 12,
                        color: colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                ),
            ],
          ),
          if (rows.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Column(
                children: [
                  for (final row in rows) _row(context, row),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _row(BuildContext context, Map<String, dynamic> row) {
    final colorScheme = Theme.of(context).colorScheme;
    final title = row['title']?.toString() ?? '';
    final price = row['price'];
    final url = row['url']?.toString() ?? '';

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Expanded(
            child: Text(
              title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 14),
            ),
          ),
          if (price != null && price.toString().isNotEmpty)
            Text(
              '¥$price',
              style: TextStyle(
                fontSize: 14,
                color: colorScheme.primary,
              ),
            ),
          if (url.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(left: 12),
              child: InkWell(
                onTap: () => Utils.onOpenLink(url, title),
                borderRadius: BorderRadius.circular(4),
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 12, vertical: 3),
                  decoration: BoxDecoration(
                    border: Border.all(color: colorScheme.outlineVariant),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    '参数',
                    style: TextStyle(
                      fontSize: 12,
                      color: colorScheme.onSurface,
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

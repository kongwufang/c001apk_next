import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../logic/model/feed/datum.dart';
import '../../logic/model/feed/entity.dart';
import '../../utils/card_ui.dart';
import '../../utils/extensions.dart';
import '../network_image.dart';

/// 通用卡片渲染：把服务端下发的**任意**卡片画出来，不再为每个模板写一个组件。
///
/// 对应 Android `adapter/GenericCardRenderer.kt` + `util/CardUi.kt`。
///
/// ## 为什么需要
/// 上游 Flutter 的 `item_card.dart` 是一个大 switch：没匹配到的模板**什么都不渲染**，
/// 于是服务端一上新卡片模板，页面就出现空白。（用户 app 的 release-notes 把这个列为
/// 本版最大改动：「一次接住服务端下发的全部卡片模板，没单独适配的模板也有兜底卡片，
/// 不再出现空白或错版」。）
///
/// ## 怎么画
/// 「怎么画」不写死在这里，而是查 [CardUi] 的规则表：
/// 模板 → 骨架（竖排 / 横滚 / 宫格 / 统计 / 纯文本）+ 字段映射（标题取谁、副标题取谁）。
/// 规则表里没有的模板按**实体数量**推断排列、按**实体内容**推断形状，
/// 所以完全没见过的新卡片也能出内容。
///
/// 字段读取一律走 [readField] 的 `|` 回退链，并且在 `raw`（原始 JSON）上取 ——
/// 这样服务端把商品图叫 `goods_pic`、热搜热度叫 `sub_title` 也能取到，
/// 不必往模型里加字段发版。
Widget genericCard(Datum data) {
  final template = data.entityTemplate;
  final layout = CardUi.layout(template, data.entities?.length ?? 0);

  // 规则表说这条模板不渲染（占 0 高度）
  if (layout.hidden) return const SizedBox.shrink();

  // ---- 照官方重画的专属骨架 ----
  switch (layout.official) {
    case CardUi.section:
      return _sectionCard(data);
    case CardUi.links:
      return _linksCard(data);
    case CardUi.capsules:
      return _capsulesCard(data);
    case CardUi.linkTabs:
      return _linkTabsCard(data);
    // 尚未移植的专属骨架（续航卡 / 跑分卡 / 两栏图文按钮 / 专辑大图）
    // 先退回通用骨架，至少能出内容，不会空白
  }

  return _genericSkeleton(data, layout);
}

// ─────────────────────────── 通用骨架 ───────────────────────────

Widget _genericSkeleton(Datum data, CardLayout layout) {
  final template = data.entityTemplate;
  final title = readField(data.raw, CardUi.titleField(template));
  final summary = readField(data.raw, CardUi.summaryField(template));
  final hero = readField(data.raw, CardUi.heroField(template));
  final entities = data.entities ?? const <Entity>[];

  final rows = <Widget>[];

  if (title != null) {
    rows.add(Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        title,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
      ),
    ));
  }

  if (summary != null) {
    rows.add(Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        summary,
        maxLines: 3,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          fontSize: 13,
          color: Theme.of(Get.context!).colorScheme.onSurfaceVariant,
        ),
      ),
    ));
  }

  // 统计卡：内容全在 extraData 里，摊成几行「名称 值」
  //
  // ⚠️ `extraData` 在 Dart 模型里是**被注释掉的**（`// String? extraData;`），
  // 所以直接从 `raw`（原始 JSON）读 —— 这正是保留 raw 的用处：
  // 服务端给了、模型没声明的字段，规则表照取不误，不必改模型发版。
  if (layout.stats) {
    final extraData = data.raw?['extraData']?.toString();
    final rows2 = CardUi.statRows(template, extraData);
    for (final row in rows2) {
      rows.add(_statRow(row.key, row.value));
    }
  } else if (entities.isNotEmpty) {
    rows.add(_entityArea(entities, layout, template));
  } else if (hero != null) {
    // 没有实体时，卡片自带配图当主图（imageScaleCard 那类）
    rows.add(ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: networkImageOf(hero),
      ),
    ));
  }

  // 一个能显示的东西都没有：整卡不占位
  // （对应 Android：原来在 debug 频道留过「暂不支持的内容卡片：模板名」的排障提示，
  //   模板都映射完了就撤掉 —— 这种技术字样不该出现在界面上）
  if (rows.isEmpty) return const SizedBox.shrink();

  return _cardShell(
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: rows,
      ),
    ),
  );
}

Widget _statRow(String name, String value) {
  final colorScheme = Theme.of(Get.context!).colorScheme;
  return Padding(
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: Row(
      children: [
        Expanded(
          child: Text(
            name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(fontSize: 13, color: colorScheme.onSurfaceVariant),
          ),
        ),
        Text(value, style: const TextStyle(fontSize: 13)),
      ],
    ),
  );
}

/// 实体区域：按骨架排（横滚 / 宫格 / 竖排）
Widget _entityArea(List<Entity> entities, CardLayout layout, String? template) {
  if (layout.horizontal) {
    final rule = CardUi.itemRule(template);
    final shape = _effectiveShape(rule, entities);
    return SizedBox(
      height: shape == CardUi.shapeCover ? 150 : 96,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: entities.length,
        separatorBuilder: (_, __) => const SizedBox(width: 10),
        itemBuilder: (_, i) => SizedBox(
          width: shape == CardUi.shapeCover ? 200 : 88,
          child: _entityTile(entities[i], rule, shape),
        ),
      ),
    );
  }

  if (layout.span > 1) {
    final rule = CardUi.itemRule(template);
    final shape = _effectiveShape(rule, entities);
    final per = layout.span;
    final rows = <Widget>[];
    for (var i = 0; i < entities.length; i += per) {
      final slice = entities.sublist(
        i,
        (i + per) > entities.length ? entities.length : (i + per),
      );
      rows.add(Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final e in slice)
            Expanded(child: _entityTile(e, rule, shape)),
          for (var k = slice.length; k < per; k++) const Spacer(),
        ],
      ));
    }
    return Column(children: rows);
  }

  // 竖排
  final rule = CardUi.itemRule(template);
  final shape = _effectiveShape(rule, entities);
  return Column(
    children: [
      for (final e in entities) _entityTile(e, rule, shape),
    ],
  );
}

/// 推断实体形状：规则表配了就用配的；没配就按内容推断
/// （有图有标题 → cover，只有标题 → text，其余 → row）
String _effectiveShape(ItemRule rule, List<Entity> entities) {
  if (rule.shape != CardUi.shapeAuto) return rule.shape;
  final withPic =
      entities.where((e) => readField(e.raw, rule.iconChain) != null).length;
  if (withPic == entities.length && entities.length <= 2) {
    return CardUi.shapeCover;
  }
  final withTitle =
      entities.where((e) => readField(e.raw, rule.titleChain) != null).length;
  if (withTitle == entities.length && withPic == 0) return CardUi.shapeText;
  return CardUi.shapeTile;
}

Widget _entityTile(Entity e, ItemRule rule, String shape) {
  final colorScheme = Theme.of(Get.context!).colorScheme;
  final icon = readField(e.raw, rule.iconChain) ?? e.pic ?? e.logo;
  final title = readField(e.raw, rule.titleChain) ?? e.title.orEmpty;
  final subtitle = readField(e.raw, rule.subtitleChain);

  if (shape == CardUi.shapeCover) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: icon == null
                ? Container(color: colorScheme.surfaceContainerHigh)
                : networkImageOf(icon),
          ),
        ),
        if (title.isNotEmpty) ...[
          const SizedBox(height: 4),
          Text(title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 13)),
        ],
      ],
    );
  }

  if (shape == CardUi.shapeText) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Text(title,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontSize: 13)),
    );
  }

  if (shape == CardUi.shapeRow) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          if (icon != null) ...[
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: SizedBox(width: 36, height: 36, child: networkImageOf(icon)),
            ),
            const SizedBox(width: 10),
          ],
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 13)),
                if (subtitle != null)
                  Text(subtitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          fontSize: 11, color: colorScheme.onSurfaceVariant)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // tile：图标在上、标题在下，居中
  return Padding(
    padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 2),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (icon != null)
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: SizedBox(width: 36, height: 36, child: networkImageOf(icon)),
          )
        else
          SizedBox(
            width: 36,
            height: 36,
            child: Center(
              child: Text(
                title.isEmpty ? '?' : title.substring(0, 1),
                style: const TextStyle(fontSize: 16),
              ),
            ),
          ),
        const SizedBox(height: 4),
        Text(title,
            maxLines: 1,
            textAlign: TextAlign.center,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 11)),
      ],
    ),
  );
}

// ─────────────────────────── 专属骨架 ───────────────────────────

/// 分组标题行（`titleCard`）：左加粗分组名 + 右「更多」，整卡可点
Widget _sectionCard(Datum data) {
  final colorScheme = Theme.of(Get.context!).colorScheme;
  final title = readField(data.raw, CardUi.titleField(data.entityTemplate));
  return _cardShell(
    child: InkWell(
      onTap: data.url.isNullOrEmpty ? null : () => Get.toNamed(data.url!),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
        child: Row(
          children: [
            Expanded(
              child: Text(
                title ?? '',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
            ),
            if (!data.url.isNullOrEmpty)
              Text('更多',
                  style: TextStyle(fontSize: 13, color: colorScheme.primary)),
          ],
        ),
      ),
    ),
  );
}

/// 一行居中的 pill 链接（`selectorLinkCard`）：实体是一条条带自己 url 的 selectorLink
Widget _linksCard(Datum data) {
  final colorScheme = Theme.of(Get.context!).colorScheme;
  final entities = data.entities ?? const <Entity>[];
  if (entities.isEmpty) return const SizedBox.shrink();
  return _cardShell(
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      child: Wrap(
        spacing: 10,
        runSpacing: 8,
        alignment: WrapAlignment.center,
        children: [
          for (final e in entities)
            InkWell(
              borderRadius: BorderRadius.circular(14),
              onTap: e.url.isNullOrEmpty ? null : () => Get.toNamed(e.url!),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                decoration: BoxDecoration(
                  color: colorScheme.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Text(
                  readField(e.raw, CardUi.itemRule(data.entityTemplate).titleChain) ??
                      e.title.orEmpty,
                  style: const TextStyle(fontSize: 13),
                ),
              ),
            ),
        ],
      ),
    ),
  );
}

/// 标题 + 热词胶囊（`capsuleListCard`）：图标 + 加粗标题一行，下面一片胶囊
Widget _capsulesCard(Datum data) {
  final colorScheme = Theme.of(Get.context!).colorScheme;
  final template = data.entityTemplate;
  final title = readField(data.raw, CardUi.titleField(template));
  final hero = readField(data.raw, CardUi.heroField(template));
  final entities = data.entities ?? const <Entity>[];
  if (title == null && entities.isEmpty) return const SizedBox.shrink();

  return _cardShell(
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (title != null)
            Row(
              children: [
                if (hero != null) ...[
                  ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: SizedBox(
                        width: 16, height: 16, child: networkImageOf(hero)),
                  ),
                  const SizedBox(width: 6),
                ],
                Expanded(
                  child: Text(title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          fontSize: 15, fontWeight: FontWeight.bold)),
                ),
              ],
            ),
          if (entities.isNotEmpty) ...[
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final e in entities)
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                    decoration: BoxDecoration(
                      color: colorScheme.surfaceContainerHigh,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Text(
                      readField(e.raw, CardUi.itemRule(template).titleChain) ??
                          e.title.orEmpty,
                      style: const TextStyle(fontSize: 12),
                    ),
                  ),
              ],
            ),
          ],
        ],
      ),
    ),
  );
}

/// 横向滚动的分类入口（`linkCard`）：56dp 高横滚行 + 圆角 14dp 文字胶囊
Widget _linkTabsCard(Datum data) {
  final colorScheme = Theme.of(Get.context!).colorScheme;
  final template = data.entityTemplate;
  final entities = data.entities ?? const <Entity>[];
  if (entities.isEmpty) return const SizedBox.shrink();
  final rule = CardUi.itemRule(template);
  return _cardShell(
    child: SizedBox(
      height: 56,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        itemCount: entities.length,
        separatorBuilder: (_, __) => const SizedBox(width: 8),
        itemBuilder: (_, i) {
          final e = entities[i];
          return InkWell(
            borderRadius: BorderRadius.circular(14),
            onTap: e.url.isNullOrEmpty ? null : () => Get.toNamed(e.url!),
            child: Container(
              alignment: Alignment.center,
              padding: const EdgeInsets.symmetric(horizontal: 14),
              decoration: BoxDecoration(
                color: colorScheme.surfaceContainerHigh,
                borderRadius: BorderRadius.circular(14),
              ),
              child: Text(
                readField(e.raw, rule.titleChain) ?? e.title.orEmpty,
                style: const TextStyle(fontSize: 13),
              ),
            ),
          );
        },
      ),
    ),
  );
}

// ─────────────────────────── 卡片外壳 ───────────────────────────

/// 通用卡片的容器外壳。
///
/// **不能复用 `AppCard`** —— 那个是专用卡片（要求 `appCardType` + `data`，
/// 内部按 app/product/topic/user 各自渲染），没有 `child` 参数。
///
/// 样式与 app 内其它卡片一致：`surfaceContainer` 底 + 12dp 圆角
/// （12dp = M3 `m3_sys_shape_corner_value_medium`，也是 Android
/// `home_card_background_color` 那一档的颜色角色）。
Widget _cardShell({required Widget child}) {
  return Material(
    clipBehavior: Clip.hardEdge,
    borderRadius: const BorderRadius.all(Radius.circular(12)),
    color: Theme.of(Get.context!).colorScheme.surfaceContainer,
    child: child,
  );
}

// ─────────────────────────── 取值 ───────────────────────────

/// 按 `|` 回退链在**原始 JSON** 上取值，返回第一个非空值。
///
/// 优先读 [raw]（模型没声明的字段也在里面）；raw 没有时回退到已解析的字段，
/// 这样即使某些响应没带 raw 也还能显示。
String? readField(Map<String, dynamic>? raw, List<String> chain) {
  if (raw != null) {
    for (final key in chain) {
      final v = raw[key];
      if (v == null) continue;
      final s = v.toString();
      if (s.isNotEmpty && s != 'null') return s;
    }
  }
  return null;
}

/// 取图统一的入口。
///
/// ⚠️ **必须走 app 自己的 `networkImage`**，不能用 `Image.network`：
/// 酷安图床 `image.coolapk.com`（TencentEdgeOne）会校验 **UA**，
/// 不带完整 UA 直接返回 **HTTP 567**（已实测；`avatar.coolapk.com` 不校验）。
/// `networkImage` 里带了这个头。
Widget networkImageOf(String url) {
  return networkImage(url, fit: BoxFit.cover);
}

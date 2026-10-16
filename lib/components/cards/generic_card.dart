import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../logic/model/feed/datum.dart';
import '../../logic/model/feed/entity.dart';
import '../../utils/card_ui.dart';
import '../../utils/extensions.dart';
import '../../utils/utils.dart';
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
    case CardUi.subtabBattery:
      // 续航卡（subTabFeedCard1）：80dp 卡 + 电池刻度条 + 平均亮屏
      return _batteryCard(data);
    case CardUi.subtabScore:
      // 跑分卡（subTabFeedCard2/3）：2x2 品牌渐变格
      return _scoreGridCard(data);
    case CardUi.iconButtons:
      // 两栏并排的图文按钮（iconButtonGridCard）
      return _iconButtonsCard(data);
    // 尚未移植的专属骨架（两栏图文按钮 / 专辑大图）先退回通用骨架，
    // 至少能出内容，不会空白
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
  // ★ 实体级「照官方重画」：`entityTemplate == albumExpandCard` 时走专属布局。
  //
  // 注意这张表配的是**实体**模板，不是卡片模板 —— 实测 `albumExpandCard`
  // 是这样出现的：外层卡片是 `listCard`，里面的 album 实体自带
  // `entityTemplate=albumExpandCard`。
  if (CardUi.entityLayout(e.entityTemplate) == CardUi.albumExpand) {
    return _albumExpandEntity(e);
  }

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

// ─────────── 两栏图文按钮（iconButtonGridCard）───────────
//
// 照 Android `item_icon_button_grid_card.xml` + `GenericCardRenderer.bindIconButtonsCard`：
// 卡片左右各留 12dp、上下 10dp，里面两栏**等宽**并排、间距 8dp；
// 每栏一张**宽高比 2.44** 的图（实测实体下发的就是 `@468x192` 这种宽图，
// 468/192 = 2.4375），文字压在图**上居中**（白色 14sp 加粗）。
// 官方那栏里还有一枚 18dp 图标，实测这个实体不带图标字段，所以只有文字。
// 每一栏点**自己的** url。

Widget _iconButtonsCard(Datum data) {
  final entities = data.entities ?? const <Entity>[];
  if (entities.isEmpty) return const SizedBox.shrink();
  final rule = CardUi.itemRule(data.entityTemplate);

  final rows = <Widget>[];
  for (var i = 0; i < entities.length; i += 2) {
    final slice = entities.sublist(
      i,
      (i + 2) > entities.length ? entities.length : (i + 2),
    );
    rows.add(Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var k = 0; k < 2; k++) ...[
          if (k > 0) const SizedBox(width: 8),
          // 不足两栏时补空位，保证已有一栏仍是「一半宽」
          Expanded(
            child: k < slice.length
                ? _iconButtonCell(slice[k], rule)
                : const SizedBox(),
          ),
        ],
      ],
    ));
  }

  return _cardShell(
    child: Padding(
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
      child: Column(children: rows),
    ),
  );
}

Widget _iconButtonCell(Entity e, ItemRule rule) {
  final colorScheme = Theme.of(Get.context!).colorScheme;
  final pic = readField(e.raw, rule.iconChain) ?? e.pic;
  final title = readField(e.raw, rule.titleChain) ?? e.title.orEmpty;
  final url = e.url;

  return InkWell(
    onTap: url.isNullOrEmpty ? null : () => Utils.onOpenLink(url, title),
    borderRadius: BorderRadius.circular(8),
    child: ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: AspectRatio(
        // 官方实测实体下发的是 @468x192
        aspectRatio: 2.44,
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (pic == null || pic.isEmpty)
              Container(color: colorScheme.surfaceContainerHigh)
            else
              networkImageOf(pic),
            Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Text(
                  title,
                  textAlign: TextAlign.center,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  // 官方就是纯白 14sp 加粗，没有描边/阴影
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
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

// ─────────── 大图打底的酷安集条目（实体模板 albumExpandCard）───────────
//
// 照 Android `item_home_generic_card_album_expand.xml` +
// `GenericCardRenderer.bindAlbumExpandEntity`：
// 整张封面（**148dp 高**，centerCrop）+ **16dp 内边距**的白字内容
// （18sp 加粗标题两行、12sp 说明、底下一行 18dp 头像 + 用户名 + 一串 24dp 应用图标）。
//
// ★ 三点值得注意：
// 1. 封面**额外压了一层渐变蒙层**（上 `#99000000` → 下透明）。
//    Android 的 drawable 注释写明：**官方布局没有这一层**，但标题/说明是白色直接压在封面上，
//    封面偏亮时读不出来，所以补一层。这是 app 自己加的改进，本工程照做。
// 2. 应用图标取自 **`raw['apkRows'][].pic`** —— 模型里没有这个数组（又一次靠 `raw`）。
// 3. 图标白底 + 1dp 内边距、**互相叠 7dp**（第一个跟左边留 8dp）；
//    第 5 个槽位是白底圆里的 `+N`（`raw['apkRowsMoreCount']`）。

Widget _albumExpandEntity(Entity e) {
  final raw = e.raw;
  final bg = readField(raw, const ['logo', 'icon', 'pic', 'bg']);
  final title = readField(raw, CardUi.chain(null, CardUi.defaultItemTitle)) ??
      e.title.orEmpty;
  final apknum = readField(raw, const ['apknum']);
  final info = readField(raw, const ['description', 'intro']) ??
      (apknum == null ? null : '$apknum 个应用');
  final user = readField(raw, const ['username']);
  final avatar = readField(raw, const ['userAvatar']);

  // 应用图标取原始 JSON 的 apkRows[].pic（模型里没有这个数组）
  final icons = <String>[];
  final rows = raw?['apkRows'];
  if (rows is List) {
    for (final r in rows) {
      if (r is Map) {
        final p = r['pic']?.toString();
        if (p != null && p.isNotEmpty) icons.add(p);
      }
    }
  }
  final more = int.tryParse(raw?['apkRowsMoreCount']?.toString() ?? '') ?? 0;
  final showIcons = !avatar.isNullOrEmpty || !user.isNullOrEmpty || icons.isNotEmpty;

  final url = e.url;

  return InkWell(
    onTap: url.isNullOrEmpty ? null : () => Utils.onOpenLink(url, title),
    child: SizedBox(
      height: 148,
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (bg == null || bg.isEmpty)
            Container(color: Theme.of(Get.context!).colorScheme.surfaceContainerHigh)
          else
            networkImageOf(bg),
          // ★ app 自加的渐变蒙层（官方没有），保证白字可读
          const DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [Color(0x99000000), Color(0x00000000)],
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (title.isNotEmpty)
                  Text(
                    title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                if (!info.isNullOrEmpty) ...[
                  const SizedBox(height: 4),
                  Text(
                    info!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Colors.white, fontSize: 12),
                  ),
                ],
                const Spacer(),
                if (showIcons)
                  SizedBox(
                    height: 24,
                    child: Row(
                      children: [
                        if (!avatar.isNullOrEmpty)
                          ClipOval(
                            child: SizedBox(
                              width: 18,
                              height: 18,
                              child: networkImageOf(avatar!),
                            ),
                          ),
                        if (!user.isNullOrEmpty) ...[
                          const SizedBox(width: 2),
                          Flexible(
                            child: Text(
                              user!,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                  color: Colors.white, fontSize: 12),
                            ),
                          ),
                        ],
                        const Spacer(),
                        // 一串应用图标：4 个 + 可能一个 +N
                        for (var i = 0; i < icons.length && i < 4; i++)
                          Container(
                            width: 24,
                            height: 24,
                            // 第一个跟左边留 8dp，之后互相叠 7dp
                            margin: EdgeInsets.only(left: i == 0 ? 8 : -7),
                            padding: const EdgeInsets.all(1),
                            color: Colors.white,
                            child: networkImageOf(icons[i]),
                          ),
                        if (icons.isNotEmpty && more > 0)
                          Container(
                            width: 24,
                            height: 24,
                            margin: const EdgeInsets.only(left: -7),
                            alignment: Alignment.center,
                            decoration: const BoxDecoration(
                              color: Colors.white,
                              shape: BoxShape.circle,
                            ),
                            child: Text(
                              '+$more',
                              style: const TextStyle(
                                  fontSize: 10, color: Color(0xFF333333)),
                            ),
                          ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

// ────────────────── 续航卡（subTabFeedCard1）──────────────────
//
// 规格真源：`_rev/SUBTAB_CARDS_SPEC.md`（逆向自官方 16.6.4 dex 的
// `SubTabComponent.kt` 的 `BatteryLifeSummaryCard`，line 211）。
// card1 走 `isSummary=true` 分支。

/// 读出 `extraData`（服务端给的是**JSON 字符串**）。
///
/// 注意 `extraData` 在 Dart 模型里是注释掉的，所以从 [Datum.raw] 取。
Map<String, dynamic>? _extraDataOf(Datum data) {
  final s = data.raw?['extraData']?.toString();
  if (s == null || s.isEmpty) return null;
  try {
    final v = jsonDecode(s);
    return v is Map ? Map<String, dynamic>.from(v) : null;
  } catch (_) {
    return null;
  }
}

/// 续航卡：80dp 卡 + 电池刻度条 + 「平均亮屏 N 小时」
Widget _batteryCard(Datum data) {
  final colorScheme = Theme.of(Get.context!).colorScheme;
  final extra = _extraDataOf(data);

  final avg = double.tryParse(extra?['avgData']?.toString() ?? '');
  final countRaw = double.tryParse(extra?['countData']?.toString() ?? '');
  // countData → 取整后拼「N人提供数据」
  final count = countRaw?.toInt();
  final hasData = avg != null && avg > 0;

  // 刻度条进度：量程 2h ~ 17h（规格：((avg - 2) / 15).coerceIn(0, 1)）
  final progress = hasData ? ((avg - 2) / 15).clamp(0.0, 1.0) : 0.0;

  return _cardShell(
    child: SizedBox(
      height: 80,
      child: Stack(
        children: [
          // 顶部：图标 + 「续航时长」+ 右侧「N人提供数据」
          Positioned(
            left: 12,
            top: 12,
            right: 15,
            child: Row(
              children: [
                Icon(Icons.battery_full,
                    size: 16, color: colorScheme.primary),
                const SizedBox(width: 4),
                const Text('续航时长', style: TextStyle(fontSize: 12)),
                const Spacer(),
                if (count != null && count > 0)
                  Text(
                    '$count人提供数据',
                    maxLines: 1,
                    style: TextStyle(
                        fontSize: 12, color: colorScheme.onSurfaceVariant),
                  ),
              ],
            ),
          ),

          if (hasData) ...[
            // 平均亮屏 N 小时
            Positioned(
              left: 12,
              top: 40,
              height: 28,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.baseline,
                textBaseline: TextBaseline.alphabetic,
                children: [
                  Text('平均亮屏',
                      style: TextStyle(
                          fontSize: 12, color: colorScheme.onSurfaceVariant)),
                  const SizedBox(width: 4),
                  Text(
                    _avgText(avg),
                    style: const TextStyle(
                        fontSize: 28, fontWeight: FontWeight.w500),
                  ),
                  const SizedBox(width: 2),
                  const Text('小时', style: TextStyle(fontSize: 10)),
                ],
              ),
            ),
            // 电池刻度条：174×30、圆角 6dp，贴在右下
            Positioned(
              right: 11,
              top: 38,
              child: SizedBox(
                width: 174,
                height: 30,
                child: _BatterySlider(progress: progress),
              ),
            ),
          ] else
            Positioned(
              left: 0,
              right: 0,
              top: 12,
              height: 51,
              child: Center(
                child: Text(
                  '暂无人分享续航时长',
                  style: TextStyle(
                      fontSize: 12, color: colorScheme.onSurfaceVariant),
                ),
              ),
            ),
        ],
      ),
    ),
  );
}

/// avgData → 整数显示整数、否则一位小数（规格：`%.1f`）
String _avgText(double v) =>
    v == v.roundToDouble() ? v.toInt().toString() : v.toStringAsFixed(1);

/// 电池刻度条。规格见 `_rev/SUBTAB_CARDS_SPEC.md` 的「续航刻度条」一节。
class _BatterySlider extends StatelessWidget {
  const _BatterySlider({required this.progress});

  final double progress;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _BatteryPainter(
        progress: progress,
        outline: Theme.of(context).colorScheme.outlineVariant,
        bg: Theme.of(context).colorScheme.surfaceContainerHigh,
        tickColor: Theme.of(context).colorScheme.onSurfaceVariant,
      ),
    );
  }
}

class _BatteryPainter extends CustomPainter {
  _BatteryPainter({
    required this.progress,
    required this.outline,
    required this.bg,
    required this.tickColor,
  });

  final double progress;
  final Color outline;
  final Color bg;
  final Color tickColor;

  /// 电池主体宽 171dp、圆角 6dp；终端凸起比例取官方 `ea` 配置
  static const double _bodyWidth = 171;
  static const double _corner = 6;
  static const double _terminalWidthRatio = 0.0117;
  static const double _terminalHeightRatio = 0.3333;

  /// 颜色随进度插值：<7h 红、7~12h 橙、≥12h 绿
  static const List<(double, Color)> _stops = [
    (0.0, Color(0xFFF44336)),
    (0.2, Color(0x80F44336)),
    (0.4, Color(0x80FF9800)),
    (0.6, Color(0xFFFF9800)),
    (0.8, Color(0x804CAF50)),
    (1.0, Color(0xFF4CAF50)),
  ];

  Color _colorAt(double t) {
    for (var i = 0; i < _stops.length - 1; i++) {
      final (p0, c0) = _stops[i];
      final (p1, c1) = _stops[i + 1];
      if (t <= p1) {
        final f = p1 == p0 ? 0.0 : ((t - p0) / (p1 - p0)).clamp(0.0, 1.0);
        return Color.lerp(c0, c1, f) ?? c1;
      }
    }
    return _stops.last.$2;
  }

  @override
  void paint(Canvas canvas, Size size) {
    // 刻度区在底部：标签字号约 10，占 12dp；电池主体占上半
    const labelH = 12.0;
    final bodyH = size.height - labelH;
    final bodyW = _bodyWidth;

    final bodyRect = RRect.fromRectAndRadius(
      Rect.fromLTWH(0, 0, bodyW, bodyH),
      const Radius.circular(_corner),
    );

    // 电池主体：底色 + 1dp 描边
    canvas.drawRRect(bodyRect, Paint()..color = bg);
    canvas.drawRRect(
      bodyRect,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1
        ..color = outline,
    );

    // 右侧终端凸起
    final termW = bodyW * _terminalWidthRatio;
    final termH = bodyH * _terminalHeightRatio;
    final termRect = RRect.fromRectAndRadius(
      Rect.fromLTWH(bodyW + 1, (bodyH - termH) / 2, termW, termH),
      Radius.circular((termH * 0.4).clamp(0.0, _corner)),
    );
    canvas.drawRRect(termRect, Paint()..color = outline);

    // 进度填充：内缩 1dp，(宽-2dp)×progress，水平渐变（50% → 100% alpha）
    if (progress > 0) {
      final innerW = (bodyW - 2) * progress;
      final c = _colorAt(progress);
      final fillRect = Rect.fromLTWH(1, 1, innerW, bodyH - 2);
      canvas.save();
      canvas.clipRRect(RRect.fromRectAndRadius(
        Rect.fromLTWH(1, 1, innerW, bodyH - 2),
        const Radius.circular(_corner - 1),
      ));
      canvas.drawRect(
        fillRect,
        Paint()
          ..shader = LinearGradient(
            colors: [c.withValues(alpha: c.a * 0.5), c],
          ).createShader(fillRect),
      );
      canvas.restore();

      // 进度足够宽时，末端画一个 5×16dp 的白色 0.8 alpha 手柄
      if (innerW > 12) {
        final handleRect = RRect.fromRectAndRadius(
          Rect.fromLTWH(1 + innerW - 5, (bodyH - 16) / 2, 5, 16),
          const Radius.circular(2.5),
        );
        canvas.drawRRect(
          handleRect,
          Paint()..color = Colors.white.withValues(alpha: 0.8),
        );
      }
    }

    // 刻度：4 个固定档位，2.0h ~ 17.0h 等分 → 2h / 7h / 12h / 17h
    const labels = ['2h', '7h', '12h', '17h'];
    final tickPaint = Paint()..color = tickColor;
    for (var i = 0; i < labels.length; i++) {
      final x = bodyW * (i / (labels.length - 1));
      // 刻度线（小圆角竖线）
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(x - 0.5, bodyH - 4, 1, 4),
          const Radius.circular(0.5),
        ),
        tickPaint,
      );
      // 标签
      final tp = TextPainter(
        text: TextSpan(
          text: labels[i],
          style: TextStyle(
              fontSize: 9, fontWeight: FontWeight.w500, color: tickColor),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      var dx = x - tp.width / 2;
      dx = dx.clamp(0.0, size.width - tp.width);
      tp.paint(canvas, Offset(dx, bodyH));
    }
  }

  @override
  bool shouldRepaint(covariant _BatteryPainter old) =>
      old.progress != progress || old.bg != bg || old.outline != outline;
}

// ────────────────── 跑分卡（subTabFeedCard2/3）──────────────────
//
// 规格真源：`_rev/SUBTAB_CARDS_SPEC.md` 的「跑分卡（card2）规格」。
// 2x2 网格，每格 = 品牌渐变底 + 标题/分数/人数 + 右下角品牌图标。
// ⚠️ 该文档特别更正过：**不是**「ScoreChipsRow 胶囊行」，那是另一张综合评分卡。

/// 四格的固定顺序 + 展示名 + 强调色（前缀对齐 `CardUi.scoreNames`）。
///
/// ⚠️ 记录类型只能用**位置**字段 `(String, String, Color, IconData)`；
/// 写成 `(String prefix, String name, ...)` 是**非法**的（那是命名字段，
/// 需要 `({String prefix, ...})` 才对），而解构 `final (a,b,c,d) = e` 走的也是位置。
const List<(String, String, Color, IconData)> _scoreCells = [
  ('aututu', '安兔兔跑分', Color(0xFFF44336), Icons.speed),
  ('geek_bench_single', 'GeekBench 单核', Color(0xFF2196F3), Icons.memory),
  ('geek_bench_multi', 'GeekBench 多核', Color(0xFF2196F3), Icons.developer_board),
  ('3d_mark', 'WildLife Extreme', Color(0xFFFF9800), Icons.videogame_asset),
];

Widget _scoreGridCard(Datum data) {
  final extra = _extraDataOf(data);

  Widget cell((String, String, Color, IconData) c) {
    final (prefix, name, accent, icon) = c;
    // 实测键形是 `<前缀>_score_avg` / `<前缀>_score_count`
    // （服务端把 Antutu 拼成 `aututu`，不是 `antutu`）
    final avg = double.tryParse(extra?['${prefix}_score_avg']?.toString() ?? '');
    final count =
        double.tryParse(extra?['${prefix}_score_count']?.toString() ?? '');
    final hasScore = avg != null && avg > 0;

    return Container(
      height: 73,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(8),
        // 品牌浅色 → 透明（官方浅/深主题各一套起始色；这里按强调色低透明度近似）
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [accent.withValues(alpha: 0.18), Colors.transparent],
        ),
        border: Border.all(
          color: Theme.of(Get.context!).colorScheme.outlineVariant,
          width: 1,
        ),
      ),
      child: Stack(
        children: [
          Padding(
            // 右侧留 32dp 给图标
            padding: const EdgeInsets.fromLTRB(12, 8, 32, 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 12)),
                const SizedBox(height: 4),
                Text(
                  hasScore ? _scoreText(avg) : '暂无人分享',
                  maxLines: 1,
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: hasScore ? accent : null,
                  ),
                ),
                // 人数行：分数缺失时收起
                if (hasScore && count != null && count > 0) ...[
                  const SizedBox(height: 2),
                  Text(
                    '${count.toInt()}人分享',
                    style: TextStyle(
                      fontSize: 10,
                      color: Theme.of(Get.context!)
                          .colorScheme
                          .onSurfaceVariant,
                    ),
                  ),
                ],
              ],
            ),
          ),
          Positioned(
            right: 6,
            bottom: 6,
            child: Icon(icon, size: 20, color: accent.withValues(alpha: 0.85)),
          ),
        ],
      ),
    );
  }

  return _cardShell(
    child: Padding(
      padding: const EdgeInsets.all(8),
      child: Column(
        children: [
          Row(children: [
            Expanded(child: cell(_scoreCells[0])),
            const SizedBox(width: 8),
            Expanded(child: cell(_scoreCells[1])),
          ]),
          const SizedBox(height: 8),
          Row(children: [
            Expanded(child: cell(_scoreCells[2])),
            const SizedBox(width: 8),
            Expanded(child: cell(_scoreCells[3])),
          ]),
        ],
      ),
    ),
  );
}

/// 跑分数字：整数显示整数、否则一位小数
String _scoreText(double v) =>
    v == v.roundToDouble() ? v.toInt().toString() : v.toStringAsFixed(1);

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

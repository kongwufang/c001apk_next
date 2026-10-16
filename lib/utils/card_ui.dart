import 'dart:convert';

/// 卡片渲染规则表：说明「哪个模板用哪个骨架、取哪些字段」。
///
/// 对应 Android `util/CardUi.kt`。服务端下发的顶层卡片模板实测已有 40 多种且随时会冒新的，
/// 给每个模板写死渲染追不上，所以改成「App 提供有限骨架，规则表说明每个模板用哪个骨架、
/// 取哪些字段」，规则按实测结果全部写死在 [BUILTIN] 里。
///
/// 字段里带 `|` 是**回退链**：取第一个非空值（同一位置在不同模板下换字段名是常态）。
///
/// 链里的字段名**不受「模型里声明过什么」限制**：卡片和实体都留了一份原始 JSON
/// （`Datum.raw` / `Entity.raw`），服务端把商品图叫 `goods_pic`、把热搜热度叫 `sub_title`，
/// 规则照配就能取到。
///
/// 表里没配过的模板不必逐个补：按实体数量推断兜底，没见过的新模板也能排得像样。
class CardUi {
  const CardUi._();

  // ---- 骨架（App 内置能力，规则表只能挑不能造） ----
  static const String auto = 'auto';
  static const String list = 'list';
  static const String hscroll = 'hscroll';
  static const String grid = 'grid';

  /// 把 extraData 摊成若干行「名称 值」
  static const String stats = 'stats';

  /// 续航卡（模板 `subTabFeedCard1`）：80dp 卡 + 电池刻度条 + 平均亮屏
  static const String subtabBattery = 'subtabBattery';

  /// 跑分卡（模板 `subTabFeedCard2/3`）：2x2 品牌渐变格
  static const String subtabScore = 'subtabScore';

  /// 一行居中的 pill 链接（`selectorLinkCard`）
  static const String links = 'links';

  /// 分组标题行（`titleCard`）：左加粗分组名 + 右「更多」，整卡可点
  static const String section = 'section';

  /// 两栏并排的图文按钮（`iconButtonGridCard`）：宽高比 2.44 背景图 + 压在图上的文字
  static const String iconButtons = 'iconButtons';

  /// 横向滚动的分类入口（`linkCard`）：56dp 高横滚行 + 圆角 14dp 文字胶囊
  static const String linkTabs = 'linkTabs';

  /// 标题 + 胶囊列表（`capsuleListCard`）：图标+加粗标题一行 + 一片热词胶囊
  static const String capsules = 'capsules';

  /// 大图打底的酷安集条目（**实体**模板 `albumExpandCard`）
  static const String albumExpand = 'albumExpand';

  /// 只有标题/正文的纯文本卡
  static const String text = 'text';

  /// 不渲染（占 0 高度）
  static const String hidden = 'hidden';

  // ---- 实体形状 ----
  static const String shapeAuto = 'auto';

  /// 图标 + 标题 + 副标题横排（置顶引导、应用列表那类）
  static const String shapeRow = 'row';

  /// 图标 + 标题居中竖排（宫格链接那类）
  static const String shapeTile = 'tile';

  /// 大图 + 标题（酷图、动态封面那类）
  static const String shapeCover = 'cover';

  /// 只有文字
  static const String shapeText = 'text';

  /// 跑分项前缀 → 中文名（真源是产品页 tab 的 `rule.name`）
  static const Map<String, String> scoreNames = {
    'aututu': '安兔兔跑分',
    'geek_bench_single': 'GeekBench 单核',
    'geek_bench_multi': 'GeekBench 多核',
    '3d_mark': 'WildLife Extreme',
  };

  /// 跑分卡 `extraData` 的键是 `<rule.name>_score_avg`，比 [scoreNames] 的键多一段
  /// `_score`（实测 `aututu_score_avg` / `aututu_score_count`）。查名字时这一层要去掉再试一次。
  static const String _scoreSuffix = '_score';

  /// 实体字段的默认回退链。
  ///
  /// `entityTypeName` 一律排最后：它多半是 `pear_goods`、`product` 这类内部名，
  /// 有真实描述时不该把它顶出来（京东商品卡以前就显示成「商品名 / pear_goods」）。
  static const String defaultIcon =
      'logo|pic|goods_pic|icon|tpic|userAvatar|cover';
  static const String defaultItemTitle =
      'title|goods_title|device_title|alias_title|config_name|name';
  static const String defaultItemSubtitle =
      'sub_title|subTitle|description|message|goods_promo_title|goods_promo_price|entityTypeName';

  /// 卡片自身的字段默认链
  static const String defaultCardTitle = 'title|goods_title';
  static const String defaultCardSummary =
      'description|subTitle|sub_title|message';
  static const String defaultCardHero = 'pic';

  /// 把 `"a|b"` 拆成回退链；空串时用默认链
  static List<String> chain(String? raw, String fallback) {
    final src = (raw != null && raw.trim().isNotEmpty) ? raw : fallback;
    return src
        .split('|')
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
  }

  /// 实体的取值规则
  static const _defaultItemRule = ItemRule();

  static const List<String> hscrollTemplates = [
    'apkScrollCard', 'apkScrollCardWithBackground', 'apkImageScrollCard',
    'apkImageCard', 'colorfulScrollCard', 'iconLargeScrollCard', 'feedScrollCard',
    'iconScrollCard', 'imageScrollCard', 'imageCarouselCard', 'iconMiniScrollCard',
  ];

  static const List<String> listTemplates = [
    'iconListCard', 'apkListCard', 'listCard', 'feedListCard',
    'productTimelineListCard',
  ];

  /// 宫格模板 → 每行几个（真源：Android `_rev/card_tpl_shape.py` 实测的实体形状）
  static const Map<String, int> gridTemplates = {
    'subTabLinkCard': 2,
    'verticalColumnsFullPageCard': 2,
    'feedCoolPictureGridCard': 2,
    'rankAwardCard': 3,
    'iconMiniLinkGridCard': 3,
    'iconTabLinkGridCard': 4,
    'iconLinkGridCard': 5,
  };

  /// 内置规则表（对应 Android `CardUi.BUILTIN`）
  static final Map<String, Rule> builtin = () {
    final m = <String, Rule>{};

    // 子版块续航卡 / 跑分卡：官方是专门的 Compose 布局，摊成「名称 值」完全不像
    m['subTabFeedCard1'] = const Rule(layout: subtabBattery);
    m['subTabFeedCard2'] = const Rule(layout: subtabScore);
    m['subTabFeedCard3'] = const Rule(layout: subtabScore);

    // 首页/发现页顶部那排分类入口：官方是一行居中的 pill 链接
    m['selectorLinkCard'] = const Rule(layout: links);

    // 分组标题行（「应用更新」那类）：官方是一行「标题 + 更多」
    m['titleCard'] = const Rule(layout: section);

    // 「试试手气 / 爆棚热门」这类两栏图文按钮：宽图当底、文字压在图上
    m['iconButtonGridCard'] = const Rule(layout: iconButtons);

    // 「应用分类 / 游戏分类」这类横滚分类入口：56dp 高横滚胶囊行
    m['linkCard'] = const Rule(layout: linkTabs);

    // 热搜胶囊：图标+加粗标题一行 + 一片热词胶囊
    m['capsuleListCard'] = const Rule(layout: capsules);

    // 纯文本卡
    m['messageCard'] = const Rule(layout: text);
    m['textCard'] = const Rule(layout: text);

    // 实测是「自绘广告占位卡」（只有 sponsorType / reward_type，没内容）：
    // 配 auto，真有内容时能按实体排出来，没内容时自然不占高度
    m['sponsorArticleNews'] = const Rule(layout: auto);

    for (final t in hscrollTemplates) {
      m[t] = const Rule(layout: hscroll);
    }
    for (final t in listTemplates) {
      m[t] = const Rule(layout: list);
    }
    gridTemplates.forEach((t, span) {
      m[t] = Rule(layout: grid, span: span);
    });

    // 下面两条是实测过的例外，必须排在上面几个批量模板之后才不会被盖掉
    // 图片放大卡：整宽大图 + 标题，按实体数推断会排成宫格
    m['imageScaleCard'] = const Rule(layout: list);
    // 数码首页那排商品横滚：实体里是 goods_pic / goods_promo_price，配成 tile 才排得出价格行
    m['iconLargeScrollCard'] = const Rule(
      layout: hscroll,
      item: ItemRule(
        shape: shapeTile,
        icon: 'goods_pic|logo|pic',
        title: 'goods_title|title',
        subtitle: 'goods_promo_price|goods_promo_title|sub_title',
      ),
    );

    return m;
  }();

  static Rule? rule(String? template) =>
      template == null ? null : builtin[template];

  /// 实体级（**不是卡片级**）的官方重画表：实体自己的 `entityTemplate` 决定单个实体怎么画，
  /// 外层卡片照常走骨架。
  ///
  /// 实测 `albumExpandCard` 就是这样：外层卡片是 `listCard`，里面的 album 实体自带
  /// `entityTemplate=albumExpandCard`。所以这张表配的是**实体**模板。
  static const Map<String, String> entityOfficial = {
    'albumExpandCard': albumExpand,
  };

  /// 实体级官方重画：返回该实体要用的专属布局名，没有就返回 null（走通用实体排版）
  static String? entityLayout(String? entityTemplate) =>
      entityTemplate == null ? null : entityOfficial[entityTemplate];

  /// 实体的排列方式。规则表配了就用配的；没配过的模板按**实体数量**推断
  /// （一两条竖排、四五条宫格、再多横滚），所以没见过的新模板也能排得像样。
  static CardLayout layout(String? template, int entityCount) {
    final r = rule(template);
    switch (r?.layout) {
      case hidden:
        return const CardLayout(hidden: true);
      case stats:
        return const CardLayout(stats: true);
      case subtabBattery:
        return const CardLayout(official: subtabBattery);
      case subtabScore:
        return const CardLayout(official: subtabScore);
      case links:
        return const CardLayout(official: links);
      case section:
        return const CardLayout(official: section);
      case iconButtons:
        return const CardLayout(official: iconButtons);
      case linkTabs:
        return const CardLayout(official: linkTabs);
      case capsules:
        return const CardLayout(official: capsules);
      case text:
        return const CardLayout();
      case hscroll:
        return const CardLayout(horizontal: true);
      case list:
        return const CardLayout();
      case grid:
        return CardLayout(span: (r?.span ?? 1) < 1 ? 1 : r!.span);
    }
    // 兜底推断
    if (entityCount <= 0) return const CardLayout();
    if (entityCount == 1) return const CardLayout();
    if (entityCount <= 4) return CardLayout(span: entityCount);
    return const CardLayout(horizontal: true);
  }

  static List<String> titleField(String? template) =>
      chain(rule(template)?.titleField, 'title');

  static List<String> summaryField(String? template) =>
      chain(rule(template)?.summaryField, 'description|subTitle|message');

  static List<String> heroField(String? template) =>
      chain(rule(template)?.heroField, 'pic');

  static ItemRule itemRule(String? template) =>
      rule(template)?.item ?? _defaultItemRule;

  // ---------------------------------------------------------------- 统计卡

  /// 把统计卡的 `extraData` 摊成「名称 → 值」。两种下发形式都见过：
  ///   - 平铺：`{"avgData":"6.9","countData":"303.0"}`（names 里配中文名）；
  ///   - 成对：`{"aututu_score_avg":3041833,"aututu_score_count":25,...}`
  static List<MapEntry<String, String>> stats(
      String? template, String? extraData) {
    if (extraData == null || extraData.isEmpty) return const [];
    Object? decoded;
    try {
      decoded = jsonDecode(extraData);
    } catch (_) {
      return const [];
    }
    if (decoded is! Map) return const [];

    final r = rule(template)?.stats ?? const StatsRule();
    final rows = <MapEntry<String, String>>[];

    final avgKeys =
        decoded.keys.map((e) => e.toString()).where((k) => k.endsWith(r.avgSuffix));
    if (avgKeys.isNotEmpty) {
      for (final key in avgKeys) {
        final prefix = key.substring(0, key.length - r.avgSuffix.length);
        final value = decoded[key]?.toString() ?? '';
        if (value.isEmpty) continue;
        // 配 `<rule.name>_score`（服务端实际键形）或 `<rule.name>`（内置表写法）都能命中
        var name = r.names[prefix];
        if (name == null && prefix.endsWith(_scoreSuffix)) {
          name = r.names[prefix.substring(0, prefix.length - _scoreSuffix.length)];
        }
        name ??= r.names[key] ?? prefix;
        final countKey = '$prefix${r.countSuffix}';
        final hasCount = decoded.containsKey(countKey);
        final count = hasCount ? (decoded[countKey]?.toString() ?? '') : '';
        rows.add(MapEntry(
          name,
          count.isNotEmpty ? '${_pretty(value)} · $count' : _pretty(value),
        ));
      }
      return rows;
    }

    decoded.forEach((key, value) {
      final v = value?.toString() ?? '';
      if (v.isNotEmpty) {
        rows.add(MapEntry(r.names[key.toString()] ?? key.toString(), _pretty(v)));
      }
    });
    return rows;
  }

  /// 值美化：上万折成 `N.N万`、整数去尾零（原始数字如 3041833 会撑破一行）
  static String _pretty(String raw) {
    final n = double.tryParse(raw);
    if (n == null) return raw;
    if (n >= 10000) return '${(n / 10000).toStringAsFixed(1)}万';
    if (n == n.toInt().toDouble()) return n.toInt().toString();
    return raw;
  }
}

/// 单个实体的取值规则
class ItemRule {
  const ItemRule({
    this.shape = CardUi.shapeAuto,
    this.icon = CardUi.defaultIcon,
    this.title = CardUi.defaultItemTitle,
    this.subtitle = CardUi.defaultItemSubtitle,
    this.action = 'url',
  });

  final String shape;
  final String icon;
  final String title;
  final String subtitle;
  final String action;

  /// 上面几个字段写的是 `|` 回退链，渲染前拆成列表
  List<String> get iconChain => CardUi.chain(icon, CardUi.defaultIcon);
  List<String> get titleChain => CardUi.chain(title, CardUi.defaultItemTitle);
  List<String> get subtitleChain =>
      CardUi.chain(subtitle, CardUi.defaultItemSubtitle);
}

class StatsRule {
  const StatsRule({
    this.names = const {},
    this.avgSuffix = '_avg',
    this.countSuffix = '_count',
  });

  final Map<String, String> names;
  final String avgSuffix;
  final String countSuffix;
}

class Rule {
  const Rule({
    this.layout = CardUi.auto,
    this.span = 0,
    this.titleField = CardUi.defaultCardTitle,
    this.summaryField = CardUi.defaultCardSummary,
    this.heroField = CardUi.defaultCardHero,
    this.item,
    this.stats,
  });

  final String layout;
  final int span;
  final String titleField;
  final String summaryField;
  final String heroField;
  final ItemRule? item;
  final StatsRule? stats;
}

class CardLayout {
  const CardLayout({
    this.horizontal = false,
    this.span = 1,
    this.stats = false,
    this.hidden = false,
    this.official,
  });

  final bool horizontal;
  final int span;
  final bool stats;
  final bool hidden;

  /// 非空表示这条模板走「照官方重画」的专属渲染，值就是骨架名
  final String? official;
}

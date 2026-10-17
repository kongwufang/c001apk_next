import 'dart:convert';

/// 动态卡里的**附加信息槽**：点评评分 / 二手商品 / 商品清单。///
/// 忠实移植 Android `util/FeedExtra.kt`（167 行）。
///
/// ## 取舍是按实测定的，不是按官方槽表
/// Android 注释（基数 **482 条真实 feed 实体**）：
/// > 官方 24 槽表里有、但**真实数据里根本没有**的槽位一律不做：
/// > - 二手：`ershou_info` 非空 **23 条** → 做
/// > - 评分点评：`star` / `rating_score` / `rating_item_info` 非空 **48 条** → 做
/// > - 商品清单：`extra_title` / `extra_url` / `extra_pic` 非空 **24 条** → 做
/// > - 富源卡变体：`source_id` 全是空串 → **不做**
/// > - 已删除蒙层：`isDelete` / `foreground_text` 在 feed 上不存在 → **不做**
/// > - 问大家：只有 `questionUrl` / `questionStatus`，标题/内容全空 → **不做**
/// > - 京东商品清单 `extra_entities` / `goodsListInfo` → 不做
///
/// ## 本工程的分工
/// - **商品清单**：`Datum` 里已有 `extraTitle` / `extraUrl` / `extraPic` 字段，
///   `FeedCard._extraUrl()` 已经实现，所以这里不重复。
/// - **点评评分 / 二手**：`Datum` 里没有，从 `raw` 取 —— 就是本文件干的事。
class FeedExtra {
  const FeedExtra({this.rating, this.ershou});

  /// 点评评分
  final FeedRating? rating;

  /// 二手商品
  final FeedErshou? ershou;

  bool get isEmpty => rating == null && ershou == null;

  /// 从 `raw` 解析；两块都没有就返回 null（整块隐藏，不留空占位）。
  static FeedExtra? from(Map<String, dynamic>? raw) {
    if (raw == null) return null;
    final extra = FeedExtra(rating: _rating(raw), ershou: _ershou(raw));
    return extra.isEmpty ? null : extra;
  }

  // ────────────────── 点评评分 ──────────────────

  /// 样本（feed 74276786）：
  /// `star = 5`（五星制）、`rating_score = 10`（十分制）、
  /// `rating_item_info = [{"name":"续航","v4_score":8}, ...]` 六个维度，`v4_score` 是 0~10。
  static FeedRating? _rating(Map<String, dynamic> raw) {
    final items = <FeedRatingDim>[];
    final nodes = _array(raw, ['rating_item_info']);
    for (final node in nodes) {
      final name = _str(node, ['name']);
      final score = int.tryParse(_str(node, ['v4_score']));
      if (name.isEmpty || score == null) continue;
      items.add(FeedRatingDim(name: name, score: score));
    }
    final star = _str(raw, ['star']);
    final score = _str(raw, ['rating_score']);
    // 只有个孤零零的分数不算点评
    //（Android 注释：实测 `rank_score` 这种恒为 0 的字段到处都是）
    if (star.isEmpty && items.isEmpty) return null;
    return FeedRating(star: star, score: score, items: items);
  }

  // ────────────────── 二手 ──────────────────

  /// 样本（feed 74276825）内层键名：
  /// `product_title` / `product_price`（数字）/ `product_config_data`
  /// （**JSON 串**，里面 `configTitle`）/ `province` + `city` /
  /// `store_type_txt` / `product_logo` / `link_url` + `link_source`（"闲鱼"）。
  static FeedErshou? _ershou(Map<String, dynamic> raw) {
    final info = _obj(raw, ['ershou_info']);
    if (info == null) return null;
    final title = _str(info, ['product_title']);
    if (title.isEmpty) return null;
    final config = _obj(info, ['product_config_data']);
    final province = _str(info, ['province']);
    final city = _str(info, ['city']);
    return FeedErshou(
      title: title,
      price: _priceText(_str(info, ['product_price'])),
      config: config == null ? '' : _str(config, ['configTitle']),
      // Android：`if (city == province || city.isEmpty) province else "$province $city"`
      area: (city.isEmpty || city == province) ? province : '$province $city',
      storeType: _str(info, ['store_type_txt']),
      logo: _http2https(_str(info, ['product_logo'])),
      linkUrl: _str(info, ['link_url']),
      linkSource: _str(info, ['link_source']),
    );
  }

  // ────────────────── 工具（对应 Kotlin 的 private 方法）──────────────────

  /// 价格：样本里是数字 `190`，也兜字符串小数，非数字原样返回
  static String _priceText(String value) {
    if (value.isEmpty) return '';
    final number = double.tryParse(value);
    if (number == null) return value;
    if (number == number.toInt().toDouble()) return number.toInt().toString();
    return number.toStringAsFixed(2);
  }

  static Object? _element(Map<String, dynamic>? node, List<String> keys) {
    if (node == null) return null;
    for (final k in keys) {
      final v = node[k];
      if (v != null) return v;
    }
    return null;
  }

  static String _str(Map<String, dynamic>? node, List<String> keys) {
    final v = _element(node, keys);
    if (v == null) return '';
    if (v is String) return v.trim();
    if (v is num || v is bool) return v.toString().trim();
    return '';
  }

  static List<Map<String, dynamic>> _array(
      Map<String, dynamic>? node, List<String> keys) {
    final v = _element(node, keys);
    if (v is! List) return const [];
    return v.whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList();
  }

  /// 对象本身，或「装着对象的 JSON 串」都能拿到。
  ///
  /// Android 注释：`product_config_data` / `v4_rating_message` 这类字段实测都是
  /// **被转义的 JSON 字符串**，直接当对象取会炸。
  static Map<String, dynamic>? _obj(
      Map<String, dynamic>? node, List<String> keys) {
    final v = _element(node, keys);
    if (v == null) return null;
    if (v is Map) return Map<String, dynamic>.from(v);
    if (v is! String) return null;
    final text = v.trim();
    if (!text.startsWith('{')) return null;
    try {
      final parsed = jsonDecode(text);
      if (parsed is Map) return Map<String, dynamic>.from(parsed);
    } catch (_) {}
    return null;
  }

  static String _http2https(String url) =>
      url.startsWith('http://') ? url.replaceFirst('http://', 'https://') : url;
}

/// 点评评分
class FeedRating {
  const FeedRating({required this.star, required this.score, required this.items});

  /// 五星制（如 `"5"`）
  final String star;

  /// 十分制（如 `"10"`）
  final String score;

  /// 各维度分值
  final List<FeedRatingDim> items;

  /// 抬头文本（照抄 Android `bindRating`）：
  /// `"★ 5"` + `"10 分"` 用**两个空格**连；都空则用「点评」。
  String get header {
    final parts = <String>[
      if (star.isNotEmpty) '★ $star',
      if (score.isNotEmpty) '$score 分',
    ];
    return parts.isEmpty ? '点评' : parts.join('  ');
  }
}

/// 点评的一个维度
class FeedRatingDim {
  const FeedRatingDim({required this.name, required this.score});

  final String name;
  final int score;

  /// chip 文案：`"续航 8"`
  String get label => '$name $score';
}

/// 二手商品
class FeedErshou {
  const FeedErshou({
    required this.title,
    required this.price,
    required this.config,
    required this.area,
    required this.storeType,
    required this.logo,
    required this.linkUrl,
    required this.linkSource,
  });

  final String title;

  /// 已格式化好的价格数字串（可能为空）
  final String price;

  final String config;
  final String area;
  final String storeType;
  final String logo;
  final String linkUrl;
  final String linkSource;

  /// 标题：有 config 就 `"标题 · 配置"`（照抄 Android `bindErshou`）
  String get titleText => config.isEmpty ? title : '$title · $config';

  /// 价格：空则「面议」，否则 `"¥190"`
  String get priceText => price.isEmpty ? '面议' : '¥$price';

  /// 次要信息：`"广东 深圳 · 全新"`（各自为空则跳过）
  String get metaText => [area, storeType].where((e) => e.isNotEmpty).join(' · ');
}

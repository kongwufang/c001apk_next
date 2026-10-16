import 'package:json_annotation/json_annotation.dart';

part 'entity.g.dart';

@JsonSerializable()
class Entity {
  String? entityType;
  String? title;
  String? url;
  String? pic;
  String? logo;
  String? userAvatar;
  String? username;

  /// **原始 JSON** —— 规则表（`CardUi`）按任意服务端字段名取值靠它。
  ///
  /// 对应 Android `HomeFeedResponse.Entities.raw`。必要性：
  /// 实体字段名五花八门（应用列表给 `logo`、动态给 `pic`、京东商品给 `goods_pic`、
  /// 热搜热度给 `sub_title`、机型别名给 `alias_title`），而本类只声明了一部分。
  /// 有了 raw，「模板配什么字段名就能取到什么」，不必改模型发版。
  ///
  /// 由 `Datum.fromJson` 在解析后回填（见 datum.dart）；不进构造函数、
  /// 也不参与 toJson —— 它是解析产物，不是接口字段。
  Map<String, dynamic>? raw;

  Entity({
    this.entityType,
    this.title,
    this.url,
    this.pic,
    this.logo,
    this.userAvatar,
    this.username,
  });

  factory Entity.fromJson(Map<String, dynamic> json) {
    return _$EntityFromJson(json);
  }

  Map<String, dynamic> toJson() => _$EntityToJson(this);
}

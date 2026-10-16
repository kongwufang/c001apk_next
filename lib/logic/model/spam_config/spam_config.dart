/// 其他屏蔽项（关键字 / 用户 / 节点）的配置。
///
/// 对应 Android `logic/model/SpamConfigResponse.kt`。
///
/// `GET /v6/user/spamWordList` 返回 `spam_word_config`（**JSON 字符串**），实测形如：
/// ```json
/// {"node":[{"id":"4063","title":"一加13","targetFullId":"7000004063","targetType":"7"}],
///  "custom":[{"title":"关键词","nodeMd5":"..."}],
///  "user":[{"uid":"23047571","name":"...","logo":"...","url":"..."}]}
/// ```
///
/// ⚠️ 写入走 `POST /v6/account/updateConfig`（form：`key=spam_word_config`），
/// 但**写键与读键不同**：
/// | 类别 | 读键 | 写键 | 写值形状 |
/// |---|---|---|---|
/// | 关键字 | `custom` | **`word`** | `{"add":"词"}` / `{"cancel":"词"}` |
/// | 用户 | `user` | `user` | `{"add":"uid"}` / `{"cancel":"uid"}` |
/// | 节点 | `node` | `node` | `{"add":[{"tid":..,"name":..}]}` |
///
/// 这个不对称是 Android 端在注释里专门记下的坑，照抄即可。
library;

class SpamConfigResponse {
  SpamConfigResponse({this.data, this.message});

  final SpamConfigData? data;
  final String? message;

  factory SpamConfigResponse.fromJson(Map<String, dynamic> json) =>
      SpamConfigResponse(
        data: json['data'] == null
            ? null
            : SpamConfigData.fromJson(
                Map<String, dynamic>.from(json['data'] as Map)),
        message: json['message']?.toString(),
      );
}

class SpamConfigData {
  SpamConfigData({this.spamWordConfig, this.maxCount});

  /// 服务端字段名是下划线的 `spam_word_config`
  final String? spamWordConfig;
  final int? maxCount;

  factory SpamConfigData.fromJson(Map<String, dynamic> json) => SpamConfigData(
        spamWordConfig: json['spam_word_config']?.toString(),
        maxCount: (json['spam_word_config_max_count'] as num?)?.toInt(),
      );
}

class SpamConfig {
  const SpamConfig({
    this.custom = const [],
    this.user = const [],
    this.node = const [],
  });

  /// 关键字（读键叫 `custom`，写键叫 `word`）
  final List<SpamKeyword> custom;
  final List<SpamUser> user;
  final List<SpamNode> node;

  bool get isEmpty => custom.isEmpty && user.isEmpty && node.isEmpty;
  bool get isNotEmpty => !isEmpty;

  factory SpamConfig.fromJson(Map<String, dynamic> json) => SpamConfig(
        custom: _list(json['custom'], SpamKeyword.fromJson),
        user: _list(json['user'], SpamUser.fromJson),
        node: _list(json['node'], SpamNode.fromJson),
      );

  Map<String, dynamic> toJson() => {
        'custom': custom.map((e) => e.toJson()).toList(),
        'user': user.map((e) => e.toJson()).toList(),
        'node': node.map((e) => e.toJson()).toList(),
      };

  static List<T> _list<T>(
      Object? raw, T Function(Map<String, dynamic>) fromJson) {
    if (raw is! List) return const [];
    final out = <T>[];
    for (final e in raw) {
      if (e is Map) out.add(fromJson(Map<String, dynamic>.from(e)));
    }
    return out;
  }
}

class SpamKeyword {
  const SpamKeyword({this.title, this.nodeMd5});

  final String? title;
  final String? nodeMd5;

  factory SpamKeyword.fromJson(Map<String, dynamic> json) => SpamKeyword(
        title: json['title']?.toString(),
        nodeMd5: json['nodeMd5']?.toString(),
      );

  Map<String, dynamic> toJson() => {'title': title, 'nodeMd5': nodeMd5};
}

class SpamUser {
  const SpamUser({this.uid, this.name, this.logo, this.url});

  final String? uid;
  final String? name;
  final String? logo;
  final String? url;

  factory SpamUser.fromJson(Map<String, dynamic> json) => SpamUser(
        uid: json['uid']?.toString(),
        name: json['name']?.toString(),
        logo: json['logo']?.toString(),
        url: json['url']?.toString(),
      );

  Map<String, dynamic> toJson() =>
      {'uid': uid, 'name': name, 'logo': logo, 'url': url};
}

class SpamNode {
  const SpamNode({
    this.id,
    this.title,
    this.targetFullId,
    this.targetType,
    this.logo,
    this.url,
  });

  final String? id;
  final String? title;
  final String? targetFullId;
  final String? targetType;
  final String? logo;
  final String? url;

  factory SpamNode.fromJson(Map<String, dynamic> json) => SpamNode(
        id: json['id']?.toString(),
        title: json['title']?.toString(),
        targetFullId: json['targetFullId']?.toString(),
        targetType: json['targetType']?.toString(),
        logo: json['logo']?.toString(),
        url: json['url']?.toString(),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'targetFullId': targetFullId,
        'targetType': targetType,
        'logo': logo,
        'url': url,
      };
}

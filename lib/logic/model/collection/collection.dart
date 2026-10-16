/// 收藏夹（多收藏夹）相关模型。
///
/// 对应 Android `logic/model/CollectionResponse.kt`，字段与官方接口一致（HAR 实测）。
library;

class CollectionListResponse {
  CollectionListResponse({this.data, this.message});

  final List<CollectionData>? data;
  final String? message;

  factory CollectionListResponse.fromJson(Map<String, dynamic> json) {
    final raw = json['data'];
    List<CollectionData>? list;
    if (raw is List) {
      list = raw
          .whereType<Map>()
          .map((e) => CollectionData.fromJson(Map<String, dynamic>.from(e)))
          .toList();
    }
    return CollectionListResponse(
      data: list,
      message: json['message']?.toString(),
    );
  }
}

class CollectionDetailResponse {
  CollectionDetailResponse({this.data, this.message});

  final CollectionData? data;
  final String? message;

  factory CollectionDetailResponse.fromJson(Map<String, dynamic> json) {
    final raw = json['data'];
    return CollectionDetailResponse(
      data: raw is Map
          ? CollectionData.fromJson(Map<String, dynamic>.from(raw))
          : null,
      message: json['message']?.toString(),
    );
  }
}

/// `POST /v6/collection/addItem` 的响应。
///
/// ⚠️ **成功体只有 `data` 包装、没有 `status`**：
/// `{"data":{"favnum":95,"collect":1}}`；
/// 失败才有 `status`/`message`（-5「该内容已存在」、-7「该内容不存在于该收藏单」）。
///
/// ⇒ **判成功要看 `data` 在不在，不能看 status。**
class CollectionActionResponse {
  CollectionActionResponse({this.data, this.status, this.message});

  final CollectionActionData? data;
  final int? status;
  final String? message;

  bool get isSuccess => data != null;

  factory CollectionActionResponse.fromJson(Map<String, dynamic> json) {
    final raw = json['data'];
    return CollectionActionResponse(
      data: raw is Map
          ? CollectionActionData.fromJson(Map<String, dynamic>.from(raw))
          : null,
      status: (json['status'] as num?)?.toInt(),
      message: json['message']?.toString(),
    );
  }
}

/// addItem 的净结果：`favnum` = 服务端最新收藏数；`collect` = 该动态此刻是否在这个夹（1=已收藏）
class CollectionActionData {
  CollectionActionData({this.favnum, this.collect});

  final int? favnum;
  final int? collect;

  factory CollectionActionData.fromJson(Map<String, dynamic> json) =>
      CollectionActionData(
        favnum: (json['favnum'] as num?)?.toInt(),
        collect: (json['collect'] as num?)?.toInt(),
      );
}

/// 一次收藏/取消收藏的结果，弹窗往外传。
///
/// - `collectionId`：本次动的那个夹（弹窗拿它**就地改这一项的勾，不重拉列表**）
/// - `favnum` / `collect`：给详情页改底栏数字与星标
///
/// ★ 为什么不做本地加减：多收藏夹下同一个夹重复点、别人同时收藏都会让本地算法偏，
/// 所以**数字只认接口返回值**；状态也不再去重拉列表或详情
/// （详情那份 `favnum` 还有服务端缓存滞后，**重拉反而更不准**）。
class CollectionAction {
  CollectionAction({
    required this.collectionId,
    this.favnum,
    required this.collect,
  });

  final String collectionId;
  final int? favnum;
  final int collect;
}

/// 返回纯文本提示的接口（`delete` →「删除成功」、`removeUnUseItem` →「开始清除…」）
class StringDataResponse {
  StringDataResponse({this.data, this.message});

  final String? data;
  final String? message;

  factory StringDataResponse.fromJson(Map<String, dynamic> json) =>
      StringDataResponse(
        data: json['data']?.toString(),
        message: json['message']?.toString(),
      );
}

class CollectionCheckCountResponse {
  CollectionCheckCountResponse({this.data, this.message});

  final String? data;
  final String? message;

  factory CollectionCheckCountResponse.fromJson(Map<String, dynamic> json) =>
      CollectionCheckCountResponse(
        data: json['data']?.toString(),
        message: json['message']?.toString(),
      );
}

/// `POST /v6/collection/uploadImage` 返回图片 URL
class CollectionUploadResponse {
  CollectionUploadResponse({this.data, this.message});

  final String? data;
  final String? message;

  factory CollectionUploadResponse.fromJson(Map<String, dynamic> json) =>
      CollectionUploadResponse(
        data: json['data']?.toString(),
        message: json['message']?.toString(),
      );
}

class CollectionData {
  CollectionData({
    this.id,
    this.uid,
    this.username,
    this.title,
    this.description,
    this.coverPic,
    this.isOpen,
    this.isOpenTitle,
    this.itemNum,
    this.followNum,
    this.type,
    this.url,
    this.entityType,
    this.entityId,
    this.isBeCollected,
    this.isDefault,
  });

  final String? id;
  final String? uid;
  final String? username;
  final String? title;
  final String? description;

  /// 服务端字段名是下划线的 `cover_pic`
  final String? coverPic;
  final int? isOpen;
  final String? isOpenTitle;
  final int? itemNum;
  final int? followNum;
  final int? type;
  final String? url;
  final String? entityType;
  final String? entityId;

  /// 该动态是否已收藏进这个夹（1=已收藏），仅在带 `id=<动态id>&type=feed` 查询时下发。
  ///
  /// **可变**：收藏/取消后按 addItem 回的结果就地改，不重拉收藏夹列表。
  int? isBeCollected;

  /// 系统默认收藏夹
  final int? isDefault;

  factory CollectionData.fromJson(Map<String, dynamic> json) => CollectionData(
        id: json['id']?.toString(),
        uid: json['uid']?.toString(),
        username: json['username']?.toString(),
        title: json['title']?.toString(),
        description: json['description']?.toString(),
        coverPic: json['cover_pic']?.toString(),
        isOpen: (json['is_open'] as num?)?.toInt(),
        isOpenTitle: json['is_open_title']?.toString(),
        itemNum: (json['item_num'] as num?)?.toInt(),
        followNum: (json['follow_num'] as num?)?.toInt(),
        type: (json['type'] as num?)?.toInt(),
        url: json['url']?.toString(),
        entityType: json['entityType']?.toString(),
        entityId: json['entity_id']?.toString(),
        isBeCollected: (json['isBeCollected'] as num?)?.toInt(),
        isDefault: (json['isDefault'] as num?)?.toInt(),
      );

  /// 已在该收藏夹
  bool get collected => isBeCollected == 1;

  /// 公开收藏夹（`is_open` 非 0）
  bool get isPublic => (isOpen ?? 0) != 0;
}

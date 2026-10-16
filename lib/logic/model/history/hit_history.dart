/// 酷安**云端**浏览历史。
///
/// 对应 Android `logic/model/HistoryResponse.kt` 的 `HitHistoryListResponse` /
/// `HitHistoryData`。
///
/// ## ★ 为什么是「云端」
/// Android 端 2026-09-19 由**本地 Room 历史改为酷安云端历史**
/// （`GET /v6/user/hitHistoryList`）。原注释：
/// > 浏览记录由服务端在打开详情（feed/topic/product/apk detail）时**自动写入**，
/// > 客户端无需（也没有）显式上报接口（已实测验证）。
///
/// 所以本工程不需要「上报浏览记录」的代码 —— 只要打开过详情，服务端就记下了。
library;

class HitHistoryListResponse {
  HitHistoryListResponse({this.data, this.message});

  final List<HitHistoryData>? data;
  final String? message;

  factory HitHistoryListResponse.fromJson(Map<String, dynamic> json) {
    final raw = json['data'];
    List<HitHistoryData>? list;
    if (raw is List) {
      list = raw
          .whereType<Map>()
          .map((e) => HitHistoryData.fromJson(Map<String, dynamic>.from(e)))
          .toList();
    }
    return HitHistoryListResponse(
      data: list,
      message: json['message']?.toString(),
    );
  }
}

class HitHistoryData {
  HitHistoryData({
    this.title,
    this.description,
    this.logo,
    this.url,
    this.historyType,
    this.typeName,
    this.id,
    this.entityType,
    this.dateline,
  });

  /// ⚠️ 服务端下发的 title / description **带 HTML 标签**
  /// （如 `<a class="feed-link-tag">#话题#</a>`），渲染前要转纯文本。
  final String? title;
  final String? description;
  final String? logo;

  /// 相对路径，如 `/feed/73697914`、`/t/xxx`、`/product/4276`、`/apk/xxx`
  final String? url;
  final String? historyType;

  /// 「话题」「数码」等类型名，feed 时空串
  final String? typeName;

  /// 形如 `feed:73697914` —— 同时是**分页游标**（首页取首条的 id 当 firstItem，
  /// 每页取末条的 id 当 lastItem）与列表去重键
  final String? id;
  final String? entityType;
  final int? dateline;

  factory HitHistoryData.fromJson(Map<String, dynamic> json) => HitHistoryData(
        title: json['title']?.toString(),
        description: json['description']?.toString(),
        logo: json['logo']?.toString(),
        url: json['url']?.toString(),
        historyType: json['historyType']?.toString(),
        typeName: json['typeName']?.toString(),
        id: json['id']?.toString(),
        entityType: json['entityType']?.toString(),
        dateline: (json['dateline'] as num?)?.toInt(),
      );
}

/// 去掉服务端 title / description 里的 HTML 标签。
///
/// 对应 Android `Utils.kt` 的 `String.richToString()`（内部就是 `Html.fromHtml`）。
/// 这里只需要「取纯文本」，所以正则去标签 + 折叠空白即可 ——
/// 与 `OfflineCacheRepo._plainText` 同一套做法。
String richToPlain(String? html) {
  if (html == null) return '';
  return html
      .replaceAll(RegExp('<[^>]*>'), '')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
}

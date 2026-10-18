/// 搜索联想（输入过程中的下拉建议）。
///
/// 接口：`GET /v6/search/suggestSearchWordsNew?searchValue=xx&type=app`
///
/// 对齐 Android `logic/model/SearchSuggestResponse.kt`：
/// **首项固定是「搜索用户：<关键词>」**（`url = searchTab://user?keyword=xx`），
/// 其余项是普通联想词（`url = searchTab://all?keyword=xx`）✓
///
/// 首项要不要特殊处理，看 `title` 的前缀（Android 也是这么判的 ✓
/// `SearchSuggestAdapter.USER_PREFIX = "搜索用户："`）。
class SearchSuggestResponse {
  SearchSuggestResponse({
    this.status,
    this.message,
    this.data,
  });

  int? status;
  String? message;
  List<SearchSuggestData>? data;

  factory SearchSuggestResponse.fromJson(Map<String, dynamic> json) {
    return SearchSuggestResponse(
      status: (json['status'] as num?)?.toInt(),
      message: json['message'] as String?,
      data: (json['data'] as List?)
          ?.whereType<Map>()
          .map((e) => SearchSuggestData.fromJson(Map<String, dynamic>.from(e)))
          .toList(),
    );
  }
}

class SearchSuggestData {
  SearchSuggestData({
    this.logo,
    this.title,
    this.url,
    this.entityType,
  });

  /// 本地图标名（服务端下发的是名字，不是图片地址）
  String? logo;

  /// 联想词原文；首项形如「搜索用户：xxx」
  String? title;

  /// `searchTab://user?keyword=xx` / `searchTab://all?keyword=xx`
  String? url;
  String? entityType;

  factory SearchSuggestData.fromJson(Map<String, dynamic> json) {
    return SearchSuggestData(
      logo: json['logo'] as String?,
      title: json['title'] as String?,
      url: json['url'] as String?,
      entityType: json['entityType'] as String?,
    );
  }
}

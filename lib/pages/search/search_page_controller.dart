import 'dart:async';

import 'package:get/get.dart';
import 'package:hive/hive.dart';

import '../../logic/model/search_suggest.dart';
import '../../logic/network/network_repo.dart';
import '../../utils/storage_util.dart';

class SearchPageController extends GetxController {
  Box searchHistory = GStorage.searchHistory;
  RxList historyList = [].obs;

  // ── 搜索联想（对齐 Android `SearchFragment` 的 suggest 面板）──

  /// 当前联想结果
  final RxList<SearchSuggestData> suggestList = <SearchSuggestData>[].obs;

  /// 当前用来高亮的关键词（= 发请求时的输入 ✓ 不是输入框的最新值 ✗
  /// 否则快速输入时高亮会和内容对不上 ✗）
  final RxString suggestKeyword = ''.obs;

  /// 防抖：打字时不要每个字符都发一次请求 ✓
  Timer? _debounce;

  /// 快速输入时要丢弃过期响应 ✓ 每次请求带一个自增序号
  int _seq = 0;

  @override
  void onClose() {
    _debounce?.cancel();
    super.onClose();
  }

  /// 输入变化 → 防抖 250ms 后拉联想 ✓
  void fetchSuggest(String keyword) {
    _debounce?.cancel();
    final word = keyword.trim();
    if (word.isEmpty) {
      clearSuggest();
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 250), () async {
      final seq = ++_seq;
      try {
        final list = await NetworkRepo.getSuggestSearchWords(word);
        if (seq != _seq) return; // 有更新的请求发出了 ⇒ 丢掉这次的 ✗
        suggestKeyword.value = word;
        suggestList.value = list;
      } catch (_) {
        if (seq != _seq) return;
        suggestKeyword.value = word;
        suggestList.value = [];
      }
    });
  }

  void clearSuggest() {
    _debounce?.cancel();
    _seq++;
    suggestKeyword.value = '';
    suggestList.value = [];
  }

  @override
  void onInit() {
    super.onInit();
    _getData();
  }

  Future<void> _getData() async {
    historyList.value = searchHistory.get('searchHistory') ?? [];
  }

  void handleSearch(String value, [bool isDelete = false]) {
    List list = historyList.where((data) => data != value).toList();
    if (!isDelete) {
      list.insert(0, value);
    }
    historyList.value = list;
    searchHistory.put('searchHistory', list);
  }

  void clearAll() {
    historyList.value = [];
    searchHistory.put('searchHistory', []);
  }
}

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../components/cards/search_history_card.dart';
import '../../logic/model/search_suggest.dart';
import 'search_suggest_card.dart';
import '../../pages/search/search_page_controller.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';

import '../../utils/extensions.dart';
import '../../utils/share_link.dart';
import '../../utils/utils.dart';

class SearchPage extends StatefulWidget {
  const SearchPage({super.key});

  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage> {
  final TextEditingController _controller = TextEditingController();
  final FocusNode _focusNode = FocusNode();
  final String? _title = Get.parameters['title'];
  final String? _pageType = Get.parameters['pageType'];
  final String? _pageParam = Get.parameters['pageParam'];
  final _clearStream = StreamController<bool>();

  late final _searchPageController =
      Get.put(SearchPageController(), tag: '$_title$_pageType$_pageParam');

  @override
  void dispose() {
    _clearStream.close();
    _controller.dispose();
    _focusNode.dispose();
    Get.delete<SearchPageController>(tag: '$_title$_pageType$_pageParam');
    super.dispose();
  }

  void onSearch(String text) async {
    final keyWord = text.trim();
    if (keyWord.isEmpty) {
      // Android 同：`Toast("请输入关键词")`
      SmartDialog.showToast('请输入关键词');
      return;
    }
    // ★ 粘进来的是**酷安分享链接**就直接跳过去，别拿它当关键词去搜。
    //
    // 对齐 Android `SearchFragment.startSearch`：
    // ```kotlin
    // // 粘进来的是酷安分享链接就直接跳过去，别拿它当关键词去搜（与剪贴板识别同一套判定）
    // NetWorkUtil.findCoolapkLink(keyWord)?.let {
    //     NetWorkUtil.openLink(requireContext(), it, null)
    //     return
    // }
    // ```
    // 判定用的是与「剪贴板识别」**同一套** `findCoolapkLink`。
    final link = ShareLinkPrompter.findCoolapkLink(keyWord);
    if (link != null) {
      Utils.onOpenLink(link, null);
      return;
    }
    _searchPageController.handleSearch(keyWord);
    await Get.toNamed('/searchResult', parameters: {
      'keyword': keyWord,
      if (!_title.isNullOrEmpty) 'title': _title!,
      if (!_pageType.isNullOrEmpty) 'pageType': _pageType!,
      if (!_pageParam.isNullOrEmpty) 'pageParam': _pageParam!,
    });
    // ★ 从结果页回来 ⇒ 回到**默认态**（历史），而不是停在联想列表 ✓
    //   对齐 Android `SearchFragment` 的同一处理 ✓
    _controller.clear();
    _clearStream.add(false);
    _searchPageController.clearSuggest();
    if (mounted) setState(() {});
    _focusNode.requestFocus();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: TextField(
          focusNode: _focusNode,
          onTap: () => _focusNode.requestFocus(),
          controller: _controller,
          style: const TextStyle(fontSize: 18),
          onChanged: (value) {
            _clearStream.add(value.isNotEmpty);
            // ★ 搜索联想（对齐 Android `SearchFragment` 的 TextWatcher）
            //   控制器内部有 250ms 防抖 ✓ 这里只管把最新输入交出去 ✓
            _searchPageController.fetchSuggest(value);
            // 输入框非空 ⇄ 空 要切换面板（联想 ⇄ 历史）⇒ 触发重建 ✓
            setState(() {});
          },
          decoration: InputDecoration(
            border: InputBorder.none,
            hintText: '搜索${!_title.isNullOrEmpty ? '：$_title' : ''}',
            hintStyle: const TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.normal,
            ),
          ),
          textInputAction: TextInputAction.search,
          autofocus: true,
          onSubmitted: (value) {
            onSearch(value);
          },
        ),
        actions: [
          StreamBuilder(
            initialData: false,
            stream: _clearStream.stream,
            builder: (_, snapshot) => snapshot.data == true
                ? IconButton(
                    onPressed: () {
                      _controller.clear();
                      _focusNode.requestFocus();
                      _clearStream.add(false);
                    },
                    icon: const Icon(Icons.clear),
                    tooltip: '清空',
                  )
                : const SizedBox.shrink(),
          ),
          IconButton(
            onPressed: () {
              onSearch(_controller.text);
            },
            icon: const Icon(Icons.search),
            tooltip: '搜索',
          ),
        ],
        bottom: const PreferredSize(
          preferredSize: Size.zero,
          child: Divider(height: 1),
        ),
      ),
      // 输入非空 ⇒ 联想面板；空 ⇒ 历史面板 ✓
      // （对齐 Android `SearchFragment.updatePanelState` 的 visible 切换 ✓）
      body: _controller.text.trim().isNotEmpty
          ? _buildSuggestPanel()
          : Obx(() => _searchPageController.historyList.isNotEmpty
          ? Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const SizedBox(width: 10),
                    const Expanded(
                      flex: 1,
                      child: Text(
                        '搜索历史',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: '清空',
                      onPressed: () => showDialog(
                          context: context,
                          builder: (context) => AlertDialog(
                                title: const Text('确定清除全部搜索历史？'),
                                actions: [
                                  TextButton(
                                    onPressed: () => Get.back(),
                                    child: const Text('取消'),
                                  ),
                                  TextButton(
                                    onPressed: () {
                                      Get.back();
                                      _searchPageController.clearAll();
                                    },
                                    child: const Text('确定'),
                                  )
                                ],
                              )),
                      icon: const Icon(Icons.clear_all),
                    )
                  ],
                ),
                SingleChildScrollView(
                  padding: EdgeInsets.only(
                    left: 10,
                    right: 10,
                    bottom: 10 + MediaQuery.of(context).padding.bottom,
                  ),
                  physics: const AlwaysScrollableScrollPhysics(
                    parent: BouncingScrollPhysics(),
                  ),
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: _searchPageController.historyList
                        .map(
                          (text) => SearchHistoryCard(
                            text: text,
                            onTap: () {
                              _controller.text = text;
                              _clearStream.add(true);
                              onSearch(text);
                            },
                            onLongPress: () =>
                                _searchPageController.handleSearch(text, true),
                          ),
                        )
                        .toList(),
                  ),
                ),
              ],
            )
          : const SizedBox()),
    );
  }

  /// 搜索联想面板
  Widget _buildSuggestPanel() {
    return Obx(() {
      final cs = Theme.of(Get.context!).colorScheme;
      final list = _searchPageController.suggestList;
      final keyword = _searchPageController.suggestKeyword.value;
      if (list.isEmpty) {
        // 还没拉到（防抖窗口内）或确实没有联想 ⇒ 不给空白页，给一句提示 ✓
        return Center(
          child: Text(
            '输入关键词后显示联想',
            style: TextStyle(fontSize: 13, color: cs.outline),
          ),
        );
      }
      return ListView.builder(
        padding: EdgeInsets.only(
          bottom: 10 + MediaQuery.of(Get.context!).padding.bottom,
        ),
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        itemCount: list.length,
        itemBuilder: (context, i) {
          final SearchSuggestData item = list[i];
          return SearchSuggestCard(
            data: item,
            keyword: keyword,
            // 点整行：直接搜 ✓（「搜索用户：」项也按词去搜 ✓
            // 对齐 Android：onItemClick 传的是**去掉前缀**的词 ✓）
            onTap: (word, isUser) {
              _controller.text = word;
              _clearStream.add(true);
              onSearch(word);
            },
            // 点箭头：只回填 ✓
            onFill: (word) {
              _controller.text = word;
              _clearStream.add(true);
              _searchPageController.fetchSuggest(word);
              setState(() {});
            },
          );
        },
      );
    });
  }
}

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../components/cards/search_history_card.dart';
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
      body: Obx(() => _searchPageController.historyList.isNotEmpty
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
}

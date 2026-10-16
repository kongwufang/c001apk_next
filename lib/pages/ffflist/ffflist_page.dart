// ignore_for_file: constant_identifier_names

import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';

import '../../pages/carousel/carousel_page.dart';
import '../../pages/ffflist/ffflist_content.dart';
import '../../logic/model/collection/collection.dart';
import '../../logic/network/network_repo.dart';
import '../../pages/collection/collection_pick_sheet.dart';
import '../../utils/extensions.dart';
import '../../utils/global_data.dart';

enum FFFListType {
  FEED,
  FOLLOW,
  APK,
  USER_FOLLOW,
  FAN,
  RECENT,
  LIKE,
  REPLY,
  REPLYME,
  FAV,
  HISTORY,
  COLLECTION,
  COLLECTION_ITEM
}

class FFFListPage extends StatefulWidget {
  const FFFListPage({super.key});

  @override
  State<FFFListPage> createState() => _FFFListPageState();
}

class _FFFListPageState extends State<FFFListPage>
    with TickerProviderStateMixin {
  late final String? _uid = Get.arguments['uid'] ?? GlobalData().uid;
  late final FFFListType _type = Get.arguments['type'];
  late final String? _id = Get.arguments['id'];
  late final String? _title = Get.arguments['title'];

  late final bool _isMe = GlobalData().uid == _uid;

  late final _followList = ['用户', '话题', '数码', '应用'];
  late final _replyList = ['我的回复', '我收到的回复'];

  late final _tabList = _type == FFFListType.FOLLOW
      ? _followList
      : _type == FFFListType.REPLY
          ? _replyList
          : null;
  late final _tabController =
      TabController(length: _tabList!.length, vsync: this);

  late final _titleText = _title ??
      switch (_type) {
        FFFListType.FEED => '我的动态',
        FFFListType.FOLLOW => '我的关注',
        FFFListType.USER_FOLLOW => _isMe ? '我关注的人' : 'TA关注的人',
        FFFListType.FAN => _isMe ? '关注我的人' : 'TA的粉丝',
        FFFListType.RECENT => '我的常去',
        FFFListType.LIKE => '我的赞',
        FFFListType.REPLY => '我的回复',
        FFFListType.COLLECTION => '我的收藏单',
        FFFListType.APK => '',
        FFFListType.REPLYME => '',
        FFFListType.FAV => '',
        FFFListType.HISTORY => '',
        FFFListType.COLLECTION_ITEM => '',
      };

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(
          _titleText,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        // ★ 收藏夹内容页（进入具体某个夹）才有右上角编辑菜单 ——
        //   对应 Android `CollectionFragment.initEditEntry`（`if (id.isNullOrEmpty()) return`）。
        //   「我的收藏单」那一级（COLLECTION）不显示。
        actions: _type == FFFListType.COLLECTION_ITEM
            ? [_collectionMenu()]
            : null,
        bottom: _tabList != null
            ? TabBar(
                controller: _tabController,
                tabs: _tabList.map((item) => Tab(text: item)).toList())
            : const PreferredSize(
                preferredSize: Size.zero, child: Divider(height: 1)),
      ),
      body: _tabList != null
          ? TabBarView(
              controller: _tabController,
              children: _type == FFFListType.REPLY
                  ? List.generate(
                      2,
                      (index) => FFFListContent(
                          uid: _uid,
                          id: _id,
                          type: index == 0
                              ? FFFListType.REPLY
                              : FFFListType.REPLYME)).toList()
                  : List.generate(4, (index) {
                      if (index == 0 || index == 3) {
                        return FFFListContent(
                            uid: _uid,
                            id: _id,
                            type: index == 0
                                ? FFFListType.FOLLOW
                                : FFFListType.APK);
                      } else {
                        return CarouselPage(
                          isInit: false,
                          url: index == 1
                              ? '#/topic/userFollowTagList'
                              : '#/product/followProductList',
                          title: index == 1 ? '我关注的话题' : '我关注的数码吧',
                        );
                      }
                    }).toList())
          : FFFListContent(
              uid: _type == FFFListType.COLLECTION ? null : _uid,
              id: _id,
              type: _type,
            ),
    );
  }

  // ─────────── 收藏夹编辑菜单（仅内容页）───────────
  //
  // 对应 Android `CollectionFragment.showEditActions`：
  // 三个动作 —— 编辑收藏夹信息 / 清除无效内容 / 删除收藏夹。

  Widget _collectionMenu() {
    return PopupMenuButton<int>(
      onSelected: (v) {
        switch (v) {
          case 0:
            _editCollectionInfo();
            break;
          case 1:
            _confirmClearUnUse();
            break;
          case 2:
            _confirmDelete();
            break;
        }
      },
      itemBuilder: (_) => const [
        PopupMenuItem(value: 0, child: Text('编辑收藏夹信息')),
        PopupMenuItem(value: 1, child: Text('清除无效内容')),
        PopupMenuItem(value: 2, child: Text('删除收藏夹')),
      ],
    );
  }

  /// 编辑收藏夹信息：先拉一次**最新**的详情（Android 也是 `loadDetail` 后再弹），
  /// 再复用「收藏到」面板那个对话框。
  Future<void> _editCollectionInfo() async {
    final id = _id ?? '';
    if (id.isEmpty) return;

    SmartDialog.showLoading();
    CollectionData? item;
    try {
      final res = await NetworkRepo.getCollectionDetail(id: id);
      final body = res.data;
      if (body is Map) {
        item = CollectionDetailResponse.fromJson(
                Map<String, dynamic>.from(body))
            .data;
      }
    } catch (_) {
      item = null;
    }
    SmartDialog.dismiss();
    if (!mounted) return;
    if (item == null) {
      SmartDialog.showToast('加载收藏夹信息失败');
      return;
    }

    final result = await showDialog<CollectionEditResult>(
      context: context,
      builder: (_) => CollectionEditDialog(item: item),
    );
    if (result == null) return;

    var pic = '';
    var coverFailed = false;
    if (result.cover != null) {
      final uploaded = await uploadCollectionCover(result.cover!);
      if (uploaded == null) {
        coverFailed = true;
      } else {
        pic = uploaded;
      }
    }
    SmartDialog.showToast(
      await applyCollectionEdit(item, result, pic, coverFailed),
    );
  }

  /// 清除无效内容（服务端异步执行，约 5 分钟后生效）
  Future<void> _confirmClearUnUse() async {
    final id = _id ?? '';
    if (id.isEmpty) return;
    final ok = await Get.dialog<bool>(
      AlertDialog(
        title: const Text('清除无效内容'),
        content: const Text('将清除该收藏夹内已失效的内容，约 5 分钟后生效，确定继续？'),
        actions: [
          TextButton(
              onPressed: () => Get.back(result: false),
              child: const Text('取消')),
          FilledButton(
              onPressed: () => Get.back(result: true),
              child: const Text('确定')),
        ],
      ),
    );
    if (ok != true) return;

    SmartDialog.showLoading();
    String? msg;
    try {
      final res = await NetworkRepo.postCollectionRemoveUnUseItem(colId: id);
      final body = res.data;
      if (body is Map) msg = body['data']?.toString();
    } catch (_) {}
    SmartDialog.dismiss();
    SmartDialog.showToast(msg ?? '操作失败');
  }

  Future<void> _confirmDelete() async {
    final id = _id ?? '';
    if (id.isEmpty) return;
    final ok = await Get.dialog<bool>(
      AlertDialog(
        title: const Text('删除收藏夹'),
        content: Text('删除后不可恢复，确定删除「$_titleText」？'),
        actions: [
          TextButton(
              onPressed: () => Get.back(result: false),
              child: const Text('取消')),
          FilledButton(
              onPressed: () => Get.back(result: true),
              child: const Text('确定')),
        ],
      ),
    );
    if (ok != true) return;

    SmartDialog.showLoading();
    var success = false;
    String? msg;
    try {
      final res = await NetworkRepo.postCollectionDelete(id: id);
      final body = res.data;
      if (body is Map) {
        msg = body['data']?.toString();
        // 返回纯文本提示（「删除成功」）；有 data 就算成功
        success = body['data'] != null;
      }
    } catch (_) {}
    SmartDialog.dismiss();
    SmartDialog.showToast(msg ?? (success ? '删除成功' : '删除失败'));
    if (success) Get.back();
  }
}

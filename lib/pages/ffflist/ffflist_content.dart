import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../components/common_body.dart';
import '../../pages/ffflist/ffflist_controller.dart';
import '../../pages/ffflist/ffflist_page.dart' show FFFListType;

class FFFListContent extends StatefulWidget {
  const FFFListContent({
    super.key,
    required this.uid,
    required this.id,
    required this.type,
  });

  final String? uid;
  final String? id;
  final FFFListType type;

  @override
  State<FFFListContent> createState() => _FFFListContentState();
}

class _FFFListContentState extends State<FFFListContent> {
  late final _fffController = Get.put(
    FFFListController(
      type: widget.type,
      url: switch (widget.type) {
        FFFListType.FEED => '/v6/user/feedList?showAnonymous=0&isIncludeTop=1',
        FFFListType.FOLLOW => '/v6/user/followList',
        FFFListType.APK => '/v6/user/apkFollowList',
        FFFListType.USER_FOLLOW => '/v6/user/followList',
        FFFListType.FAN => '/v6/user/fansList',
        FFFListType.RECENT => '/v6/user/recentHistoryList',
        FFFListType.LIKE => '/v6/user/likeList',
        FFFListType.REPLY => '/v6/user/replyList',
        FFFListType.REPLYME => '/v6/user/replyToMeList',
        FFFListType.FAV => throw UnimplementedError(),
        FFFListType.HISTORY => throw UnimplementedError(),
        FFFListType.COLLECTION => '/v6/collection/list',
        FFFListType.COLLECTION_ITEM => '/v6/collection/itemList',
      },
      id: widget.id,
      uid: widget.uid,
      showDefault: widget.type == FFFListType.COLLECTION ? 0 : null,
    ),
    tag: '${widget.type.name}${widget.id}${widget.uid}',
  );

  @override
  void dispose() {
    _fffController.scrollController?.dispose();
    Get.delete<FFFListController>(
      tag: '${widget.type.name}${widget.id}${widget.uid}',
    );
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // ★ 这几个列表是「某人的内容列表」，整页要走**同一种排法**。
    //
    // 对应 Android `FollowViewModel.FULL_CARD_LISTS`：
    //   setOf("feed", "like", "reply", "replyToMe", "recentHistory")
    // 原注释：
    // > 这几个 type 都是「某人的内容列表」，整页按完整动态卡排。
    // > （htmlFeedList 实测 17 条里 6 条 feedCover、11 条 feed，照模板分派会在一页里
    // >   混出「紧凑封面卡 / 完整动态卡」两种排法）
    const fullCardLists = {
      FFFListType.FEED,
      FFFListType.LIKE,
      FFFListType.REPLY,
      FFFListType.REPLYME,
      FFFListType.RECENT,
    };
    return commonBody(
      _fffController,
      feedCoverAsFeed: fullCardLists.contains(widget.type),
    );
  }
}

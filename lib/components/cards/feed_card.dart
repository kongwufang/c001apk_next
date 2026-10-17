import 'package:c001apk_flutter/components/network_image.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';

import '../../components/cards/icon_mini_scroll_card.dart' show miniCardItem;
import '../../components/html_text.dart';
import '../../components/icon_text.dart';
import '../../components/imageview.dart';
import '../../components/like_button.dart';
import '../../components/no_splash_factory.dart';
import '../../logic/model/feed/datum.dart';
import '../../logic/model/feed/feed_extra.dart';
import '../../logic/model/feed/feed_video.dart';
import '../../utils/date_util.dart';
import '../clamped_html.dart';
import 'extra_slot.dart';
import 'video_slot.dart';
import '../../utils/extensions.dart';
import '../../utils/global_data.dart';
import '../../utils/storage_util.dart';
import '../../utils/utils.dart';

class FeedCard extends StatelessWidget {
  const FeedCard({
    super.key,
    required this.data,
    this.isFeedContent = false,
    this.isHistory = false,
    this.onDelete,
    this.onStickTop,
    this.onBlock,
    this.onLike,
  });

  final Datum data;
  final bool isFeedContent;
  final bool isHistory;
  final Function(dynamic id)? onDelete;
  final Function(dynamic uid)? onBlock;


  /// 个人主页置顶 / 取消置顶：`(id, 当前是否已置顶)`。
  ///
  /// ★ 真正的接口调用 + **本地切标记 / 排到最前**都在
  /// `CommonController.postStickTop` 里（对齐 Android `onPostStickTop`），
  /// 这里只负责把点击传上去。
  final Function(dynamic id, bool isStickTop)? onStickTop;
  final Function(dynamic id, dynamic like)? onLike;

  /// 本条的**视频信息**；不是视频动态则为 null。
  ///
  /// 逻辑全在 `FeedVideo.from`（移植自 Android `util/FeedVideo.kt`），
  /// 这里只是个 getter —— 每次 build 算一次，成本可以忽略。
  FeedVideo? get _video => FeedVideo.from(
        data.raw,
        feedType: data.feedType,
        feedTypeName: data.feedTypeName,
      );

  /// 本条的**附加信息**（点评评分 / 二手）；都没有则为 null（整块隐藏）。
  ///
  /// 商品清单（`extraTitle` / `extraUrl` / `extraPic`）走的是
  /// `Datum` 的现成字段，由 `_extraUrl()` 渲染，不在这里。
  FeedExtra? get _extra => FeedExtra.from(data.raw);

  void _onViewFeed() {
    Get.toNamed('/feed/${data.id}');
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color:
          isFeedContent ? null : Theme.of(context).colorScheme.surfaceContainer,
      borderRadius:
          isFeedContent ? null : const BorderRadius.all(Radius.circular(12)),
      child: InkWell(
        highlightColor: isFeedContent ? Colors.transparent : null,
        splashColor: isFeedContent ? Colors.transparent : null,
        splashFactory: isFeedContent ? NoSplashFactory() : null,
        onTap: isFeedContent ? null : _onViewFeed,
        onLongPress: () =>
            Get.toNamed('/copy', parameters: {'text': data.message.orEmpty}),
        // 桌面端：右键 = 长按（见 components/secondary_tap.dart）
        onSecondaryTap: () =>
            Get.toNamed('/copy', parameters: {'text': data.message.orEmpty}),
        borderRadius: isFeedContent ? null : BorderRadius.circular(12),
        child: Padding(
          padding: EdgeInsets.only(bottom: isFeedContent ? 12 : 10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              header(
                context,
                data,
                isFeedContent,
                isHistory: isHistory,
                onDelete: onDelete,
                onStickTop: onStickTop,
                onBlock: () {
                  if (onBlock != null) {
                    onBlock!(data.uid);
                  }
                },
              ),
              ..._message(),
              // ★ 视频槽：视频动态的 `pic`/`picArr` **实测全为空**，
              //   封面只在 `media_info.cover` 里 —— 九图槽位对视频不出图，
              //   必须由它自己出封面（Android `item_home_feed.xml` 的同名注释）。
              //   与九图槽在数据上互斥，两个槽位落同一位置。
              if (_video != null) VideoSlot(video: _video!),
              if (!data.picArr.isNullOrEmpty) _image(),
              if (!data.forwardSourceType.isNullOrEmpty)
                _forwardSourceFeed(context),
              // ★ 附加信息槽（点评评分 / 二手）：放在媒体区之下、热评之上
              //   （Android `item_home_feed.xml` 里 `extraPart` 的位置）。
              //   三块「点评 / 二手 / 商品清单」在数据上基本互斥，
              //   商品清单由下面的 `_extraUrl()` 负责，这里管另外两块。
              if (_extra != null)
                ExtraSlot(extra: _extra!, isFeedContent: isFeedContent),
              if (!data.extraUrl.isNullOrEmpty) _extraUrl(context),
              if (!data.replyRows.isNullOrEmpty) _hotReply(context),
              bottomInfo(context, data, isFeedContent, _onViewFeed, () {
                if (onLike != null) {
                  onLike!(data.id, data.userAction?.like);
                }
              }),
              if (data.targetRow != null || !data.relationRows.isNullOrEmpty)
                _rows(context),
            ],
          ),
        ),
      ),
    );
  }

  Widget _image() {
    return Padding(
      padding: EdgeInsets.only(
        left: isFeedContent ? 16 : 10,
        top: 10,
        right: isFeedContent ? 16 : 10,
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          double maxWidth = constraints.maxWidth;
          return image(
            maxWidth,
            data.picArr!,
          );
        },
      ),
    );
  }

  Widget _forwardSourceFeed(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: EdgeInsets.only(
        left: isFeedContent ? 16 : 10,
        top: 10,
        right: isFeedContent ? 16 : 10,
      ),
      child: Material(
        color: isFeedContent
            ? Theme.of(context).colorScheme.surfaceContainer
            : Theme.of(context).colorScheme.surface,
        borderRadius: const BorderRadius.all(Radius.circular(12)),
        child: InkWell(
          onTap: data.forwardSourceFeed != null
              ? () => Get.toNamed('/feed/${data.forwardSourceFeed?.id}')
              : null,
          onLongPress: data.forwardSourceFeed != null
              ? () => Get.toNamed('/copy',
                  parameters: {'text': data.forwardSourceFeed?.message ?? ''})
              : null,
          // 桌面端：右键 = 长按（见 components/secondary_tap.dart）
          onSecondaryTap: data.forwardSourceFeed != null
              ? () => Get.toNamed('/copy',
                  parameters: {'text': data.forwardSourceFeed?.message ?? ''})
              : null,
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: data.forwardSourceFeed == null
                ? Text(
                    '动态已被删除',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.outline,
                    ),
                  )
                : Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (!data
                          .forwardSourceFeed!.messageTitle.isNullOrEmpty) ...[
                        htmlText(
                            '<a class="feed-link-uname" href="/u/${data.forwardSourceFeed?.uid}">@${data.forwardSourceFeed?.username}</a>: ${data.forwardSourceFeed?.messageTitle}'),
                        htmlText(data.forwardSourceFeed?.message ?? ''),
                      ],
                      if (data.forwardSourceFeed!.messageTitle.isNullOrEmpty)
                        htmlText(
                            '<a class="feed-link-uname" href="/u/${data.forwardSourceFeed?.uid}">@${data.forwardSourceFeed?.username}</a>: ${data.forwardSourceFeed?.message}'),
                      if (!data.forwardSourceFeed!.picArr.isNullOrEmpty) ...[
                        const SizedBox(height: 10),
                        LayoutBuilder(
                          builder: (context, constraints) => image(
                              constraints.maxWidth,
                              data.forwardSourceFeed!.picArr!),
                        ),
                      ],
                    ],
                  ),
          ),
        ),
      ),
    );
  }

  Widget _extraUrl(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: EdgeInsets.only(
        left: isFeedContent ? 16 : 10,
        top: 10,
        right: isFeedContent ? 16 : 10,
      ),
      child: Material(
        color: isFeedContent
            ? Theme.of(context).colorScheme.surfaceContainer
            : Theme.of(context).colorScheme.surface,
        borderRadius: const BorderRadius.all(Radius.circular(12)),
        child: InkWell(
          onTap: () => Utils.onOpenLink(data.extraUrl!, data.extraTitle ?? ''),
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: Row(
              children: [
                !data.extraPic.isNullOrEmpty
                    ? clipNetworkImage(
                        data.extraPic!,
                        radius: 8,
                        width: 40,
                        height: 40,
                        fit: BoxFit.cover,
                      )
                    : Container(
                        width: 40,
                        height: 40,
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(8),
                          color: Theme.of(context).colorScheme.primary,
                        ),
                        child: Icon(
                          Icons.link_rounded,
                          color: Theme.of(context).colorScheme.onPrimary,
                        ),
                      ),
                const SizedBox(width: 10),
                Expanded(
                  flex: 1,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        data.extraTitle.toString(),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      Text(
                        data.extraUrl.toString(),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                            fontSize: 13,
                            color: Theme.of(context).colorScheme.outline),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _hotReply(BuildContext context) {
    var reply = data.replyRows![0];
    var replyPic = reply.picArr.isNullOrEmpty
        ? ''
        : ''' <a class="feed-forward-pic" href=${reply.pic}>查看图片(${reply.picArr!.length})</a>''';
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(left: 10, top: 10, right: 10),
      child: Material(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: const BorderRadius.all(Radius.circular(12)),
        child: InkWell(
          onTap: _onViewFeed,
          onLongPress: () =>
              Get.toNamed('/copy', parameters: {'text': reply.message.orEmpty}),
          // 桌面端：右键 = 长按（见 components/secondary_tap.dart）
          onSecondaryTap: () =>
              Get.toNamed('/copy', parameters: {'text': reply.message.orEmpty}),
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: htmlText(
              """<a class="feed-link-uname" href="/u/${reply.uid}">${reply.userInfo?.username}</a>: ${reply.message}$replyPic""",
              fontSize: 14,
              onShowTotalReply: _onViewFeed,
              picArr: reply.picArr,
            ),
          ),
        ),
      ),
    );
  }

  Widget _rows(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: isFeedContent ? 16 : 10,
        top: 10,
        right: isFeedContent ? 16 : 10,
      ),
      child: Wrap(
        spacing: 5.0,
        runSpacing: 5.0,
        children: [
          if (data.targetRow != null)
            miniCardItem(
              context,
              data.targetRow!.logo!.orEmpty,
              data.targetRow!.title.orEmpty,
              data.targetRow!.url.orEmpty,
              isFeedContent ? false : true,
              false,
            ),
          if (!data.relationRows.isNullOrEmpty)
            ...data.relationRows!.map(
              (item) => miniCardItem(
                context,
                item.logo.orEmpty,
                item.title.orEmpty,
                item.url.orEmpty,
                isFeedContent ? false : true,
                false,
              ),
            ),
        ],
      ),
    );
  }

  List<Widget> _message() {
    // ★ 标题回落（只在**详情页**做）：
    //
    // 实测普通动态（`is_html_article = "0"`）给的是
    // `title = "xxx的点评"` + `message_title = ""` —— 标题只在 `title` 里。
    // 不回落的话详情页会丢掉标题（正文修好之后这一行尤其显眼）。
    //
    // 列表卡**不做**这个回落：列表里 `title` 常是别的含义，
    // 当成正文抬头显示会串味。
    final title = !data.messageTitle.isNullOrEmpty
        ? data.messageTitle.orEmpty
        : (isFeedContent ? data.title.orEmpty : '');
    return [
      if (title.isNotEmpty)
        Padding(
          padding: EdgeInsets.only(
            left: isFeedContent ? 16 : 10,
            top: 5,
            right: isFeedContent ? 16 : 10,
          ),
          child: htmlText(
            title,
            fontSize: 16,
            isBold: true,
          ),
        ),
      Padding(
        padding: EdgeInsets.only(
          left: isFeedContent ? 16 : 10,
          top: 5,
          right: isFeedContent ? 16 : 10,
        ),
        child: _buildMessageBody(),
      )
    ];
  }

  /// 正文：列表里**最多 5 行 + 「查看更多」**，详情页给全文。
  ///
  /// 对齐 Android `item_home_feed.xml`：
  /// ```xml
  /// <TextView android:id="@+id/message" android:maxLines="5"
  ///           android:textSize="16sp" android:lineSpacingMultiplier="1.2" />
  /// <TextView android:id="@+id/messageMore" android:text="查看更多"
  ///           android:layout_marginTop="6dp" android:textSize="13sp"
  ///           android:textStyle="bold" android:textColor="?attr/colorPrimary" />
  /// ```
  /// Android 那边 `messageMore` 只在**真的被截断**时才显示
  /// （`FeedViewHolder.bindMessageMore` 按排版结果判断），这里也只在超行时才加。
  ///
  /// 折叠的具体做法见 [ClampedHtml]（`HtmlWidget` 没有 `maxLines`，得自己量 + 裁）。
  Widget _buildMessageBody() {
    final content = htmlText(
      data.message.orEmpty,
      fontSize: 16,
      onClick: _onViewFeed,
    );
    // 详情页不折叠（Android 详情页也没有「查看更多」）
    if (isFeedContent) return content;
    return ClampedHtml(
      plainText: ClampedHtml.stripHtml(data.message.orEmpty),
      onMore: _onViewFeed,
      child: content,
    );
  }
}

Widget header(
  BuildContext context,
  Datum data,
  bool isFeedContent, {
  bool isHeader = false,
  bool isHistory = false,
  Function(dynamic id)? onDelete,
  Function()? onBlock,
  /// 个人主页置顶 / 取消置顶 —— `header(...)` 是**顶层函数**
  /// （不在 `FeedCard` 里），`FeedCard.onStickTop` 不会自动可见，必须显式传进来。
  Function(dynamic id, bool isStickTop)? onStickTop,
}) {
  return Row(
    children: [
      Flexible(
        flex: 1,
        child: Padding(
          padding: EdgeInsets.only(
            left: isHeader
                ? 0
                : isFeedContent
                    ? 16
                    : 10,
            top: isHeader
                ? 0
                : isFeedContent
                    ? 12
                    : 10,
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              GestureDetector(
                onTap: () => Get.toNamed('/u/${data.uid}'),
                child: clipNetworkImage(
                  data.userAvatar.orEmpty,
                  radius: 50,
                  width: 35,
                  height: 35,
                  isAvatar: true,
                ),
              ),
              const SizedBox(width: 10),
              Flexible(
                flex: 1,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      data.userInfo?.username ?? '',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 15),
                    ),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        if (isFeedContent || !data.infoHtml.isNullOrEmpty) ...[
                          Text(
                            isFeedContent
                                ? DateUtil.fromToday(data.dateline)
                                : Utils.parseHtmlString(data.infoHtml.orEmpty),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: 14,
                              color: Theme.of(context).colorScheme.outline,
                            ),
                          ),
                          const SizedBox(width: 10),
                        ],
                        if (!data.deviceTitle.isNullOrEmpty)
                          Flexible(
                            flex: 1,
                            child: IconText(
                              icon: Icons.smartphone,
                              text: Utils.parseHtmlString(
                                  data.deviceTitle.orEmpty),
                            ),
                          ),
                      ],
                    )
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
      if (!isFeedContent && data.isStickTop == 1)
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surface,
            borderRadius: const BorderRadius.all(Radius.circular(4)),
            border: Border.all(
              width: 1.25,
              color: Theme.of(context).colorScheme.primary,
            ),
          ),
          child: Text(
            '置顶',
            style: TextStyle(
              height: 1,
              fontSize: 12,
              color: Theme.of(context).colorScheme.primary,
            ),
            strutStyle: const StrutStyle(
              leading: 0,
              height: 1,
              fontSize: 12,
            ),
          ),
        ),
      if (!isFeedContent)
        IconButton(
          onPressed: () => showModalBottomSheet(
            context: context,
            useRootNavigator: true,
            isScrollControlled: true,
            builder: (context) {
              return _MorePanel(
                id: data.id,
                uid: data.uid,
                isHistory: isHistory,
                onDelete: onDelete,
                onBlock: onBlock,
                onStickTop: onStickTop,
                isStickTop: data.isStickTop == 1,
                entityType: data.entityType,
              );
            },
          ),
          icon: const Icon(Icons.keyboard_arrow_down),
          color: Theme.of(context).colorScheme.outline,
        ),
    ],
  );
}

Widget bottomInfo(
  BuildContext context,
  Datum data,
  bool isFeedContent,
  Function()? onViewFeed,
  Function()? onLike, {
  bool isFeedArticle = false,
}) {
  return Padding(
    padding: EdgeInsets.only(
      left: isFeedContent ? 16 : 10,
      top: isFeedArticle ? 0 : 10,
      right: isFeedContent ? 16 : 10,
    ),
    child: Row(
      children: [
        Expanded(
          flex: 1,
          child: Text(
            isFeedContent
                ? !data.ipLocation.isNullOrEmpty
                    ? '发布于 ${data.ipLocation}'
                    : ''
                : DateUtil.fromToday(data.dateline),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                  color: Theme.of(context).colorScheme.outline,
                ),
          ),
        ),
        if (data.replynum != null)
          LikeButton(
            value: data.replynum,
            icon: Icons.message_outlined,
            // onClick: () {
            //   // todo
            //   SmartDialog.showToast('${data.replynum}');
            // },
          ),
        if (data.likenum != null)
          LikeButton(
            value: data.likenum,
            icon: data.userAction?.like == 1
                ? Icons.thumb_up
                : Icons.thumb_up_alt_outlined,
            isLike: data.userAction?.like == 1,
            onClick: () {
              if (GlobalData().isLogin && onLike != null) {
                onLike();
              }
            },
          ),
      ],
    ),
  );
}

// `stickTop` = 个人主页置顶 / 取消置顶（Android 菜单项 `R.id.stickTop`）
enum PanelAction { copy, block, report, delete, stickTop }

class _MorePanel extends StatelessWidget {
  const _MorePanel({
    required this.id,
    required this.uid,
    required this.isHistory,
    required this.onDelete,
    required this.onBlock,
    required this.isStickTop,
    required this.entityType,
    required this.onStickTop,
  });

  final dynamic id;
  final dynamic uid;
  final bool isHistory;
  final Function(dynamic id)? onDelete;
  final Function()? onBlock;

  /// 这条动态**当前**是否已在自己主页置顶（`data.isStickTop == 1`）
  final bool isStickTop;

  /// 实体类型 —— 只有 `feed` 才允许置顶（Android 同）
  final String? entityType;

  /// 置顶 / 取消置顶的回调（`(id, 当前是否已置顶)`）
  final Function(dynamic id, bool isStickTop)? onStickTop;

  Future<dynamic> menuActionHandler(PanelAction type) async {
    switch (type) {
      case PanelAction.copy:
        Utils.copyText('https://www.coolapk.com/feed/$id');
        Get.back();
        break;
      case PanelAction.block:
        Get.back();
        GStorage.onBlock(uid);
        if (onBlock != null) {
          onBlock!();
        }
        break;
      case PanelAction.report:
        Get.back();
        Utils.report(id, ReportType.Feed);
        break;
      case PanelAction.delete:
        Get.back();
        if (onDelete != null) {
          onDelete!(id);
        }
        break;
      case PanelAction.stickTop:
        Get.back();
        // 接口调用 + **本地切标记 / 把置顶项排到列表最前** 都在
        // `CommonController.postStickTop` 里（对齐 Android `onPostStickTop`）。
        // 这里只负责把点击传上去 —— 之前直接在这儿调接口，
        // 置顶后界面毫无变化，徽标和顺序都得刷新才更新。
        onStickTop?.call(id, isStickTop);
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).padding.bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          InkWell(
            borderRadius: const BorderRadius.only(
              topLeft: Radius.circular(30),
              topRight: Radius.circular(30),
            ),
            onTap: () => Get.back(),
            child: Container(
              alignment: Alignment.center,
              height: 35,
              child: Container(
                width: 32,
                height: 3,
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.outline,
                  borderRadius: const BorderRadius.all(
                    Radius.circular(3),
                  ),
                ),
              ),
            ),
          ),
          ListTile(
            onTap: () async => await menuActionHandler(PanelAction.copy),
            minLeadingWidth: 0,
            leading: const Icon(Icons.copy_outlined, size: 19),
            title: Text('复制', style: Theme.of(context).textTheme.titleSmall),
          ),
          ListTile(
            onTap: () async => await menuActionHandler(PanelAction.block),
            minLeadingWidth: 0,
            leading: const Icon(Icons.block, size: 19),
            title: Text('加入黑名单', style: Theme.of(context).textTheme.titleSmall),
          ),
          if (Utils.isSupportWebview())
            ListTile(
              onTap: () async => await menuActionHandler(PanelAction.report),
              minLeadingWidth: 0,
              leading: const Icon(Icons.error_outline, size: 19),
              title:
                  Text('举报', style: Theme.of(context).textTheme.titleSmall),
            ),
          // 置顶 / 取消置顶：**只有自己的动态**才出现（Android 同）
          if (GlobalData().uid.isNotEmpty &&
              GlobalData().uid == uid.toString() &&
              entityType == 'feed')
            ListTile(
              onTap: () async =>
                  await menuActionHandler(PanelAction.stickTop),
              minLeadingWidth: 0,
              leading: const Icon(Icons.push_pin_outlined, size: 19),
              title: Text(
                isStickTop ? '取消置顶' : '置顶',
                style: Theme.of(context).textTheme.titleSmall,
              ),
            ),
          ListTile(
            onTap: () async => await menuActionHandler(PanelAction.delete),
              minLeadingWidth: 0,
              leading: const Icon(Icons.delete_outline, size: 19),
              title:
                  Text('删除', style: Theme.of(context).textTheme.titleSmall),
            ),
        ],
      ),
    );
  }
}

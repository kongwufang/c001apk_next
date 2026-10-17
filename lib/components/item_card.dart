import 'package:flutter/material.dart';

import '../components/cards/app_card.dart';
import '../components/cards/carousel_card.dart';
import '../components/cards/collection_card.dart';
import '../components/cards/feed_card.dart';
import '../components/cards/feed_cover_card.dart';
import '../components/cards/feed_reply_card.dart';
import '../components/cards/generic_card.dart';
import '../components/cards/icon_link_grid_card.dart';
import '../components/cards/icon_mini_grid_card.dart';
import '../components/cards/icon_mini_scroll_card.dart';
import '../components/cards/icon_scroll_card.dart';
import '../components/cards/image_square_scroll_card.dart';
import '../components/cards/image_text_scroll_card.dart';
import '../components/cards/like_card.dart';
import '../components/cards/message_card.dart';
import '../components/cards/notification_card.dart';
import '../components/cards/product_config_list_card.dart';
import '../components/cards/rating_card.dart';
import '../components/cards/text_card.dart';
import '../components/cards/title_card.dart';
import '../logic/model/feed/datum.dart';
import '../utils/extensions.dart';

Widget itemCard(
  Datum data, {
  bool isFeedContent = false,
  bool isHomeCard = false,
  bool isReply2Reply = false,
  bool isTopReply = false,
  /// `feedCover` 是否**按完整动态卡排**。
  ///
  /// 对应 Android `AppAdapter.feedCoverAsFeed`，默认 false。原注释：
  /// > 个人主页「动态」「图文」tab、我的动态 / 我的赞 / 我的回复 / 我的常去这些列表
  /// > 会把文章（entityTemplate=feedCover）和普通动态混在同一个列表里下发
  /// > （htmlFeedList 实测 17 条里 6 条 feedCover、11 条 feed），照模板分派会在一页里
  /// > 混出「紧凑封面卡 / 完整动态卡」两种排法，而要求是整页长一样。
  /// > 首页 / 教程页 / 产品页晒单 / 数码首页那些发现流仍走紧凑卡（viewType 27）。
  bool feedCoverAsFeed = false,
  dynamic uid,
  Function(dynamic uid)? onBlock,
  Function(dynamic id, dynamic uname, dynamic fid)? onReply,
  Function(dynamic id, dynamic fid, {bool isFeed, bool isReply, bool isNoti})?
      onDelete,
  Function(dynamic id)? onDeleteNoti,
  Function(dynamic id, dynamic like, {bool isFeed, bool isReply})? onLike,
  Function(dynamic id)? onPM,
}) {
  // ★ `feedCover` 在「整页统一」的列表里要走**完整动态卡**，
  //   所以把这一坨抽成局部函数给两个 case 共用。
  //   （Dart 的 `break` 是**退出整个 switch**，不是 fall-through，
  //     不能像 Java 那样靠 break 落到下一个 case。）
  Widget buildFeedCard() => FeedCard(
        data: data,
        isFeedContent: isFeedContent,
        onBlock: onBlock,
        onDelete: (id) {
          if (onDelete != null) {
            onDelete(id, null, isFeed: true);
          }
        },
        onLike: (id, like) {
          if (onLike != null) {
            onLike(id, like, isFeed: true);
          }
        },
      );

  // ★★ 先按 **`entityTemplate`** 分派 `feedCover` —— 这是 Android 的做法。
  //
  // 实测（`_rev/win_ref/probe_feedcover3.py` 打首页 indexV8）：
  //   feedCover 实体的结构是 `entityType='feed'` + **`entityTemplate='feedCover'`**
  //   —— **`entityType` 是 `feed`，不是 `feedCover`**！
  // 所以按 `entityType` 分派永远不会命中（我第一版就是这么写错的）。
  //
  // Android `AppAdapter.getItemViewType` 也正是按它分派的：
  //   `!feedCoverAsFeed && currentList[position].entityTemplate == "feedCover" -> 27`
  if (!feedCoverAsFeed && data.entityTemplate == 'feedCover') {
    return FeedCoverCard(
      data: data,
      onLike: onLike != null ? () => onLike(data.id, true, isFeed: true) : null,
    );
  }

  switch (data.entityType) {
    case 'card':
      switch (data.entityTemplate) {
        case 'imageCarouselCard_1':
          return CarouselCard(dataList: data.entities!);
        case 'iconLinkGridCard':
          return IconLinkGridCard(dataList: data.entities!);
        case 'iconMiniScrollCard':
          return IconMiniScrollCard(dataList: data.entities!);
        case 'iconMiniGridCard':
          return IconMiniGridCard(data: data);
        case 'imageSquareScrollCard':
          return ImageSquareScrollCard(dataList: data.entities!);
        case 'titleCard':
          return TitleCard(title: data.title.orEmpty, url: data.url.orEmpty);
        case 'iconScrollCard':
          return IconScrollCard(data: data);
        case 'imageTextScrollCard':
          return ImageTextScrollCard(data: data);
        case 'noMoreDataCard':
          return TextCard(text: data.title.orEmpty, isEndCard: true);
        case 'messageCard':
          return TextCard(text: data.description.orEmpty, isMessage: true);
        case 'refreshCard':
          return TextCard(text: data.title.orEmpty, isRefreshCard: true);
        // ★ 产品页「参数」tab 的版本配置卡（Android viewType 15）。
        //
        //   ⚠️ 注意分派层级：这类条目的 **`entityType` 是 `'card'`**，
        //   `productConfigList` 是 **`entityTemplate`** ——
        //   所以 case 要放在这个**内层按 entityTemplate 的 switch** 里，
        //   放到外层 `switch (entityType)` 永远匹配不到（会掉进通用卡片，
        //   表现就是「只有『版本配置』标题、没有下面那些行」）。
        //
        //   另外服务端下发的这张卡 entities **恒为空**，
        //   内容靠 `TopicContentController` 从 `product/detail` 的 configRows 注入。
        case 'productConfigList':
          return ProductConfigListCard(data: data);
        default:
          // ★ 未单独适配的卡片模板 → 走**通用卡片**（规则表驱动），
          //   而不是什么都不渲染。对应 Android `AppAdapter` 的
          //   `else -> 26`（GenericCardViewHolder）。
          //   用户 app 的 release-notes：「一次接住服务端下发的全部卡片模板，
          //   没单独适配的模板也有兜底卡片，不再出现空白或错版」。
          return genericCard(data);
      }
    case 'feed':
      return buildFeedCard();
    case 'feed_reply':
      if (data.likeUserInfo != null) {
        return LikeCard(data: data);
      } else {
        return FeedReplyCard(
          data: data,
          isReply2Reply: isReply2Reply,
          isTopReply: isTopReply,
          uid: uid,
          onBlock: onBlock != null ? (uid, id) => onBlock(uid) : null,
          onReply: onReply,
          onDelete: (id, fid) {
            if (onDelete != null) {
              onDelete(id, fid, isReply: true);
            }
          },
          onLike: (id, like) {
            if (onLike != null) {
              onLike(id, like, isReply: true);
            }
          },
        );
      }
    case 'apk':
      return AppCard(
        appCardType: AppCardType.APP,
        data: data,
        isHomeCard: isHomeCard,
      );
    case 'product':
      return AppCard(
        appCardType: AppCardType.PRODUCT,
        data: data,
        isHomeCard: isHomeCard,
      );
    case 'user':
      return AppCard(
        appCardType: AppCardType.USER,
        data: data,
        isHomeCard: isHomeCard,
      );
    case 'topic':
      return AppCard(
        appCardType: AppCardType.TOPIC,
        data: data,
        isHomeCard: isHomeCard,
      );
    case 'contacts':
      return AppCard(
        appCardType: AppCardType.CONTACTS,
        data: data,
        isHomeCard: isHomeCard,
      );
    case 'recentHistory':
      return AppCard(
        appCardType: AppCardType.RECENT,
        data: data,
        isHomeCard: isHomeCard,
      );
    case 'notification':
      return NotificationCard(
          data: data, onBlock: onBlock, onDelete: onDeleteNoti);
    case 'message':
      return MessageCard(data: data, onPM: onPM);
    case 'collection':
      return CollectionCard(data: data);
    // 个人主页「点评」tab 的实体（Android `AppAdapter` 的 viewType 21）
    case 'nodeRating':
      return RatingCard(data: data);
  }
  // ★ 顶层 `entityType` 也没匹配到 → 同样交给通用卡片。
  //
  // 原来这里是 `ListTile(title: entityType, subtitle: entityTemplate)`，
  // **把内部类型名直接显示给用户**（如「pear_goods / pear_goods」）。
  // Android 端明确撤掉了这种排障提示：
  // 「这种技术字样不该出现在界面上」（见 GenericCardRenderer.render 的注释）。
  return genericCard(data);
}

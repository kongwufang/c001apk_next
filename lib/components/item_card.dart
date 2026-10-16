import 'package:flutter/material.dart';

import '../components/cards/app_card.dart';
import '../components/cards/carousel_card.dart';
import '../components/cards/collection_card.dart';
import '../components/cards/feed_card.dart';
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
  dynamic uid,
  Function(dynamic uid)? onBlock,
  Function(dynamic id, dynamic uname, dynamic fid)? onReply,
  Function(dynamic id, dynamic fid, {bool isFeed, bool isReply, bool isNoti})?
      onDelete,
  Function(dynamic id)? onDeleteNoti,
  Function(dynamic id, dynamic like, {bool isFeed, bool isReply})? onLike,
  Function(dynamic id)? onPM,
}) {
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
        default:
          // ★ 未单独适配的卡片模板 → 走**通用卡片**（规则表驱动），
          //   而不是什么都不渲染。对应 Android `AppAdapter` 的
          //   `else -> 26`（GenericCardViewHolder）。
          //   用户 app 的 release-notes：「一次接住服务端下发的全部卡片模板，
          //   没单独适配的模板也有兜底卡片，不再出现空白或错版」。
          return genericCard(data);
      }
    case 'feed':
      return FeedCard(
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
  }
  // ★ 顶层 `entityType` 也没匹配到 → 同样交给通用卡片。
  //
  // 原来这里是 `ListTile(title: entityType, subtitle: entityTemplate)`，
  // **把内部类型名直接显示给用户**（如「pear_goods / pear_goods」）。
  // Android 端明确撤掉了这种排障提示：
  // 「这种技术字样不该出现在界面上」（见 GenericCardRenderer.render 的注释）。
  return genericCard(data);
}

import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../logic/model/feed/datum.dart';
import '../../utils/date_util.dart';
import '../../utils/extensions.dart';
import '../../utils/global_data.dart';
import '../../utils/utils.dart';
import '../like_button.dart';
import '../network_image.dart';

/// `feedCover`（紧凑封面卡）。
///
/// 对应 Android `res/layout/item_home_feed_cover.xml`（viewType 27）。
///
/// ## ★ 它和完整动态卡**不是一套排法**
/// 原 XML 的注释写得很清楚：
/// > 官方布局跟完整动态卡不是一个排法：**没有头像 / 用户名区**，
/// > 内容就是「标题（16sp 加粗、最多 2 行）+ 正文摘要（14sp、最多 3 行）」在左，
/// > 「106dp 正方形封面图」在右，底下挂评论行与互动栏。
///
/// ## 规格（照抄 XML）
/// | 元素 | 规格 |
/// |---|---|
/// | 卡片 | margin 5dp（上/左/右）、elevation 0、无描边 |
/// | 内边距 | 10dp |
/// | 封面图 | **106×106dp**、`centerCrop`、**右上角**、圆角 8dp |
/// | 标题 | **16sp 加粗**、最多 **2 行**、右间距 10dp、`colorOnSurface`、空则**整块隐藏** |
/// | 正文 | **14sp**、最多 **3 行**、上间距 6dp、右间距 10dp、`colorOnSurfaceVariant` |
/// | 时间 | **12sp**、灰、上间距 10dp |
/// | 回复 / 点赞 | 12sp，与时间**基线对齐**，在右侧 |
///
/// ## 几个要点
/// 1. **封面图取数：`pic` 优先，没有就用 `picArr` 第一张**
///    （Android `AppAdapter.FeedCoverViewHolder` 就是这个顺序）。
/// 2. **缩略图圆角走项目统一的 8dp** —— XML 注释说官方那张是直角 CardView，
///    本项目其它缩略图统一 8dp，这里跟项目一致。
/// 3. **底部互动沿用本项目动态卡的写法**（XML 注释原话：
///    「底部互动沿用本项目动态卡的写法（时间 / 回复 / 点赞）」），
///    所以复用 [LikeButton]，不另造一套。
/// 4. 点击进详情、**长按复制正文**（对应 XML 的 `onLongClick -> onCopyText(message)`）。
///
/// ## 什么时候**不用**它
/// 个人主页的「动态」「图文」tab、我的动态 / 我的赞 / 我的回复 / 我的常去
/// 这些列表会把文章（`entityTemplate=feedCover`）和普通动态混在同一个列表里下发，
/// 照模板分派会在一页里混出两种排法。Android 用 `feedCoverAsFeed` 开关让整页长一样
/// （见 `AppAdapter` 的注释），[itemCard] 的参数同名同义。
class FeedCoverCard extends StatelessWidget {
  const FeedCoverCard({
    super.key,
    required this.data,
    this.onLike,
    this.onOpen,
  });

  final Datum data;
  final VoidCallback? onLike;
  final VoidCallback? onOpen;

  /// 封面图地址：`pic` 优先，没有就用 `picArr` 第一张
  /// （对应 Android `AppAdapter.FeedCoverViewHolder`）。
  String get _cover {
    if (!data.pic.isNullOrEmpty) return data.pic!;
    final arr = data.picArr;
    if (arr != null && arr.isNotEmpty) return arr.first;
    return '';
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final title = data.title.orEmpty;

    return Material(
      color: colorScheme.surfaceContainer,
      borderRadius: BorderRadius.circular(12),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        // 与 FeedCard 一致：点进详情页
        onTap: onOpen ?? () => Get.toNamed('/feed/${data.id}'),
        // 桌面端：右键 = 长按（见 components/secondary_tap.dart）
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // 标题：空则整块不占位（对应 XML 的 visibility=GONE）
                        if (title.isNotEmpty)
                          Padding(
                            padding: const EdgeInsets.only(right: 10),
                            child: Text(
                              title,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        if (!data.message.isNullOrEmpty)
                          Padding(
                            padding: EdgeInsets.only(
                              top: title.isNotEmpty ? 6 : 0,
                              right: 10,
                            ),
                            child: Text(
                              data.message!,
                              maxLines: 3,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontSize: 14,
                                color: colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                  if (_cover.isNotEmpty)
                    ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: SizedBox(
                        width: 106,
                        height: 106,
                        child: networkImage(
                          _cover,
                          fit: BoxFit.cover,
                          borderRadius: null,
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 10),
              // 底部互动：时间 / 回复 / 点赞（沿用本项目动态卡的写法）
              Row(
                children: [
                  Expanded(
                    child: Text(
                      DateUtil.fromToday(data.dateline),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 12,
                        color: colorScheme.outline,
                      ),
                    ),
                  ),
                  if (data.replynum != null)
                    LikeButton(
                      value: data.replynum,
                      icon: Icons.message_outlined,
                    ),
                  if (data.likenum != null)
                    LikeButton(
                      value: data.likenum,
                      icon: data.userAction?.like == 1
                          ? Icons.thumb_up
                          : Icons.thumb_up_alt_outlined,
                      isLike: data.userAction?.like == 1,
                      onClick: () {
                        if (GlobalData().isLogin && onLike != null) onLike!();
                      },
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

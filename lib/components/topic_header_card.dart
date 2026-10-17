import 'package:flutter/material.dart';

import 'network_image.dart';

/// 话题 / 机型页的**头部卡片**。
///
/// 对应 Android `res/layout/item_topic_header.xml`
/// + `TopicFragment.bindHeader`。
///
/// ## 布局（照抄 XML）
/// 外层 padding **15 / actionBarSize / 15 / 10dp**
/// （`paddingTop="?attr/actionBarSize"` 是给折叠工具栏留的位置；
/// 本工程不做折叠，见下方「差异」，所以上边距改小）。
///
/// | 元素 | 规格 |
/// |---|---|
/// | logo | **64×64dp**，左边 |
/// | 标题 | **18sp**，与 logo 间距 **12dp** |
/// | 统计 | **12sp**，上间距 **4dp** = `热点 · N讨论`（各自为空则跳过，` · ` 连） |
/// | 简介 | **12sp**，上间距 **4dp**（空则整行不显示） |
/// | 关注行 | 上间距 **6dp**，横向 |
/// | 关注者头像 | **28dp** 圆形，最多 **3 个**，后一个左边距 **-8dp**（叠压） |
/// | 「N人关注」 | **12sp**，与头像间距 **6dp** |
/// | 关注按钮 | 高 **32dp**、左右内边距 **14dp**、左间距 **8dp**、**13sp**；已关注用次要色，未关注用 `primary` |
///
/// ## ★ 与 Android 的差异（如实说明）
/// Android 是 `AppBarLayout + CollapsingToolbarLayout`：
/// 列表往上滑时**头部卡片折叠/淡出**，工具栏钉住。
///
/// 本工程的话题页是「AppBar + TabBar + TabBarView」的结构，
/// 要做折叠得改成 `NestedScrollView` + `SliverOverlapAbsorber/Injector`，
/// 而每个 tab 的内容是 `commonBody(...)` 自带的 `CustomScrollView` ——
/// 需要动公共列表组件，风险大。
///
/// 所以这里**先做成不折叠**（固定在顶部），把**内容**先对齐；
/// 折叠动效记为后续项。
class TopicHeaderCard extends StatelessWidget {
  const TopicHeaderCard({
    super.key,
    required this.logo,
    required this.title,
    required this.hotNum,
    required this.commentNum,
    required this.intro,
    required this.followNum,
    required this.avatars,
    required this.isFollow,
    required this.isLogin,
    this.onFollow,
  });

  final String logo;
  final String title;

  /// `hot_num_txt`
  final String hotNum;

  /// `commentnum_txt`
  final String commentNum;

  /// `intro`
  final String intro;

  /// `follownum_txt`
  final String followNum;

  /// `recent_follow_list[].userAvatar`（最多取 3 个）
  final List<String> avatars;

  final bool isFollow;
  final bool isLogin;
  final VoidCallback? onFollow;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    // 统计行：`hotNum` 与 `"N讨论"`，各自为空则跳过，` · ` 连
    final stats = <String>[
      if (hotNum.isNotEmpty) hotNum,
      if (commentNum.isNotEmpty) '$commentNum讨论',
    ];

    final shownAvatars = avatars.take(3).toList();
    final followText = followNum.isNotEmpty ? '$followNum人关注' : '';

    return Padding(
      padding: const EdgeInsets.fromLTRB(15, 8, 15, 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // logo 64×64
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: SizedBox(
                  width: 64,
                  height: 64,
                  child: logo.isEmpty
                      ? ColoredBox(color: colorScheme.surfaceContainer)
                      : networkImage(
                          logo,
                          width: 64,
                          height: 64,
                          fit: BoxFit.cover,
                          borderRadius: null,
                        ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          fontSize: 18, fontWeight: FontWeight.bold),
                    ),
                    if (stats.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        stats.join(' · '),
                        style: TextStyle(
                          fontSize: 12,
                          color: colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                    if (intro.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        intro,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 12,
                          color: colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
          // 关注行
          if (shownAvatars.isNotEmpty || followText.isNotEmpty || isLogin)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Row(
                children: [
                  // 头像叠压：28dp 圆形，后一个左移 8dp（即露出 20dp），最多 3 个
                  if (shownAvatars.isNotEmpty)
                    SizedBox(
                      width: 28.0 + (shownAvatars.length - 1) * 20.0,
                      height: 28,
                      child: Stack(
                        children: [
                          for (var i = 0; i < shownAvatars.length; i++)
                            Positioned(
                              left: i * 20.0,
                              child: ClipOval(
                                child: SizedBox(
                                  width: 28,
                                  height: 28,
                                  child: networkImage(
                                    shownAvatars[i],
                                    width: 28,
                                    height: 28,
                                    isAvatar: true,
                                    fit: BoxFit.cover,
                                    borderRadius: null,
                                  ),
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                  if (followText.isNotEmpty)
                    Padding(
                      padding:
                          EdgeInsets.only(left: shownAvatars.isEmpty ? 0 : 6),
                      child: Text(
                        followText,
                        style: TextStyle(
                          fontSize: 12,
                          color: colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                  const Spacer(),
                  if (isLogin)
                    SizedBox(
                      height: 32,
                      child: FilledButton(
                        onPressed: onFollow,
                        style: FilledButton.styleFrom(
                          padding:
                              const EdgeInsets.symmetric(horizontal: 14),
                          // 已关注用次要色，未关注用 primary
                          backgroundColor: isFollow
                              ? colorScheme.secondaryContainer
                              : colorScheme.primary,
                          foregroundColor: isFollow
                              ? colorScheme.onSecondaryContainer
                              : colorScheme.onPrimary,
                          textStyle: const TextStyle(fontSize: 13),
                        ),
                        child: Text(isFollow ? '已关注' : '关注'),
                      ),
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

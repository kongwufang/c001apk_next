import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../components/html_text.dart';
import '../../components/network_image.dart';
import '../../logic/model/feed/datum.dart';
import '../../utils/date_util.dart';
import '../../utils/extensions.dart';
import '../../utils/utils.dart';

/// 「我的赞」列表里的一行 —— **紧凑的「来源」卡**。
///
/// ## 为什么不用完整动态大卡
/// `/v6/user/likeList`（我的赞）返回的是**我赞过的动态**，字段里
/// 只有 `likeTime`（我点赞的时间）和 `userInfo`（**动态作者**）——
/// **没有 `likeUserInfo`**（实测 133 个字段里没有 ✗）。
///
/// 而 `/v6/notification/feedLikeList`（我收到的赞）才有
/// `likeUserInfo` / `likeUid` / `likeUsername` / `likeAvatar`。
///
/// ⇒ 用户要求：我的赞要像 app 那样显示**点赞来源**（是谁的动态 + 我什么时候赞的）
///   而不是把整条动态铺成大卡。
///
/// ## 和 [LikeCard] 的关系
/// 结构照抄 `LikeCard`（我收到的赞那种）：
/// ```
/// [头像] 名字                          12-18
/// 你赞了这条动态
/// ┌ [图] @作者 …摘要… ┐                ← 点进动态详情
/// ```
/// 区别只在第一行的说明文字：这里是「你赞了这条动态」而不是「XX 赞了你的XX」。
class LikeSourceCard extends StatelessWidget {
  const LikeSourceCard({super.key, required this.data});

  final Datum data;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    // 作者头像：优先 userInfo，退回 userAvatar
    final avatar = data.userInfo?.userAvatar ?? data.userAvatar ?? '';
    final uname = data.userInfo?.username ?? data.username ?? '';

    return Ink(
      decoration: BoxDecoration(
        color: cs.surfaceContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      padding: const EdgeInsets.all(10),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              GestureDetector(
                onTap: () {
                  final uid = data.userInfo?.uid ?? data.uid;
                  if (uid != null) Get.toNamed('/u/$uid');
                },
                child: clipNetworkImage(
                  avatar,
                  isAvatar: true,
                  width: 30,
                  height: 30,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(flex: 1, child: Text(uname)),
              Text(
                DateUtil.fromToday(data.likeTime),
                style: TextStyle(fontSize: 13, color: cs.outline),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            '你赞了这条动态',
            style: TextStyle(fontSize: 13, color: cs.onSurfaceVariant),
          ),
          const SizedBox(height: 10),
          // 引用卡：点进去看原动态（对齐 LikeCard 的引用样式）
          InkWell(
            onTap: () {
              final url = data.url ?? '';
              if (url.isNotEmpty) {
                Utils.onOpenLink(url);
              } else if (data.id != null) {
                Get.toNamed('/feed/${data.id}');
              }
            },
            borderRadius: BorderRadius.circular(12),
            child: Ink(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: cs.surface,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                children: [
                  if (!data.pic.isNullOrEmpty) ...[
                    clipNetworkImage(
                      data.pic!,
                      radius: 8,
                      width: 40,
                      height: 40,
                      fit: BoxFit.cover,
                    ),
                    const SizedBox(width: 10),
                  ],
                  Expanded(
                    flex: 1,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Text(
                              '@$uname',
                              style: const TextStyle(fontSize: 13),
                            ),
                            if (!data.ttitle.isNullOrEmpty) ...[
                              const SizedBox(width: 6),
                              Flexible(
                                child: Text(
                                  '#${data.ttitle}',
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: cs.outline,
                                  ),
                                ),
                              ),
                            ],
                          ],
                        ),
                        const SizedBox(height: 5),
                        htmlText(data.message ?? '', fontSize: 13),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

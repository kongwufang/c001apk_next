import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../logic/model/feed/datum.dart';
import '../../utils/date_util.dart';
import '../../utils/extensions.dart';
import '../network_image.dart';

/// 点评卡（个人主页「点评」tab 的 `nodeRating` 实体）。
///
/// 对应 Android `res/layout/item_user_node_rating.xml` + `util/UserCardUtils.kt`。
///
/// ## 布局（逐项照抄 XML）
/// | 元素 | 规格 |
/// |---|---|
/// | 卡片 | margin 12/6/12/6dp、圆角 12dp、elevation 0、底色 `surface` |
/// | 内边距 | 12dp |
/// | 头像 | **24×24dp** 圆形（左上） |
/// | 认证角标 | **10×10dp**，压头像右下角，无认证不显示 |
/// | 用户名 | **13sp 加粗**，与头像间距 6dp，垂直居中 |
/// | 时间 | **11sp** 次要色，右上，与头像垂直居中 |
/// | 产品图 | **44×44dp** 圆角，上间距 10dp |
/// | 产品名 | **15sp 加粗**、单行省略，与产品图间距 10dp |
/// | 星级 | **13sp `#FFB300`**，产品名下方 4dp |
/// | 得分 | **11sp** 次要色，星级右侧 6dp |
/// | 平均分 | **11sp** 次要色，右侧（与产品图底部对齐）= 「平均 X」 |
/// | 优点 / 缺点 / 总结 | 上间距 10 / 4 / 4dp，**空则整块不占位** |
///
/// ## 文案格式（`UserCardUtils`）
/// - 星级：`★`×star + `☆`×(5-star)，star 夹到 0~5
/// - 得分：`"N 分"`，`<=0` 不显示
/// - 平均：`"平均 X"`，X 为空不显示
/// - 点评：`"标签：内容"`，内容为空则不显示整行
///
/// ## 数据来源
/// Flutter 的 `Datum` **没有**这些字段（`star` / `rating_score_old` /
/// `comment_*` / `target_info`），只能从 `data.raw` 取 —— 与 `extraData`
/// / `apkRows` / `entityTemplate` 是同一套做法。
class RatingCard extends StatelessWidget {
  const RatingCard({super.key, required this.data});

  final Datum data;

  // ── raw 取值 ──

  Object? _raw(String key) => data.raw?[key];

  int? get _star => (_raw('star') as num?)?.toInt();

  int? get _score => (_raw('rating_score_old') as num?)?.toInt();

  String? _comment(String key) {
    final v = _raw(key);
    return v is String && v.isNotEmpty ? v : null;
  }

  Map<String, dynamic>? get _target {
    final v = _raw('target_info');
    if (v is Map) return Map<String, dynamic>.from(v);
    return null;
  }

  /// `UserCardUtils.starText`：★×star + ☆×(5-star)
  String get _starText {
    // ★ 注意 `.toInt()`：`int.clamp()` 返回的是 **`num`**，
    //   而 `String * int` 只认 `int`，少了这一步会编译不过。
    final s = (_star ?? 0).clamp(0, 5).toInt();
    return '\u2605' * s + '\u2606' * (5 - s);
  }

  /// `UserCardUtils.scoreText`：`"N 分"`，`<=0` 给空
  String get _scoreText {
    final s = _score ?? 0;
    return s <= 0 ? '' : '$s 分';
  }

  /// `UserCardUtils.prefixText('平均 ', star_average_score)`
  String get _averageText {
    final v = _target?['star_average_score']?.toString();
    return (v == null || v.isEmpty) ? '' : '平均 $v';
  }

  /// `UserCardUtils.commentText(label, text)`：`"标签：内容"`，空给空
  String _commentText(String label, String? text) =>
      (text == null || text.isEmpty) ? '' : '$label：$text';

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    // ★ 注意这个坑：`a?.b.c` 里的 `?.` 会把**整条链**的静态类型变成可空 ——
    //   所以 `data.userInfo?.userAvatar.orEmpty` 的类型是 `String?`（不是 `String`），
    //   因为 `userInfo` 为 null 时整条链就是 null，`orEmpty` 根本不会被调用。
    //   要么 `?? ''`，要么写成 `(data.userInfo?.userAvatar).orEmpty`。
    final avatar = data.userInfo?.userAvatar.orEmpty ?? '';
    final username = data.userInfo?.username.orEmpty ?? '';
    final target = _target;
    final good = _commentText('优点', _comment('comment_good'));
    final bad = _commentText('缺点', _comment('comment_bad'));
    final general = _commentText('总结', _comment('comment_general'));

    return Material(
      color: colorScheme.surface,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── 作者行：头像 24 + 用户名 + 时间 ──
            Row(
              children: [
                SizedBox(
                  width: 24,
                  height: 24,
                  child: avatar.isEmpty
                      ? ColoredBox(color: colorScheme.surfaceContainer)
                      : networkImage(
                          avatar,
                          width: 24,
                          height: 24,
                          isAvatar: true,
                          borderRadius: null,
                        ),
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    username,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        fontSize: 13, fontWeight: FontWeight.bold),
                  ),
                ),
                Text(
                  DateUtil.fromToday(data.dateline),
                  style: TextStyle(fontSize: 11, color: colorScheme.outline),
                ),
              ],
            ),
            const SizedBox(height: 10),

            // ── 产品行：44 图 + 标题 / 星级 / 得分 + 平均分 ──
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: SizedBox(
                    width: 44,
                    height: 44,
                    child: (target?['logo']?.toString().orEmpty ?? '').isEmpty
                        ? ColoredBox(color: colorScheme.surfaceContainer)
                        : networkImage(
                            target!['logo'].toString(),
                            fit: BoxFit.cover,
                            borderRadius: null,
                          ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        target?['title']?.toString().orEmpty ?? '',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                            fontSize: 15, fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Text(
                            _starText,
                            style: const TextStyle(
                                fontSize: 13, color: Color(0xFFFFB300)),
                          ),
                          if (_scoreText.isNotEmpty) ...[
                            const SizedBox(width: 6),
                            Text(
                              _scoreText,
                              style: TextStyle(
                                  fontSize: 11, color: colorScheme.outline),
                            ),
                          ],
                        ],
                      ),
                    ],
                  ),
                ),
                if (_averageText.isNotEmpty)
                  Text(
                    _averageText,
                    style: TextStyle(fontSize: 11, color: colorScheme.outline),
                  ),
              ],
            ),

            // ── 点评正文（三段各自可缺） ──
            if (good.isNotEmpty) ...[
              const SizedBox(height: 10),
              _commentLine(good, colorScheme),
            ],
            if (bad.isNotEmpty) ...[
              const SizedBox(height: 4),
              _commentLine(bad, colorScheme),
            ],
            if (general.isNotEmpty) ...[
              const SizedBox(height: 4),
              _commentLine(general, colorScheme),
            ],
          ],
        ),
      ),
    );
  }

  Widget _commentLine(String text, ColorScheme colorScheme) => Text(
        text,
        style: TextStyle(fontSize: 13, color: colorScheme.onSurfaceVariant),
      );
}

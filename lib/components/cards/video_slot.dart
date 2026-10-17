import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';

import '../../logic/model/feed/feed_video.dart';
import '../../logic/network/network_repo.dart';
import '../../utils/utils.dart';
import '../network_image.dart';

/// 动态卡里的**视频槽**。
///
/// 对应 Android `view/FeedVideoView.kt`（362 行）+ `item_home_feed.xml` 的 `videoPart`。
///
/// ## 为什么必须有它
/// Android 的实测口径写得很明确：
/// > **`pic` / `picArr` 实测全为空**，封面只在 `media_info.cover` 里 —— 所以九图槽位对
/// > 视频动态**不出图**，必须由视频槽自己出封面。
///
/// 也就是说：不做这个槽位，**视频动态在信息流里会变成纯文字卡**（一条图都没有）。
///
/// ## 布局（照抄 `FeedVideoView` 的 init 里那几个 addView）
/// | 元素 | 规格 |
/// |---|---|
/// | 容器 | 上间距 **10dp**（`item_home_feed.xml` 的 `layout_marginTop`） |
/// | 封面 | 铺满、`centerCrop`，底底色 `surfaceVariant` |
/// | 播放图标 | **48×48dp**，居中 |
/// | 时长角标 | 右下角，**右边距/下边距 10dp**，白字 **12sp**、内边距 **6/2dp** |
///
/// ## ★ 与 Android 的差异（如实说明）
/// Android 用 ExoPlayer **内嵌播放**（还要处理 B 站 DASH 的音视频分离 + `Referer` 防盗链）。
/// 本工程**没有引入播放器**（`media_kit` 之类会给 Windows 加 ~30 MB 原生库），
/// 所以视频槽**只做展示**，点击**用系统浏览器打开**：
/// - 有直链 → 直接开；
/// - 没直链但有 `requestParams`（微博 / B 站）→ 先打 `/v6/player/getUrl` 解析，再开；
/// - 解析不出来 → 不拦点击，让卡片自己进详情页
///   （Android 同此：`playFailed` / `resolveFailed` 时把点击让回卡片）。
class VideoSlot extends StatefulWidget {
  const VideoSlot({super.key, required this.video});

  final FeedVideo video;

  @override
  State<VideoSlot> createState() => _VideoSlotState();
}

class _VideoSlotState extends State<VideoSlot> {
  late FeedVideo _video = widget.video;

  /// 解析中（转圈）
  bool _resolving = false;

  /// 解析失败过 / 播放失败过：不再抢点击，让回卡片
  bool _failed = false;

  Future<void> _onTap() async {
    if (_resolving) return;

    // ① 已有直链 → 直接开
    if (_video.url.isNotEmpty) {
      // `Utils.launchURL` 是 `void`（内部自己 catch），不能 await
      Utils.launchURL(_video.url);
      return;
    }

    // ② 没直链也没解析参数 → 不拦，让卡片进详情
    if (!_video.resolvable) {
      _fallback();
      return;
    }

    // ③ 打解析接口
    setState(() => _resolving = true);
    try {
      final res = await NetworkRepo.postVideoUrl(params: _video.requestParams);
      final body = res.data;
      Map<String, dynamic>? data;
      if (body is Map && body['data'] is Map) {
        data = Map<String, dynamic>.from(body['data'] as Map);
      }
      final resolved = _video.withResolved(data);
      if (!mounted) return;
      setState(() {
        _video = resolved;
        _resolving = false;
      });
      if (resolved.url.isEmpty) {
        _fallback();
      } else {
        Utils.launchURL(resolved.url);
      }
    } catch (_) {
      if (!mounted) return;
      setState(() => _resolving = false);
      _fallback();
    }
  }

  /// 解析/播放失败：标记一下，把点击让回卡片（Android 同此做法）
  void _fallback() {
    if (!mounted) return;
    setState(() => _failed = true);
    SmartDialog.showToast('这个视频需要在酷安 App 里播放');
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Padding(
      // item_home_feed.xml：`videoPart` 的 `layout_marginTop="10dp"`
      padding: const EdgeInsets.only(top: 10),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: AspectRatio(
          // 服务端多数不给宽高，缺省 16:9；给了就夹在 1:2 ~ 2.4:1
          aspectRatio: _video.aspectRatio,
          child: GestureDetector(
            // `_failed` 时返回 false 语义上等于「不处理」——
            // 但 GestureDetector 没有「穿透」能力，所以这里仍由卡片接管
            //（卡片自己的 InkWell 在更外层，tap 会同时冒泡上去）
            onTap: _failed ? null : _onTap,
            child: Stack(
              fit: StackFit.expand,
              children: [
                // 封面底（对应 `cover` 的 `colorSurfaceVariant` 背景）
                ColoredBox(color: colorScheme.surfaceVariant),
                if (_video.poster.isNotEmpty)
                  networkImage(
                    _video.poster,
                    fit: BoxFit.cover,
                    borderRadius: null,
                  ),
                // 播放图标 48×48dp 居中（或解析中转圈）
                Center(
                  child: _resolving
                      ? SizedBox(
                          width: 36,
                          height: 36,
                          child: CircularProgressIndicator(
                            strokeWidth: 3,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(
                          Icons.play_circle_fill,
                          size: 48,
                          color: Colors.white,
                        ),
                ),
                // 时长角标：右下角 10dp，白字 12sp，内边距 6/2dp
                if (_video.duration.isNotEmpty)
                  Positioned(
                    right: 10,
                    bottom: 10,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        // Android 用 `bg_video_duration` drawable（半透明黑 + 小圆角）
                        color: Colors.black54,
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        _video.duration,
                        style: const TextStyle(
                          fontSize: 12,
                          color: Colors.white,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

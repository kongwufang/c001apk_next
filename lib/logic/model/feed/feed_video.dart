import 'dart:convert';

/// 动态里的视频信息（封面 / 时长 / 播放地址）。
///
/// 忠实移植 Android `util/FeedVideo.kt`（258 行 + 19 条真实样本的实测口径）。
///
/// ## 为什么要「别名链」这么麻烦
/// 视频这块同一份数据有好几个来源（顶层 `media_*`、`video` 对象、
/// `media_info` 里的 **JSON 串**），而且不同 provider 下发的位置不一样。
/// 所以不往 `Datum` 里堆字段，一律从 `raw` 按别名链取。
///
/// ## 实测口径（来自 Android 注释里的 19 条样本）
/// - **类型判定**：`media_type = 2`（数字）或 `feedType = "video"` /
///   `feedTypeName = "视频"`，`media_info.mediaType` 也会给 `"video"`
/// - ★ **`pic` / `picArr` 实测全为空** —— 封面只在 `media_info.cover` 里，
///   所以九图槽位对视频动态**不出图**，必须由视频槽自己出封面
/// - `media_url` 顶层多数是「分享页地址」（B 站 / 微博），**不是能播的地址**
/// - 只有 `media_info.requestParams` 里 `fromType = coolapkVideo` 的那一档，
///   `"0"` 才是可直接播放的 mp4（实测 2/19）
/// - `media_info.duration` 是**毫秒**
///
/// ## 与 Android 的差异（如实说明）
/// Android 用 ExoPlayer **内嵌播放**（含 B 站 DASH 的音视频分离 + Referer 防盗链）。
/// Flutter 侧**没有引入播放器**（`media_kit` 之类会给 Windows 加 ~30 MB 原生库），
/// 所以这里**只做展示**：封面 + 时长 + ▶，点击**用系统浏览器打开**。
/// 对桌面端来说这是合理取舍 —— 浏览器播放能力比自己接播放器强得多。
class FeedVideo {
  const FeedVideo({
    required this.url,
    required this.poster,
    required this.duration,
    required this.provider,
    this.requestParams = '',
    this.aspectRatio = defaultAspect,
  });

  /// 可直接播放的地址；空串表示只有分享页地址（或没解析出来）
  final String url;

  /// 封面
  final String poster;

  /// 已格式化成 `m:ss` / `h:mm:ss`；空串表示服务端没给
  final String duration;

  /// `media_info.requestParams` 里的 fromType，仅用于排查
  final String provider;

  /// 交 `POST /v6/player/getUrl` 解析用的 `params`：
  /// **单个档位对象**的 JSON 原文，不是整张档位表。
  ///
  /// Android 注释（实测）：
  /// > `params` 必须是**单个档位对象**，形如
  /// > `{"fromType":"weiboDirect250924","0":"https://..."}`；
  /// > 传整张档位表（`{"普通":{...},"32":{...}}`）一律返回 `{"data":[]}`
  final String requestParams;

  /// 封面宽高比 = width / height；服务端多数不给，缺省 16:9
  final double aspectRatio;

  /// 还没地址但有解析参数 —— 点一下可以去 `/v6/player/getUrl` 试着解析
  bool get resolvable => url.isEmpty && requestParams.isNotEmpty;

  /// 官方 feed 封面的默认比例
  static const double defaultAspect = 16 / 9;

  /// 这些 provider 的 `"0"` 就是能直接播的地址
  static const Set<String> _directProviders = {'coolapkvideo', 'localvideo'};

  /// 地址以这些结尾才算媒体直链（分享页地址永远不带后缀，天然被排除）
  static const List<String> _mediaSuffix = ['.mp4', '.m3u8', '.m4v'];

  /// 从 `raw` 里解析；不是视频就返回 null。
  ///
  /// 对应 Android `FeedVideo.from(raw, feedType, feedTypeName)`。
  static FeedVideo? from(
    Map<String, dynamic>? raw, {
    String? feedType,
    String? feedTypeName,
  }) {
    if (raw == null) return null;

    final info = _obj(raw, ['media_info', 'mediaInfo']);
    final mediaType = _str(raw, ['media_type', 'mediaType']);
    final isVideo = mediaType == '2' ||
        mediaType.toLowerCase() == 'video' ||
        _str(info, ['mediaType', 'media_type']).toLowerCase() == 'video' ||
        (feedType ?? '').toLowerCase() == 'video' ||
        feedTypeName == '视频';
    if (!isVideo) return null;

    final video = _obj(raw, ['video', 'videoInfo', 'video_info']);
    final params = _lastRequestParams(info);
    final provider = _str(params, ['fromType', 'from_type']);

    // 1) 直链 provider 的 "0"
    var url = _directProviders.contains(provider.toLowerCase())
        ? _str(params, ['0', 'url', 'url0'])
        : '';
    // 2) 顶层 video_* 系列
    if (url.isEmpty) {
      url = _str(raw, ['video_url', 'videoUrl', 'videoURL', 'videoSrc', 'video_src']);
    }
    // 3) video 对象里
    if (url.isEmpty) {
      url = _str(video, ['url', 'videoUrl', 'src', 'playUrl', 'play_url']);
    }
    // 4) media_url 本身就是媒体直链才认
    if (url.isEmpty) url = _directMediaUrl(raw);

    var poster = _str(raw, ['video_pic', 'videoPic', 'video_cover', 'videoCover']);
    if (poster.isEmpty) poster = _str(raw, ['media_pic', 'mediaPic']);
    if (poster.isEmpty) poster = _str(info, ['cover', 'pic', 'poster', 'thumbnail']);
    if (poster.isEmpty) poster = _str(video, ['pic', 'cover', 'poster', 'thumbnail']);
    if (poster.isEmpty) poster = _str(raw, ['pic']);

    var duration = _formatDuration(
      _str(raw, ['video_duration', 'videoDuration']).isNotEmpty
          ? _str(raw, ['video_duration', 'videoDuration'])
          : _str(video, ['duration', 'videoDuration']),
      assumeMillis: false,
    );
    if (duration.isEmpty) {
      duration = _formatDuration(_str(info, ['duration', 'videoDuration']),
          assumeMillis: true);
    }

    return FeedVideo(
      url: _http2https(url),
      poster: _http2https(poster),
      duration: duration,
      provider: provider,
      requestParams: params == null ? '' : jsonEncode(params),
      aspectRatio: _aspectOf(info),
    );
  }

  /// 吃掉 `/v6/player/getUrl` 的返回，补上可播地址。
  ///
  /// 对应 Android `FeedVideo.withResolved`。原注释：
  /// > 解析不出来时服务端给的是 `{"data":[]}`（数组），所以这里必须自己判形状，
  /// > 形状不对就原样返回 —— 界面继续只出封面，绝不抛异常给上层。
  FeedVideo withResolved(Map<String, dynamic>? data) {
    if (data == null) return this;
    final resolved = _firstUrl(data, ['urlList', 'url_list']);
    if (resolved.isEmpty) return this;
    return FeedVideo(
      url: _http2https(resolved),
      poster: poster,
      duration: duration,
      provider: provider,
      requestParams: requestParams,
      aspectRatio: aspectRatio,
    );
  }

  /// 取数组里的第一个非空字符串（`urlList` / `audioList` 都是数组）
  static String _firstUrl(Map<String, dynamic> node, List<String> keys) {
    final v = _element(node, keys);
    if (v is! List) return '';
    for (final item in v) {
      if (item is String && item.trim().isNotEmpty) return item.trim();
    }
    return '';
  }

  // ────────────────── 内部工具（对应 Kotlin 的 private 方法）──────────────────

  /// 封面比例。服务端给错值时不至于把卡片撑坏，夹在 1:2 ~ 2.4:1 之间
  static double _aspectOf(Map<String, dynamic>? info) {
    final w = double.tryParse(_str(info, ['width']));
    final h = double.tryParse(_str(info, ['height']));
    if (w == null || h == null || w <= 0 || h <= 0) return defaultAspect;
    return (w / h).clamp(0.5, 2.4);
  }

  /// `media_url` / `mediaUrl`，仅当它本身就是媒体直链时才认
  static String _directMediaUrl(Map<String, dynamic> raw) {
    final url = _str(raw, ['media_url', 'mediaUrl', 'mediaURL']);
    if (url.isEmpty) return '';
    final lower = url.toLowerCase();
    return _mediaSuffix.any(lower.contains) ? url : '';
  }

  /// 取 `requestParams` 的**最后一档**（Android 参考实现取的是
  /// `Object.values(...).at(-1)`）。值可能是对象，也可能是 JSON 串，两种都兜住。
  static Map<String, dynamic>? _lastRequestParams(Map<String, dynamic>? info) {
    final node = _parseObject(_element(info, ['requestParams', 'request_params']));
    if (node == null) return null;
    Object? last;
    for (final v in node.values) {
      last = v;
    }
    return _parseObject(last);
  }

  /// 时长格式化。服务端给过 `"1:23"` 这种成品，也给过纯数字：
  /// 数字默认按秒算，`media_info.duration` 明确是毫秒所以调用处传 `assumeMillis: true`。
  static String _formatDuration(String value, {required bool assumeMillis}) {
    if (value.isEmpty) return '';
    if (value.contains(':')) return value;
    final number = double.tryParse(value);
    if (number == null || number <= 0) return '';
    final total = (assumeMillis || number >= 1000 ? number / 1000 : number).toInt();
    final hour = total ~/ 3600;
    final minute = total % 3600 ~/ 60;
    final second = total % 60;
    String two(int v) => v.toString().padLeft(2, '0');
    return hour > 0 ? '$hour:${two(minute)}:${two(second)}' : '$minute:${two(second)}';
  }

  /// 取多个别名里第一个非 null 的值
  static Object? _element(Map<String, dynamic>? node, List<String> keys) {
    if (node == null) return null;
    for (final k in keys) {
      final v = node[k];
      if (v != null) return v;
    }
    return null;
  }

  /// 字符串取值（非字符串一律给空串，对应 Kotlin 的 `isJsonPrimitive` 判断）
  static String _str(Map<String, dynamic>? node, List<String> keys) {
    final v = _element(node, keys);
    if (v == null) return '';
    if (v is String) return v;
    if (v is num || v is bool) return v.toString();
    return '';
  }

  /// 对象本身，或是「装着对象的 JSON 串」都能拿到，其余一律 null
  static Map<String, dynamic>? _obj(Map<String, dynamic>? node, List<String> keys) =>
      _parseObject(_element(node, keys));

  static Map<String, dynamic>? _parseObject(Object? node) {
    if (node == null) return null;
    if (node is Map) return Map<String, dynamic>.from(node);
    if (node is! String) return null;
    final text = node.trim();
    if (!text.startsWith('{')) return null;
    try {
      final parsed = jsonDecode(text);
      if (parsed is Map) return Map<String, dynamic>.from(parsed);
    } catch (_) {}
    return null;
  }

  /// Android `http2https`：把 http 换成 https（Coolapk 的图/视频 CDN 都支持 https，
  /// 而部分 provider 给的是 http，桌面端 WebView/浏览器混内容会拦）。
  static String _http2https(String url) =>
      url.startsWith('http://') ? url.replaceFirst('http://', 'https://') : url;
}

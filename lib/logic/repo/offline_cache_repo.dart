import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;

import 'package:dio/dio.dart' show Options, ResponseType;
import 'package:get/get.dart';
import 'package:path_provider/path_provider.dart';

import '../model/feed/datum.dart';
import '../model/offline/offline_feed.dart';
import '../network/network_repo.dart';
import '../network/request.dart';
import '../state/loading_state.dart';
import '../../utils/extensions.dart';
import '../../utils/storage_util.dart';

/// 离线缓存：把动态 / 图文连图带评论存到本机，断网（甚至原帖被删）也能看。
///
/// 对应 Android `logic/repository/OfflineCacheRepo.kt`（287 行）。
///
/// ## ★ 存的是**详情接口返回的原样 JSON**，只把里面的图片 URL 递归换成本地路径
/// 所以详情页不用为「离线」再写一套渲染 —— 把存档解回 `Datum`，
/// 走的是和在线页面**完全同一条渲染路径**。
///
/// ## 与 Android 的差异
/// Android 用 Room（SQLite）；本工程用 **Hive**（已有依赖），
/// 一条存档存成一个 Map，按 `fid` 作 key。其余逻辑逐条对齐。
class OfflineCacheRepo {
  OfflineCacheRepo._();

  /// 图片根目录名（在应用支持目录下）
  static const String dir = 'offline_cache';

  /// 存评论的条数：置顶那条在详情数据里，不占这个额度
  static const int replyLimit = 20;

  /// 本地图片地址前缀。带这个前缀的串由 `networkImage` 直接读本地文件。
  static const String localScheme = 'file://';

  /// 正在缓存的帖子 id —— 界面拿它显示「缓存中…」，顺便防重复点击
  static final savingIds = <String>{}.obs;

  static bool isSaving(String fid) => savingIds.contains(fid);

  // ────────────────────────── 读 ──────────────────────────

  static List<OfflineFeedEntity> loadAll() {
    final out = <OfflineFeedEntity>[];
    for (final key in GStorage.offlineCache.keys) {
      final raw = GStorage.offlineCache.get(key);
      if (raw is Map) {
        out.add(OfflineFeedEntity.fromJson(Map<String, dynamic>.from(raw)));
      }
    }
    // 新存的排前面
    out.sort((a, b) => b.savedAt.compareTo(a.savedAt));
    return out;
  }

  static OfflineFeedEntity? get(String fid) {
    final raw = GStorage.offlineCache.get(fid);
    if (raw is Map) {
      return OfflineFeedEntity.fromJson(Map<String, dynamic>.from(raw));
    }
    return null;
  }

  /// 存档 → 详情页要的 `Datum`（图片已经是本地路径）
  static Datum? feedDataOf(OfflineFeedEntity entity) {
    try {
      return Datum.fromJson(
          Map<String, dynamic>.from(jsonDecode(entity.feedJson) as Map));
    } catch (_) {
      return null;
    }
  }

  /// 存档 → 评论列表（置顶那条不在里面，它跟着详情数据走）
  static List<Datum> replyListOf(OfflineFeedEntity entity) {
    try {
      final v = jsonDecode(entity.replyJson);
      if (v is List) {
        return v
            .whereType<Map>()
            .map((e) => Datum.fromJson(Map<String, dynamic>.from(e)))
            .toList();
      }
    } catch (_) {}
    return const [];
  }

  // ────────────────────────── 删 ──────────────────────────

  static Future<void> delete(String fid) async {
    final e = get(fid);
    if (e != null) {
      final d = Directory(e.imageDir);
      if (d.existsSync()) await d.delete(recursive: true);
    }
    await GStorage.offlineCache.delete(fid);
  }

  static Future<void> deleteAll() async {
    for (final e in loadAll()) {
      final d = Directory(e.imageDir);
      if (d.existsSync()) await d.delete(recursive: true);
    }
    await GStorage.offlineCache.clear();
    final root = await _rootDir();
    if (root.existsSync()) await root.delete(recursive: true);
  }

  // ────────────────────────── 存 ──────────────────────────

  /// 异步存一条。**不 await**：缓存要下几十张图，用户点完就把面板划走了，
  /// 下载不能跟着入口的生命周期陪葬（Android 用应用级 scope，同此意）。
  ///
  /// 结果通过 [onDone] 回调（参数为 null 表示成功，否则是给用户看的原因）。
  static void saveAsync(String fid, {void Function(String? error)? onDone}) {
    // 已在下载就直接忽略重复点击
    if (savingIds.contains(fid)) return;
    savingIds.add(fid);

    save(fid).then((error) {
      savingIds.remove(fid);
      onDone?.call(error);
    }).catchError((Object e) {
      savingIds.remove(fid);
      onDone?.call('离线缓存失败，请检查网络后重试');
    });
  }

  /// 真正干活的：拉详情 → 拉评论 → 下图 → 落库。
  /// 返回 null 表示成功，否则是给用户看的原因。
  static Future<String?> save(String fid) async {
    const failed = '离线缓存失败，请检查网络后重试';

    // 详情接口与在线详情页用的是同一个：`/v6/feed/detail?id=`
    final state = await NetworkRepo.getDataFromUrl(url: '/v6/feed/detail?id=$fid');
    if (state is! Success) return failed;
    final datum = state.response as Datum;

    // ★ 用 `Datum.raw`（服务端原 JSON）而不是 `toJson()`：
    // 模型里**很多字段是注释掉的**，序列化会丢；详情页渲染要的就是原样 JSON。
    // 与 Android「存接口原样 JSON」的做法一致。
    final rawMap = datum.raw;
    if (rawMap == null || rawMap.isEmpty) return failed;
    final feedObj = Map<String, dynamic>.from(rawMap);

    final root = await _rootDir();
    final dir = Directory('${root.path}/$fid');
    // 重存时先清掉上次的图，免得留下再也引用不到的孤儿文件
    if (dir.existsSync()) await dir.delete(recursive: true);
    await dir.create(recursive: true);

    // 已经下过的图不重复下：一张图在正文、头像、评论里可能反复出现
    final downloaded = <String, String>{};
    await _rewriteMap(feedObj, dir, downloaded);

    final replies = await _fetchReply(fid, feedObj['feedType']?.toString());
    final replyList = replies.map((e) => e.raw ?? e.toJson()).toList();
    await _rewriteList(replyList, dir, downloaded);

    final userInfo =
        feedObj['userInfo'] is Map ? feedObj['userInfo'] as Map : null;

    final entity = OfflineFeedEntity(
      fid: fid,
      uid: userInfo?['uid']?.toString(),
      uname: userInfo?['username']?.toString(),
      avatar: userInfo?['userAvatar']?.toString(),
      title: _blankToNull(feedObj['messageTitle']?.toString()),
      message: _plainText(feedObj['message']?.toString()),
      cover: _coverOf(feedObj),
      device: _blankToNull(feedObj['deviceTitle']?.toString()),
      dateline: (feedObj['dateline'] as num?)?.toInt() ?? 0,
      replyCount: (feedObj['replynum'] as num?)?.toInt() ?? 0,
      savedReplyCount: replyList.length,
      savedAt: DateTime.now().millisecondsSinceEpoch,
      imageDir: dir.path,
      feedJson: jsonEncode(feedObj),
      replyJson: jsonEncode(replyList),
    );

    await GStorage.offlineCache.put(fid, entity.toJson());
    return null;
  }

  /// 置顶评论跟着详情数据走（在 `topReplyRows` 里），这里只取前 [replyLimit] 条普通评论。
  ///
  /// 参数取值抄 Android `OfflineCacheRepo.fetchReply`：
  /// `lastupdate_desc` / page 1 / first,last 都空 / `discussMode` 1 /
  /// `blockStatus` 0 / `fromFeedAuthor` 0。
  static Future<List<Datum>> _fetchReply(String fid, String? feedType) async {
    try {
      final state = await NetworkRepo.getFeedReply(
        id: fid,
        listType: 'lastupdate_desc',
        page: 1,
        firstItem: null,
        lastItem: null,
        discussMode: 1,
        feedType: feedType ?? '',
        blockStatus: 0,
        fromFeedAuthor: 0,
      );
      if (state is Success) {
        final list = state.response as List<Datum>;
        return list.take(replyLimit).toList();
      }
    } catch (_) {}
    return const [];
  }

  static String? _coverOf(Map<String, dynamic> feedObj) {
    final candidates = <String>[
      feedObj['messageCover']?.toString() ?? '',
      feedObj['pic']?.toString() ?? '',
      (feedObj['picArr'] is List && (feedObj['picArr'] as List).isNotEmpty)
          ? (feedObj['picArr'] as List).first.toString()
          : '',
    ];
    for (final c in candidates) {
      if (c.isNotEmpty) return c;
    }
    return null;
  }

  // ────────────────────────── 图片落地 ──────────────────────────

  /// 递归改写：对象 / 数组**就地改**，字符串如果有新值就返回给上层写回。
  ///
  /// 返回 null 表示「不用改」。
  static Future<Object?> _rewriteNode(
    Object? value,
    Directory dir,
    Map<String, String> done,
  ) async {
    if (value is String) {
      if (_imageUrl.hasMatch(value)) {
        final cached = done[value];
        if (cached != null) return cached;
        final local = await _localImage(value, dir);
        final result = local ?? value;
        done[value] = result;
        return result;
      }
      // 「JSON 装在字符串里」的字段：图文的 `messageRawOutput` 就是这类，
      // 而且它是**数组**（`[{...},{...}]`），正文里的图全在这里面，得钻进去改
      final trimmed = value.trimLeft();
      if (trimmed.startsWith('[')) {
        return _jsonInString(value, (s) async {
          try {
            final arr = jsonDecode(s);
            if (arr is! List) return null;
            await _rewriteList(arr, dir, done);
            return jsonEncode(arr);
          } catch (_) {
            return null;
          }
        });
      }
      if (trimmed.startsWith('{')) {
        return _jsonInString(value, (s) async {
          try {
            final obj = jsonDecode(s);
            if (obj is! Map) return null;
            final m = Map<String, dynamic>.from(obj);
            await _rewriteMap(m, dir, done);
            return jsonEncode(m);
          } catch (_) {
            return null;
          }
        });
      }
      return null;
    }
    if (value is Map) {
      await _rewriteMap(value, dir, done);
      return null;
    }
    if (value is List) {
      await _rewriteList(value, dir, done);
      return null;
    }
    return null;
  }

  static Future<void> _rewriteMap(
    Map<dynamic, dynamic> obj,
    Directory dir,
    Map<String, String> done,
  ) async {
    for (final key in obj.keys.toList()) {
      final v = await _rewriteNode(obj[key], dir, done);
      if (v != null) obj[key] = v;
    }
  }

  static Future<void> _rewriteList(
    List<dynamic> arr,
    Directory dir,
    Map<String, String> done,
  ) async {
    for (var i = 0; i < arr.length; i++) {
      final v = await _rewriteNode(arr[i], dir, done);
      if (v != null) arr[i] = v;
    }
  }

  /// 把字符串里装着的 JSON 解出来改写，返回新串。
  ///
  /// ★ **只有真的改到了内容才回写**：`extraData` 那种 `{"avgData":"6.9"}` 的小对象
  /// 解析一遍也会拼回同样的串，返回 null 让上层保留原值，
  /// 不必给每个字符串都白白重排一次。
  static Future<String?> _jsonInString(
    String raw,
    Future<String?> Function(String) rewrite,
  ) async {
    final out = await rewrite(raw);
    return (out != null && out != raw) ? out : null;
  }

  /// 下到本机，返回交给渲染层的地址：
  /// `file://` + 本地绝对路径 + **原 URL 上的尺寸提示**。
  ///
  /// ★ 尺寸提示必须留着：图文正文里每张图的宽高比是 `Utils.getImageLp`
  /// 从地址里解出来的（`@1000x1000.jpg` 这种），
  /// 只留一个干净路径的话，**宽图会被当成 1:1 裁成方的**。
  static Future<String?> _localImage(String url, Directory dir) async {
    try {
      final path = await downloadToFile(url, File('${dir.path}/${_imageFileName(url)}'));
      if (path == null) return null;
      return '$localScheme$path${_urlSuffix(url)}';
    } catch (_) {
      return null;
    }
  }

  /// 原 URL 挂在文件名后面的尺寸 / 处理后缀（`@1000x1000.jpg`、`?x-oss-process=...`）
  static String _urlSuffix(String url) {
    final tail = url.substring(url.lastIndexOf('/') + 1);
    final i = tail.indexOf(RegExp(r'[@?]'));
    return i == -1 ? '' : tail.substring(i);
  }

  static String _imageFileName(String url) {
    final path = url.split('?').first.split('@').first;
    var ext = path.contains('.') ? path.split('.').last.toLowerCase() : '';
    ext = ext.replaceAll(RegExp(r'[^a-z0-9]'), '');
    if (ext.length > 4) ext = ext.substring(0, 4);
    if (ext.isEmpty) ext = 'jpg';
    return '${url.hashCode.abs()}.$ext';
  }

  /// 把 `file://` + 路径 + 尺寸后缀还原成**真实文件路径**（去掉尺寸后缀）。
  ///
  /// 渲染层（`networkImage`）用它读本地文件。
  static String localFilePath(String localUrl) {
    var s = localUrl.substring(localScheme.length);
    final i = s.lastIndexOf('@');
    final j = s.lastIndexOf('?');
    final cut = math.max(i, j);
    if (cut > 0) s = s.substring(0, cut);
    return s;
  }

  /// 是不是本地离线地址
  static bool isLocal(String url) => url.startsWith(localScheme);

  // ────────────────────────── 杂 ──────────────────────────

  static Future<Directory> _rootDir() async {
    final base = await getApplicationSupportDirectory();
    return Directory('${base.path}/$dir');
  }

  static String? _blankToNull(String? s) =>
      (s == null || s.trim().isEmpty) ? null : s;

  /// 正文纯文本，列表页拿它当摘要
  static String? _plainText(String? s) {
    if (s == null) return null;
    final t = s.replaceAll(RegExp('<[^>]*>'), '').replaceAll(RegExp(r'\s+'), ' ').trim();
    return t.isEmpty ? null : t;
  }

  /// 认图片 URL 的口径：coolapk 的图后面常跟 `@1000w_1000h.webp` 这类处理串、
  /// 或者 `?x-oss-process=...` 的查询串，所以扩展名后面允许再挂任意非空白字符。
  static final _imageUrl = RegExp(
    r'^https?://\S+\.(jpg|jpeg|png|webp|gif|bmp|heic)\S*$',
    caseSensitive: false,
  );
}

/// 下载图片到 [target]，成功返回绝对路径。
///
/// 走工程自己的 dio（`Request`），这样**拦截器会把酷安要求的 UA / Referer /
/// Cookie 一并带上** —— 直接用 `HttpClient` 裸下 `image.coolapk.com` 会拿到 567。
Future<String?> downloadToFile(String url, File target) async {
  try {
    if (target.existsSync()) return target.path;
    final res = await Request().get<List<int>>(
      url,
      options: Options(responseType: ResponseType.bytes),
    );
    final bytes = res.data;
    if (bytes == null || bytes.isEmpty) return null;
    await target.create(recursive: true);
    await target.writeAsBytes(bytes, flush: true);
    return target.path;
  } catch (_) {
    return null;
  }
}

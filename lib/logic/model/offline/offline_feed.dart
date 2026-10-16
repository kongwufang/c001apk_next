/// 离线缓存的数据模型。
///
/// 对应 Android `logic/model/OfflineFeedEntity.kt` + `OfflineFeedSummary.kt`
/// （那边用 Room，本工程用 Hive —— 见 `OfflineCacheRepo`）。
library;

/// 一条离线存档：帖子文稿 + 图片 + 置顶评论 + 前 20 条评论。
///
/// ## ★ 为什么存**整份 JSON 快照**而不是拆成一张张表
/// 详情页渲染用的就是接口原样的 `Datum`（图文还得再解 `messageRawOutput`）。
/// 拆表等于把几十个字段和嵌套结构重搬一遍，**服务端一加字段这里就漏**。
/// 存原文的代价只是列表页要用的几个字段冗余一份。
///
/// ## ★ 落库前所有图片 URL 都会被换成本地文件路径
/// 所以断网、**甚至原帖已经被删掉**，也照样能翻出来看。
class OfflineFeedEntity {
  OfflineFeedEntity({
    required this.fid,
    this.uid,
    this.uname,
    this.avatar,
    this.title,
    this.message,
    this.cover,
    this.device,
    this.dateline = 0,
    this.replyCount = 0,
    this.savedReplyCount = 0,
    required this.savedAt,
    required this.imageDir,
    required this.feedJson,
    required this.replyJson,
  });

  /// 帖子 id，同时是主键：同一个帖子重复缓存直接覆盖
  final String fid;
  final String? uid;
  final String? uname;

  /// 作者头像，**本地路径**
  final String? avatar;

  /// 图文标题，动态没有这一项
  final String? title;

  /// 正文纯文本，列表页拿它当摘要
  final String? message;

  /// 列表页封面（正文第一张图 / 图文封面），**本地路径**
  final String? cover;
  final String? device;
  final int dateline;

  /// 原帖的回复总数；存档里只有置顶 + 前 20 条，通常比这个数小
  final int replyCount;

  /// 存档里实际存下来的评论条数
  final int savedReplyCount;

  /// 存下来的本机时间
  final int savedAt;

  /// 图片目录，删存档时整个目录一起删
  final String imageDir;

  /// `Datum` 的 JSON 原文（图片已换成本地路径）
  final String feedJson;

  /// 评论列表的 JSON 原文（图片已换成本地路径）
  final String replyJson;

  Map<String, dynamic> toJson() => {
        'fid': fid,
        'uid': uid,
        'uname': uname,
        'avatar': avatar,
        'title': title,
        'message': message,
        'cover': cover,
        'device': device,
        'dateline': dateline,
        'replyCount': replyCount,
        'savedReplyCount': savedReplyCount,
        'savedAt': savedAt,
        'imageDir': imageDir,
        'feedJson': feedJson,
        'replyJson': replyJson,
      };

  factory OfflineFeedEntity.fromJson(Map<String, dynamic> json) =>
      OfflineFeedEntity(
        fid: json['fid']?.toString() ?? '',
        uid: json['uid']?.toString(),
        uname: json['uname']?.toString(),
        avatar: json['avatar']?.toString(),
        title: json['title']?.toString(),
        message: json['message']?.toString(),
        cover: json['cover']?.toString(),
        device: json['device']?.toString(),
        dateline: (json['dateline'] as num?)?.toInt() ?? 0,
        replyCount: (json['replyCount'] as num?)?.toInt() ?? 0,
        savedReplyCount: (json['savedReplyCount'] as num?)?.toInt() ?? 0,
        savedAt: (json['savedAt'] as num?)?.toInt() ?? 0,
        imageDir: json['imageDir']?.toString() ?? '',
        feedJson: json['feedJson']?.toString() ?? '',
        replyJson: json['replyJson']?.toString() ?? '[]',
      );
}

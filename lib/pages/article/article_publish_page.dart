import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart' show FormData;
import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
// ⚠️ get 也导出了 `FormData`（它自己那套），与 dio 冲突 —— hide 掉用 dio 的。
import 'package:get/get.dart' hide FormData;
import 'package:image_picker/image_picker.dart';

import '../../logic/model/article/article_block.dart';
import '../../logic/model/oss_upload/oss_upload_model.dart';
import '../../logic/network/network_repo.dart';
import '../../utils/oss/aliyunoss_client.dart';
import '../../utils/oss/aliyunoss_config.dart';
import '../../utils/oss_util.dart';

/// 图文发布。
///
/// 对应 Android `ui/article/ArticlePublishActivity.kt`（752 行）。
///
/// ## 与 Android 的差异（如实说明）
/// Android 的正文是**一个 EditText + 内联图片 span**（`PLACEHOLDER = "\uFFFC"`），
/// 图片插在光标处。Flutter 的 `TextField` 做内联图很难，所以这里改成
/// **块列表**：正文 = 一个有序的「文本块 / 图片块」序列，可增删。
///
/// 两者产出的 `message` JSON **结构完全一致**（同样是 text/image 交错的数组），
/// 只是编辑手感不同。
///
/// ## 请求契约（照抄 Android）
/// 1. `ossUploadPrepare`：`uploadBucket=image` / `uploadDir=feed` / `feed_type=feed`，
///    `uploadFileList` = **封面(1600x719) + 正文图**，每项带 `hdr=0`
/// 2. 逐个 PUT 到 OSS
/// 3. `POST /v6/feed/createFeed`：
///    ```
///    id="" type="feed" status="1"
///    publish_status = "1" 公开 / "0" 仅自己可见
///    message_title = 标题
///    is_html_article = "1"        ← 标记为图文
///    pic = ""
///    message = [{type:text,message}, {type:image,url,description}, ...]
///    message_cover = <prefix>/<封面文件名>
///    ```
///
/// ## 校验（照抄 Android `updatePublishState`）
/// 标题非空 **且** 选了封面 **且**（正文非空 或 至少一张图）→ 发布按钮才可用。
class ArticlePublishPage extends StatefulWidget {
  const ArticlePublishPage({super.key});

  @override
  State<ArticlePublishPage> createState() => _ArticlePublishPageState();
}

class _ArticlePublishPageState extends State<ArticlePublishPage> {
  /// 正文图上限（Android `MAX_BODY_IMAGES = 20`）
  static const int _maxBodyImages = 20;

  /// 图片说明上限（Android `DESC_MAX_LENGTH = 200`）
  static const int _descMaxLength = 200;

  /// 封面在 `uploadFileList` 里声明的分辨率（Android 写死这个值）
  static const String _coverResolution = '1600x719';

  final _titleController = TextEditingController();

  /// 正文块列表。初始给一个空文本块，让用户直接开始写。
  final List<ArticleBlock> _blocks = [ArticleTextBlock()];

  /// 每个文本块一个 controller（与 `_blocks` 里的文本块一一对应，按 index 维护）
  final Map<ArticleTextBlock, TextEditingController> _textControllers = {};

  /// 封面
  XFile? _coverFile;
  Uint8List? _coverBytes;
  String _coverName = '';
  String _coverMd5 = '';

  bool _privateVisible = false;
  bool _publishing = false;

  @override
  void initState() {
    super.initState();
    // 文本块变化要重算「发布」按钮是否可用
    _titleController.addListener(_refresh);
  }

  @override
  void dispose() {
    _titleController.dispose();
    for (final c in _textControllers.values) {
      c.dispose();
    }
    super.dispose();
  }

  void _refresh() {
    if (mounted) setState(() {});
  }

  TextEditingController _controllerOf(ArticleTextBlock block) =>
      _textControllers.putIfAbsent(block, () {
        final c = TextEditingController(text: block.text);
        c.addListener(_refresh);
        return c;
      });

  // ────────────────── 校验 ──────────────────

  List<ArticleImageBlock> get _images =>
      _blocks.whereType<ArticleImageBlock>().toList();

  bool get _hasBodyText =>
      _blocks.whereType<ArticleTextBlock>().any((b) => b.text.trim().isNotEmpty);

  /// 照抄 Android `updatePublishState`：
  /// 标题非空 且 选了封面 且（正文非空 或 有图）
  bool get _canPublish =>
      _titleController.text.trim().isNotEmpty &&
      _coverFile != null &&
      (_hasBodyText || _images.isNotEmpty);

  // ────────────────── 选图 ──────────────────

  /// 读图 → 算 md5 → 取分辨率。三者都是 `ossUploadPrepare` 要的。
  Future<({Uint8List bytes, String md5, String resolution, String name})?>
      _prepareImage(XFile file) async {
    try {
      final bytes = await file.readAsBytes();
      if (bytes.isEmpty) return null;
      final md5hex = md5.convert(bytes).toString();

      // 分辨率（`uploadFileList` 里的 `resolution`）
      final codec = await ui.instantiateImageCodec(bytes);
      final frame = await codec.getNextFrame();
      final w = frame.image.width;
      final h = frame.image.height;
      frame.image.dispose();

      final name = file.name.isNotEmpty ? file.name : '$md5hex.jpg';
      return (
        bytes: bytes,
        md5: md5hex,
        resolution: '${w}x$h',
        name: name,
      );
    } catch (_) {
      return null;
    }
  }

  Future<void> _pickCover() async {
    final picked = await ImagePicker().pickImage(source: ImageSource.gallery);
    if (picked == null) return;
    final info = await _prepareImage(picked);
    if (info == null) {
      SmartDialog.showToast('读取图片失败');
      return;
    }
    setState(() {
      _coverFile = picked;
      _coverBytes = info.bytes;
      _coverMd5 = info.md5;
      _coverName = info.name;
    });
  }

  Future<void> _addImage() async {
    if (_images.length >= _maxBodyImages) {
      // Android 同此上限
      SmartDialog.showToast('最多 $_maxBodyImages 张图片');
      return;
    }
    final picked = await ImagePicker().pickImage(source: ImageSource.gallery);
    if (picked == null) return;
    final info = await _prepareImage(picked);
    if (info == null) {
      SmartDialog.showToast('读取图片失败');
      return;
    }
    final block = ArticleImageBlock(picked)
      ..bytes = info.bytes
      ..md5 = info.md5
      ..resolution = info.resolution
      ..name = info.name;
    setState(() => _blocks.add(block));
  }

  // ────────────────── 发布 ──────────────────

  Future<void> _publish() async {
    // 三段校验，文案照抄 Android `publish()`
    final title = _titleController.text.trim();
    if (title.isEmpty) {
      SmartDialog.showToast('请输入标题');
      return;
    }
    if (_coverFile == null) {
      SmartDialog.showToast('请选择封面图');
      return;
    }
    if (!_hasBodyText && _images.isEmpty) {
      SmartDialog.showToast('请输入正文');
      return;
    }
    if (_publishing) return;
    setState(() => _publishing = true);

    try {
      final images = _images;

      // ① uploadFileList：封面在前，正文图依次在后（顺序与 message 里的编号一致）
      final uploadFiles = <OssUploadModel>[
        OssUploadModel(
          name: _coverName,
          resolution: _coverResolution,
          md5: _coverMd5,
        ),
        ...images.map((e) => OssUploadModel(
              name: e.name,
              resolution: e.resolution,
              md5: e.md5,
            )),
      ];

      final prepare = await OssUtil.onPostOSSUploadPrepare(
        'image',
        'feed',
        uploadFiles,
        forArticle: true, // 图文多 hdr + feed_type
      );
      if (prepare == null) {
        SmartDialog.showToast('准备上传失败');
        return;
      }

      final info = prepare.uploadPrepareInfo!;
      final fileInfo = prepare.fileInfo!;
      if (fileInfo.length < uploadFiles.length) {
        SmartDialog.showToast('上传凭证数量不符');
        return;
      }

      final client = AliyunOssClient(
        accessKeyId: info.accessKeyId!,
        accessKeySecret: info.accessKeySecret!,
        securityToken: info.securityToken!,
      );
      final config = AliyunOssConfig(
        endpoint: info.endPoint!,
        bucket: info.bucket!,
        directory: 'feed',
      );

      // ② 逐个 PUT 到 OSS（下标 0 = 封面）
      final paths = <String>[_coverFile!.path, ...images.map((e) => e.file.path)];
      for (var i = 0; i < paths.length; i++) {
        SmartDialog.showLoading(msg: '正在上传图片 ${i + 1}/${paths.length}');
        final result = await client.upload(
          id: i.toString(),
          config: config,
          // 与 reply / 收藏封面上传同一套做法：OSS 对象名要去掉目录前缀
          ossFileName: fileInfo[i].uploadFileName!.replaceFirst('feed', ''),
          filePath: paths[i],
        );
        if (result.state != AliyunOssResultState.success) {
          SmartDialog.dismiss();
          SmartDialog.showToast('图片上传失败');
          return;
        }
      }

      // ③ 组装 message / message_cover 并发布
      final prefix = info.uploadImagePrefix!;
      final fileNames = fileInfo.map((e) => e.uploadFileName!).toList();
      final messageJson = encodeArticleMessage(buildArticleMessage(
        prefix: prefix,
        fileNames: fileNames,
        blocks: _blocks,
      ));
      final coverUrl = '$prefix/${fileNames[0]}';

      final form = buildArticleFeedForm(
        title: title,
        privateVisible: _privateVisible,
        messageJson: messageJson,
        coverUrl: coverUrl,
      );

      SmartDialog.showLoading(msg: '正在发布');
      final res = await NetworkRepo.postCreateFeed(FormData.fromMap(form));
      SmartDialog.dismiss();

      final body = res.data;
      String? fail;
      String? newId;
      if (body is Map) {
        final data = body['data'];
        if (data is Map) newId = data['id']?.toString();
        fail = body['message']?.toString();
      }
      if (newId == null || newId.isEmpty) {
        SmartDialog.showToast(
            (fail != null && fail.isNotEmpty) ? fail : '发布失败');
        return;
      }
      SmartDialog.showToast('发布成功');
      Get.back(result: true);
    } catch (e) {
      SmartDialog.dismiss();
      SmartDialog.showToast('发布失败：$e');
    } finally {
      if (mounted) setState(() => _publishing = false);
    }
  }

  // ────────────────── UI ──────────────────

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: const Text('发布图文'),
        actions: [
          TextButton(
            onPressed: _canPublish && !_publishing ? _publish : null,
            child: Text(
              '发布',
              style: TextStyle(
                color: _canPublish && !_publishing
                    ? colorScheme.primary
                    : colorScheme.outline,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          const SizedBox(width: 4),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          // 标题
          TextField(
            controller: _titleController,
            maxLines: 2,
            minLines: 1,
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            decoration: const InputDecoration(
              hintText: '标题',
              border: InputBorder.none,
            ),
          ),
          const Divider(height: 24),

          // 封面
          _sectionTitle('封面'),
          const SizedBox(height: 8),
          _coverPicker(colorScheme),
          const SizedBox(height: 6),
          Text(
            '封面比例固定为 1600:719，选图后需裁剪',
            style: TextStyle(fontSize: 12, color: colorScheme.outline),
          ),
          const Divider(height: 24),

          // 正文
          Row(
            children: [
              Expanded(child: _sectionTitle('正文')),
              Text(
                '点击图片可加说明',
                style: TextStyle(fontSize: 12, color: colorScheme.outline),
              ),
            ],
          ),
          const SizedBox(height: 8),
          ..._blocks.asMap().entries.map((e) => _blockTile(e.key, e.value)),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: _images.length >= _maxBodyImages ? null : _addImage,
            icon: const Icon(Icons.add_photo_alternate_outlined),
            label: Text('添加图片（${_images.length}/$_maxBodyImages）'),
          ),
          const SizedBox(height: 24),

          // 可见性
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            value: _privateVisible,
            onChanged: (v) => setState(() => _privateVisible = v),
            title: const Text('仅自己可见'),
          ),
        ],
      ),
    );
  }

  Widget _sectionTitle(String text) => Text(
        text,
        style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
      );

  Widget _coverPicker(ColorScheme colorScheme) {
    return InkWell(
      onTap: _pickCover,
      borderRadius: BorderRadius.circular(12),
      child: AspectRatio(
        // 1600:719
        aspectRatio: 1600 / 719,
        child: Container(
          decoration: BoxDecoration(
            color: colorScheme.surfaceContainer,
            borderRadius: BorderRadius.circular(12),
          ),
          clipBehavior: Clip.antiAlias,
          child: _coverBytes == null
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.image_outlined,
                          size: 32, color: colorScheme.outline),
                      const SizedBox(height: 6),
                      Text('点击选择封面图',
                          style:
                              TextStyle(fontSize: 13, color: colorScheme.outline)),
                    ],
                  ),
                )
              : Image.memory(_coverBytes!, fit: BoxFit.cover),
        ),
      ),
    );
  }

  Widget _blockTile(int index, ArticleBlock block) {
    final colorScheme = Theme.of(context).colorScheme;
    switch (block) {
      case ArticleTextBlock():
        return Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: TextField(
                  controller: _controllerOf(block),
                  onChanged: (v) => block.text = v,
                  maxLines: null,
                  minLines: 3,
                  keyboardType: TextInputType.multiline,
                  decoration: InputDecoration(
                    hintText: '正文',
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ),
              ),
              if (_blocks.length > 1)
                IconButton(
                  tooltip: '删除这一段',
                  onPressed: () {
                    setState(() {
                      _textControllers.remove(block)?.dispose();
                      _blocks.removeAt(index);
                    });
                  },
                  icon: const Icon(Icons.delete_outline),
                ),
            ],
          ),
        );
      case ArticleImageBlock():
        return Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: SizedBox(
                  width: 96,
                  height: 96,
                  child: block.bytes != null
                      ? Image.memory(block.bytes!, fit: BoxFit.cover)
                      : ColoredBox(color: colorScheme.surfaceContainer),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: TextField(
                  maxLength: _descMaxLength,
                  onChanged: (v) => block.description = v,
                  maxLines: null,
                  decoration: InputDecoration(
                    hintText: '图片说明（可留空）',
                    isDense: true,
                    counterStyle: TextStyle(
                        fontSize: 11, color: colorScheme.outline),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ),
              ),
              IconButton(
                tooltip: '删除这张图',
                onPressed: () {
                  setState(() => _blocks.removeAt(index));
                },
                icon: const Icon(Icons.delete_outline),
              ),
            ],
          ),
        );
    }
  }
}

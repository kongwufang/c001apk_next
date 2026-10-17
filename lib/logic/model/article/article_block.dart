import 'dart:convert';
import 'dart:typed_data';

// `XFile` 从 image_picker 拿 —— `cross_file` 在本工程里只是**传递依赖**，
// 直接 import 会触发 `depend_on_referenced_packages`；
// 而 image_picker 是直接依赖，且它 re-export 了 XFile。
import 'package:image_picker/image_picker.dart';

/// 图文正文的一个块。
///
/// 对应 Android `ui/article/ArticleBlock.kt`：
/// > 图文正文块：按顺序排列即正文的「文本/图片」混排结构。
/// > 服务端 `message` 字段为 JSON 数组，text 块与 image 块交错。
sealed class ArticleBlock {
  const ArticleBlock();
}

class ArticleTextBlock extends ArticleBlock {
  ArticleTextBlock([this.text = '']);

  String text;
}

class ArticleImageBlock extends ArticleBlock {
  ArticleImageBlock(this.file);

  final XFile file;

  /// 图片说明（对应 Android `Image.description`，上限 200 字）
  String description = '';

  /// 上传用的三个字段（`ossUploadPrepare` 的 `uploadFileList` 需要）
  String name = '';
  String resolution = '';
  String md5 = '';

  /// 选图时读一次缓存下来，发布时不必再读盘
  Uint8List? bytes;

  /// 服务端返回的 `uploadFileName`（上传前为空）
  String uploadedFileName = '';
}

/// 组装 `createFeed` 的 `message` 字段。
///
/// 照抄 Android `ArticlePublishActivity.buildMsgList`：
/// ```kotlin
/// mapOf("type" to "text",  "message" to chunk)
/// mapOf("type" to "image", "url" to prefix + "/" + fileName,
///                         "description" to description)
/// ```
///
/// ★ `fileNames` 的顺序是「**封面在前**，正文图依次在后」
/// （Android：`fileInfo[0]` = 封面，`fileInfo[1..]` = 正文图），
/// 所以正文第 i 张图取 `fileNames[1 + i]`。
List<Map<String, String>> buildArticleMessage({
  required String prefix,
  required List<String> fileNames,
  required List<ArticleBlock> blocks,
}) {
  final list = <Map<String, String>>[];
  var imageIndex = 0;
  for (final block in blocks) {
    switch (block) {
      case ArticleTextBlock(:final text):
        final t = text.trim();
        // 空文本块直接跳过（Android 里 `if (chunk.isNotEmpty())`）
        if (t.isNotEmpty) list.add({'type': 'text', 'message': t});
        break;
      case ArticleImageBlock(:final description):
        final idx = 1 + imageIndex;
        imageIndex++;
        if (idx >= fileNames.length) break;
        list.add({
          'type': 'image',
          'url': '$prefix/${fileNames[idx]}',
          'description': description,
        });
        break;
    }
  }
  return list;
}

/// `createFeed` 的完整表单。
///
/// 照抄 Android `ArticlePublishActivity` 的 `feedData.apply { ... }`：
/// ```
/// id = ""  type = "feed"  status = "1"
/// publish_status = 1(公开) / 0(私密)
/// message_title = 标题
/// is_html_article = "1"     ← 关键：标记为图文
/// pic = ""
/// message = 块数组 JSON
/// message_cover = 封面 URL
/// ```
Map<String, String> buildArticleFeedForm({
  required String title,
  required bool privateVisible,
  required String messageJson,
  required String coverUrl,
}) {
  return {
    'id': '',
    'type': 'feed',
    'status': '1',
    'publish_status': privateVisible ? '0' : '1',
    'message_title': title,
    'is_html_article': '1',
    'pic': '',
    'message': messageJson,
    'message_cover': coverUrl,
  };
}

/// 把块数组序列化成 JSON（服务端要的是数组，不是字符串数组）
String encodeArticleMessage(List<Map<String, String>> blocks) =>
    jsonEncode(blocks);

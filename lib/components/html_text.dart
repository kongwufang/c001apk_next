import 'package:c001apk_flutter/components/network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_widget_from_html_core/flutter_widget_from_html_core.dart';
import 'package:get/get.dart';

import '../utils/emoji_util.dart';
import '../utils/extensions.dart';
import '../utils/markdown_util.dart';
import '../utils/storage_util.dart';
import '../utils/utils.dart';

/// 代码块渲染（带「一键复制」）。
///
/// Markdown 的 ``` 围栏与 HTML 的 `<pre>` 都会走到这里。
/// 横向可滚动，避免长行被折得难读。
Widget _codeBlock(String code, double fontSize) {
  final colorScheme = Theme.of(Get.context!).colorScheme;
  final text = code.replaceAll(RegExp(r'\n+$'), '');
  return Container(
    width: double.infinity,
    margin: const EdgeInsets.symmetric(vertical: 6),
    decoration: BoxDecoration(
      color: colorScheme.surfaceContainerHigh,
      borderRadius: BorderRadius.circular(8),
    ),
    child: Stack(
      children: [
        Padding(
          // 右侧留出复制按钮的位置
          padding: const EdgeInsets.fromLTRB(12, 12, 36, 12),
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Text(
              text,
              style: TextStyle(
                fontFamily: 'monospace',
                fontSize: fontSize - 2,
                height: 1.45,
              ),
            ),
          ),
        ),
        Positioned(
          top: 0,
          right: 0,
          child: IconButton(
            iconSize: 16,
            visualDensity: VisualDensity.compact,
            tooltip: '复制代码',
            icon: const Icon(Icons.copy_all_outlined),
            onPressed: () => Utils.copyText(text),
          ),
        ),
      ],
    ),
  );
}

Widget htmlText(
  String html, {
  double fontSize = 15,
  bool isBold = false,
  List<String>? picArr,
  Function? onClick,
  Function? onShowTotalReply,
  Function? onViewFan,
}) {
  // ★ Markdown 自动识别。
  //
  // 酷安服务端不支持 Markdown，作者写进正文的 md 语法是**当纯文本存的**，
  // 所以客户端要自己判断这段正文是普通文本还是 md（对齐 Android
  // `MarkdownUtils.isMarkdown` + Markwon 的方案，见 `utils/markdown_util.dart`）。
  //
  // 命中则先做 HTML→md 预处理再转成 HTML，然后**复用下面这整条 HtmlWidget 管道** ——
  // 表情替换、图片点击、链接跳转、@ 提及等既有能力全部保留。
  final isMd = isMarkdown(html);
  final source = isMd ? toHtml(html) : html;

  String? htmlConvert;
  if (GStorage.showEmoji) {
    RegExp regExp = RegExp('\\[[^\\]]+\\]');
    // ⚠️ md 路径**不能**把换行换成 `<br/>`：
    // markdown 已经把结构转成 `<p>` / `<li>` 了，再塞 `<br/>` 会多出空行。
    // 非 md 路径保持原行为（服务端正文用 `\n` 表示换行）。
    final base = isMd ? source : source.replaceAll('\n', '<br/>');
    htmlConvert = base.replaceAllMapped(
      regExp,
      (match) {
        String matchedString = match.group(0) ?? '';
        String? src = EmojiUtil.emojiMap[matchedString];
        if (!src.isNullOrEmpty) {
          return '<img src="$src">';
        } else {
          return matchedString;
        }
      },
    );
  }

  return HtmlWidget(
    htmlConvert ?? source,
    onTapUrl: (url) {
      if (url.isEmpty) {
        if (onClick != null) {
          onClick();
        }
      } else if (url.contains('/feed/replyList')) {
        if (onShowTotalReply != null) {
          onShowTotalReply();
        }
      } else if (url.contains('image.coolapk.com')) {
        Map<dynamic, dynamic> arguments = {
          "imgList": !picArr.isNullOrEmpty ? picArr : [url],
        };
        Get.toNamed('/imageview', arguments: arguments);
      } else if (url == '/contacts/fans') {
        if (onViewFan != null) {
          onViewFan();
        }
      } else {
        Utils.onOpenLink(url, null);
      }
      return true;
    },
    textStyle: TextStyle(
      fontSize: fontSize,
      height: 1.5,
      fontWeight: isBold ? FontWeight.bold : FontWeight.normal,
    ),
    customStylesBuilder: (element) {
      switch (element.localName) {
        case 'a':
          return {'text-decoration': 'none'};
      }
      return null;
    },
    customWidgetBuilder: (element) {
      switch (element.localName) {
        // ★ 代码块：加「一键复制」（release-notes 明确要求的功能）。
        // `pre` 里通常还嵌一层 `code`，`element.text` 能一次取到全部代码文本。
        case 'pre':
          final code = element.text;
          if (code.trim().isEmpty) return null;
          return _codeBlock(code, fontSize);
        case 'img':
          {
            double size =
                MediaQuery.of(Get.context!).textScaler.scale(fontSize);
            String src = element.attributes['src'] ?? '';
            return InlineCustomWidget(
              alignment: PlaceholderAlignment.middle,
              child: src.endsWith('icon')
                  ? Icon(
                      size: size * 1.3,
                      Icons.photo_outlined,
                      color: Theme.of(Get.context!).colorScheme.primary,
                    )
                  : src.endsWith('png')
                      ? Image.asset(
                          'assets/emojis/$src',
                          width: size * 1.3,
                          height: size * 1.3,
                        )
                      : src.startsWith('http')
                          ? networkImage(
                              src,
                              width: size * 1.3,
                              height: size * 1.3,
                            )
                          : Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 2,
                                vertical: 1,
                              ),
                              decoration: BoxDecoration(
                                border: Border.all(
                                  color: Theme.of(Get.context!)
                                      .colorScheme
                                      .primary,
                                ),
                                borderRadius:
                                    const BorderRadius.all(Radius.circular(4)),
                              ),
                              child: Text(
                                src,
                                style: TextStyle(
                                  height: 1,
                                  fontWeight: FontWeight.bold,
                                  fontSize: fontSize - 4,
                                  color: Theme.of(Get.context!)
                                      .colorScheme
                                      .primary,
                                ),
                                strutStyle: StrutStyle(
                                  height: 1,
                                  leading: 0,
                                  fontWeight: FontWeight.bold,
                                  fontSize: fontSize - 4,
                                ),
                              ),
                            ),
            );
          }
      }
      return null;
    },
  );
}

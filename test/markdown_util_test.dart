import 'package:c001apk_flutter/utils/markdown_util.dart';
import 'package:flutter_test/flutter_test.dart';

/// Markdown 自动识别 / URL 清洗 / HTML 预处理的单元测试。
///
/// 这些是纯函数，CI 的 `flutter test` 会跑 —— 所以移植是否忠实，
/// **不依赖人工截图**就能验证。
///
/// 用例对着 Android `util/MarkdownUtils.kt` 的规则逐条造：
/// 强特征任一命中即 md；弱特征要累计 2 个以上；判断前先剔除酷安话题。
void main() {
  group('isMarkdown —— 强特征：命中任一即为 md', () {
    test('代码围栏', () {
      expect(isMarkdown('```dart\nvoid main() {}\n```'), isTrue);
    });

    test('标题', () {
      expect(isMarkdown('# 这是一级标题\n正文'), isTrue);
      expect(isMarkdown('### 三级标题'), isTrue);
    });

    test('行内代码', () {
      expect(isMarkdown('用 `flutter test` 跑测试'), isTrue);
    });

    test('删除线', () {
      expect(isMarkdown('这个功能~~已经废弃~~了'), isTrue);
    });

    test('链接', () {
      expect(isMarkdown('见 [官方文档](https://flutter.dev)'), isTrue);
    });

    test('图片', () {
      expect(isMarkdown('![截图](https://image.coolapk.com/a.png)'), isTrue);
    });

    test('加粗（** 与 __ 两种写法）', () {
      expect(isMarkdown('这点**非常重要**'), isTrue);
      expect(isMarkdown('这点__非常重要__'), isTrue);
    });

    test('服务端把 md 链接的 URL 换成了 <a> 标签', () {
      expect(
        isMarkdown('[文字](<a href="https://a.com">查看链接</a>)'),
        isTrue,
      );
    });
  });

  group('isMarkdown —— 弱特征：要累计 2 个以上', () {
    test('只有一条无序列表 → 不算 md（避免误报）', () {
      expect(isMarkdown('- 只有一个列表项而已'), isFalse);
    });

    test('两条无序列表 → 算 md', () {
      expect(isMarkdown('- 第一项\n- 第二项'), isTrue);
    });

    test('有序列表 + 引用 → 2 分，算 md', () {
      expect(isMarkdown('1. 第一步\n> 引用一下'), isTrue);
    });

    test('只有分隔线 → 不算 md', () {
      expect(isMarkdown('正文\n\n---\n\n正文'), isFalse);
    });

    test('表格行 >= 2 → 算 md', () {
      expect(isMarkdown('| 参数 | 值 |\n| --- | --- |\n| 屏幕 | 6.7 |'), isTrue);
    });
  });

  group('isMarkdown —— 边界与误报防护', () {
    test('null / 空 / 太短 → false', () {
      expect(isMarkdown(null), isFalse);
      expect(isMarkdown(''), isFalse);
      expect(isMarkdown('   '), isFalse);
      expect(isMarkdown('abc'), isFalse); // 少于 4 字符
    });

    test('普通中文正文 → false', () {
      expect(
        isMarkdown('今天天气不错，出去走走吧。顺便试试这个新手机的手感如何。'),
        isFalse,
      );
    });

    test('酷安话题 #[xxx]# 要剔除，不能因为 # 就判成 md', () {
      // 单个话题：剔除后没有别的特征 → 不是 md
      expect(isMarkdown('#[一加13]# 这机器手感不错，续航也还行。'), isFalse);
      // 两个话题：同样应剔除后判断
      expect(isMarkdown('#[一加13]# 和 #[ColorOS15]# 都挺好用的，推荐。'), isFalse);
    });

    test('话题剔除后仍有强特征 → 仍是 md', () {
      expect(isMarkdown('#[一加13]# 看这里 **重点**'), isTrue);
    });

    test('美元符号等普通符号不该误判', () {
      expect(isMarkdown('价格是 199*3 = 597 元左右，性价比还可以。'), isFalse);
    });
  });

  group('cleanUrl —— 去掉被吞进 URL 的尾随标点', () {
    test('未配对的右括号要去掉', () {
      expect(cleanUrl('https://a.com/b)'), 'https://a.com/b');
    });

    test('配对的括号要保留', () {
      expect(cleanUrl('https://a.com/b_(c)'), 'https://a.com/b_(c)');
    });

    test('尾部句读标点要去掉', () {
      expect(cleanUrl('https://a.com/b.'), 'https://a.com/b');
      expect(cleanUrl('https://a.com/b,;:!?'), 'https://a.com/b');
    });

    test('只剥 ASCII 句读，中文标点不动（与 Android 一致）', () {
      // Android 的 cleanUrl 只在 ".,;:!?" 里剥，不处理全角标点。
      // 这是**刻意对齐**，不是遗漏。
      expect(cleanUrl('https://a.com/b。'), 'https://a.com/b。');
      expect(cleanUrl('https://a.com/b，'), 'https://a.com/b，');
    });

    test('两端空白要去掉', () {
      expect(cleanUrl('  https://a.com/b  '), 'https://a.com/b');
    });

    test('正常的 URL 不受影响', () {
      expect(cleanUrl('https://flutter.dev/docs'), 'https://flutter.dev/docs');
    });
  });

  group('decodeEntities —— HTML 实体解码', () {
    test('常见命名实体', () {
      expect(decodeEntities('&lt;div&gt;'), '<div>');
      expect(decodeEntities('a &amp; b'), 'a & b');
      expect(decodeEntities('&quot;引号&quot;'), '"引号"');
    });

    test('引用块的 &gt; 要还原，否则 md 引用块渲染不出来', () {
      expect(decodeEntities('&gt; 这是引用'), '> 这是引用');
    });

    test('十进制与十六进制数字实体', () {
      expect(decodeEntities('&#65;&#66;'), 'AB');
      expect(decodeEntities('&#x41;&#x42;'), 'AB');
    });

    test('不认识的实体保持原样', () {
      expect(decodeEntities('&notarealentity;'), '&notarealentity;');
    });

    test('没有 & 时原样返回', () {
      expect(decodeEntities('普通文本'), '普通文本');
    });
  });

  group('preprocess —— 服务端 HTML 转成标准 Markdown', () {
    test('<br> 变成换行', () {
      expect(preprocess('第一行<br>第二行'), '第一行\n第二行');
      expect(preprocess('第一行<br/>第二行'), '第一行\n第二行');
    });

    test('<a href> 变成 md 链接', () {
      expect(
        preprocess('<a href="https://a.com">点我</a>'),
        '[点我](https://a.com)',
      );
    });

    test('链接文字就是 URL 时，尾随标点一并去掉', () {
      expect(
        preprocess('<a href="https://a.com/b.">https://a.com/b.</a>'),
        '[https://a.com/b](https://a.com/b)',
      );
    });

    test('行内 HTML 标签映射成 md 语法', () {
      expect(preprocess('<b>粗</b>'), '**粗**');
      expect(preprocess('<strong>粗</strong>'), '**粗**');
      expect(preprocess('<i>斜</i>'), '*斜*');
      expect(preprocess('<em>斜</em>'), '*斜*');
      expect(preprocess('<s>删</s>'), '~~删~~');
      expect(preprocess('<del>删</del>'), '~~删~~');
    });

    test('嵌套链接降级还原成作者写的 [文字](url)', () {
      expect(
        preprocess('[外层](<a href="https://a.com">查看链接</a>)'),
        '[外层](https://a.com)',
      );
    });
  });

  group('toHtml —— 转成 HTML 后交给 HtmlWidget', () {
    test('标题渲染成 h 标签', () {
      expect(toHtml('# 标题'), contains('<h1>'));
    });

    test('加粗渲染成 strong', () {
      expect(toHtml('**粗**'), contains('<strong>'));
    });

    test('删除线（GFL 扩展）渲染成 del', () {
      expect(toHtml('~~删~~'), contains('<del>'));
    });

    test('表格（GFL 扩展）渲染成 table', () {
      final html = toHtml('| a | b |\n| --- | --- |\n| 1 | 2 |');
      expect(html, contains('<table>'));
      expect(html, contains('<th>'));
      expect(html, contains('<td>'));
    });

    test('代码围栏渲染成 pre/code，供一键复制使用', () {
      final html = toHtml('```\nvoid main() {}\n```');
      expect(html, contains('<pre>'));
      expect(html, contains('<code>'));
    });

    test('普通文本也会被包成段落，不会报错', () {
      expect(toHtml('就是一段普通的话'), contains('<p>'));
    });
  });
}

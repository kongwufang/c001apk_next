// ★ 只 import **纯逻辑**文件（零依赖）——
//   之前 import `share_link.dart` 会连带拉进 `utils.dart` 背后的一整片
//   插件/页面依赖图，实测会**卡死 CI 的 `flutter test`**。
import 'package:c001apk_flutter/utils/coolapk_link.dart';
import 'package:flutter_test/flutter_test.dart';

/// 剪贴板分享链接识别（`ShareLinkPrompter.findCoolapkLink`）的测试。
///
/// ## 为什么值得测
/// 这段逻辑是从 Android `util/NetWorkUtil.findCoolapkLink` **逐条照抄**的，
/// 里面有一堆**看起来可以「优化」、实际一改就错**的口径：
///
/// | 口径 | 为什么不能改 |
/// |---|---|
/// | `scheme` / `host` **大小写敏感** | 那边 `openLink` 是大小写敏感的字符串替换，<br>`HTTPS://WWW.COOLAPK.COM/feed/1` 它一样打不开；<br>这里宽容地认下来，只会让用户点「确认」后换回一句「不支持的链接」 |
/// | **只有域名首页不算**分享链接 | `path.length > 1`；`https://www.coolapk.com` 空路径不是分享 |
/// | 先扫带 scheme 的子串，**再**退回按空白切词找裸域名 | 微信/QQ 分享文案把链接和说明黏在一起；<br>而有些 App 复制时会把 `https://` 吃掉 |
/// | 要削**尾部中文标点** | 句末标点跟着链接一起被复制过来是常态（`。，、）】`） |
/// | 长度上限 4096 | 避免拿一大段文章去跑正则 |
void main() {
  group('findCoolapkLink —— 微信/QQ 分享文案（链接和说明黏在一起）', () {
    test('前面有中文说明，取到链接', () {
      expect(
        findCoolapkLink(
            '分享自酷安https://www.coolapk.com/feed/1?s=abc'),
        'https://www.coolapk.com/feed/1?s=abc',
      );
    });

    test('中文句号跟着一起复制过来，要削掉', () {
      expect(
        findCoolapkLink('看这个。https://www.coolapk.com/feed/1'),
        'https://www.coolapk.com/feed/1',
      );
      expect(
        findCoolapkLink('https://www.coolapk.com/feed/1。'),
        'https://www.coolapk.com/feed/1',
      );
      expect(
        findCoolapkLink('（https://www.coolapk.com/feed/1）'),
        'https://www.coolapk.com/feed/1',
      );
    });

    test('尾部英文标点也要削', () {
      expect(
        findCoolapkLink('https://www.coolapk.com/feed/1.'),
        'https://www.coolapk.com/feed/1',
      );
      expect(
        findCoolapkLink('https://www.coolapk.com/feed/1!'),
        'https://www.coolapk.com/feed/1',
      );
    });
  });

  group('findCoolapkLink —— 裸域名（有些 App 复制时吃掉 https://）', () {
    test('按空白切词能找到', () {
      expect(
        findCoolapkLink('www.coolapk.com/feed/1'),
        'www.coolapk.com/feed/1',
      );
      expect(
        findCoolapkLink('看看 www.coolapk.com/feed/1 这个'),
        'www.coolapk.com/feed/1',
      );
    });

    test('裸域名也要削尾部标点', () {
      expect(
        findCoolapkLink('www.coolapk.com/feed/1。'),
        'www.coolapk.com/feed/1',
      );
    });

    test('没有路径的裸域名不算', () {
      // 代码里要求 path 非空
      expect(findCoolapkLink('www.coolapk.com'), isNull);
      expect(findCoolapkLink('www.coolapk.com/'), isNull);
    });
  });

  group('findCoolapkLink —— 域名白名单', () {
    test('白名单内的域名都认', () {
      for (final host in [
        'coolapk.com',
        'www.coolapk.com',
        'm.coolapk.com',
        'coolapk1s.com',
        'www.coolapk1s.com',
        'm.coolapk1s.com',
      ]) {
        expect(
          findCoolapkLink('https://$host/feed/1'),
          'https://$host/feed/1',
          reason: '应该认 $host',
        );
      }
    });

    test('白名单外的域名不认', () {
      expect(findCoolapkLink('https://www.baidu.com/feed/1'),
          isNull);
      expect(findCoolapkLink('https://coolapk.com.evil.io/x'),
          isNull);
    });

    test('★ 只有域名首页不算分享链接（path.length > 1）', () {
      expect(findCoolapkLink('https://www.coolapk.com'),
          isNull);
      expect(findCoolapkLink('https://www.coolapk.com/'),
          isNull);
      expect(findCoolapkLink('https://www.coolapk.com/feed'),
          'https://www.coolapk.com/feed');
    });
  });

  group('findCoolapkLink —— coolmarket:// scheme', () {
    test('白名单 host 认', () {
      expect(
        findCoolapkLink(
            'coolmarket://www.coolapk.com/feed/74194931?s=abc'),
        'coolmarket://www.coolapk.com/feed/74194931?s=abc',
      );
      expect(
        findCoolapkLink('coolmarket://feed/1'),
        'coolmarket://feed/1',
      );
    });

    test('非白名单 host 不认', () {
      expect(findCoolapkLink('coolmarket://evil.com/x'),
          isNull);
    });
  });

  group('findCoolapkLink —— ★ 大小写敏感（与 Android openLink 的能力边界一致）',
      () {
    test('大写 scheme 不认', () {
      // 故意不宽容：`Utils.onOpenLink` 是大小写敏感的字符串替换，
      // 认下来只会让用户点「确认」后换回一句「不支持的链接」
      expect(
        findCoolapkLink('HTTPS://WWW.COOLAPK.COM/feed/1'),
        isNull,
      );
    });

    test('大写 host 不认', () {
      expect(
        findCoolapkLink('https://WWW.COOLAPK.COM/feed/1'),
        isNull,
      );
    });
  });

  group('findCoolapkLink —— 不该命中的输入', () {
    test('空串 / 纯文本 / 纯空白', () {
      expect(findCoolapkLink(''), isNull);
      expect(findCoolapkLink('   '), isNull);
      expect(findCoolapkLink('今天天气不错'), isNull);
      expect(findCoolapkLink('https://'), isNull);
    });

    test('超过 4096 字符直接放弃', () {
      final long = '看这个 https://www.coolapk.com/feed/1 ' + List.filled(5000, 'x').join();
      expect(findCoolapkLink(long), isNull);
    });

    test('4096 以内仍然认', () {
      final ok = 'https://www.coolapk.com/feed/1 ' + List.filled(3000, 'x').join();
      expect(findCoolapkLink(ok),
          'https://www.coolapk.com/feed/1');
    });
  });
}

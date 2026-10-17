import 'dart:convert';
import 'dart:io';

import 'package:c001apk_flutter/logic/model/feed/feed_video.dart';
import 'package:flutter_test/flutter_test.dart';

/// 视频槽的提取逻辑测试。
///
/// ## 样本来源
/// `test/fixtures/video_feed.json` 是从**真实抓包**里裁出来的 8 条视频动态
/// （原样本 `_rev/card_scan_out/06_V9_HOME_TAB_SHIPIN.json`，63.5 KB，
/// 就是 Android `FeedVideo.kt` 注释里说的那 19 条样本中的一批）。
///
/// 覆盖面正好是 Android 注释描述的那三类：
/// | # | provider | 情况 |
/// |---|---|---|
/// | 0 / 7 | `coolapkVideo` | `"0"` 就是**能直接播的 mp4** |
/// | 1~6 | `weiboDirect250924` | `media_url` 只是**分享页地址**，要解析 |
///
/// ## 这份测试在守什么
/// 视频字段是从 `raw` 按**别名链**取的，最容易出的错是「换了个 provider 就取不到」。
/// 所以断言的是**具体值**（而不是「不为空」），这样别名链任何一环错位都会红。
void main() {
  late List<Map<String, dynamic>> samples;

  setUpAll(() {
    final text = File('test/fixtures/video_feed.json').readAsStringSync();
    samples = (jsonDecode(text) as List).cast<Map<String, dynamic>>();
  });

  FeedVideo? parse(int i) {
    final x = samples[i];
    return FeedVideo.from(
      x,
      feedType: x['feedType'] as String?,
      feedTypeName: x['feedTypeName'] as String?,
    );
  }

  group('真实样本：类型判定', () {
    test('8 条全部被认成视频', () {
      for (var i = 0; i < samples.length; i++) {
        expect(parse(i), isNotNull, reason: '第 $i 条应是视频');
      }
    });

    test('非视频数据返回 null', () {
      expect(FeedVideo.from({'message': '普通动态', 'pic': 'a.jpg'}), isNull);
      expect(FeedVideo.from(null), isNull);
    });

    test('只靠 feedType = "video" 也能认出来（media_type 缺失时）', () {
      expect(FeedVideo.from({'feedType': 'video'}, feedType: 'video'), isNotNull);
    });

    test('只靠 feedTypeName = "视频" 也能认出来', () {
      expect(FeedVideo.from({'x': 1}, feedTypeName: '视频'), isNotNull);
    });
  });

  group('真实样本：pic / picArr 实测为空', () {
    // Android 注释里最关键的一条口径：
    //   > `pic` / `picArr` 实测全为空，封面只在 `media_info.cover` 里
    // 这正是「必须单独做视频槽」的原因。这条断言把这个事实钉住 ——
    // 哪天服务端开始给 pic 了，这里会红，提醒去重新评估两个槽位的关系。
    test('8 条视频的 pic / picArr 都是空的', () {
      for (var i = 0; i < samples.length; i++) {
        final pic = samples[i]['pic'];
        final picArr = samples[i]['picArr'];
        expect(pic == null || pic == '', isTrue, reason: '第 $i 条 pic 应为空，实际 $pic');
        expect(picArr == null || (picArr is List && picArr.isEmpty), isTrue,
            reason: '第 $i 条 picArr 应为空');
      }
    });

    test('但封面取得到（来自 media_info.cover）', () {
      for (var i = 0; i < samples.length; i++) {
        expect(parse(i)!.poster, isNotEmpty, reason: '第 $i 条应取到封面');
      }
    });
  });

  group('真实样本：直链 provider（coolapkVideo）', () {
    test('#0 → 直接拿到 mp4，时长 80992ms = 1:20，比例 1280/720', () {
      final v = parse(0)!;
      expect(v.provider, 'coolapkVideo');
      expect(v.url, startsWith('https://'));
      expect(v.url, contains('.mp4'));
      // 有地址就不需要再解析
      expect(v.resolvable, isFalse);
      // 80992ms → 80s → 1:20
      expect(v.duration, '1:20');
      // 1280x720
      expect(v.aspectRatio, closeTo(1280 / 720, 0.001));
    });

    test('#7 → 竖屏 720x1280，时长 14064ms = 0:14', () {
      final v = parse(7)!;
      expect(v.provider, 'coolapkVideo');
      expect(v.url, contains('.mp4'));
      expect(v.duration, '0:14');
      expect(v.aspectRatio, closeTo(720 / 1280, 0.001));
    });
  });

  group('真实样本：需解析的 provider（weibo）', () {
    test('#1 → 分享页地址不算播放地址，但带解析参数', () {
      final v = parse(1)!;
      expect(v.provider, 'weiboDirect250924');
      // ★ 关键：media_url 是 https://weibo.com/... 这种分享页，
      //   不带 .mp4/.m3u8/.m4v 后缀 → 必须当作「没有播放地址」
      expect(v.url, isEmpty);
      // 但有 requestParams，点一下能去解析
      expect(v.resolvable, isTrue);
      expect(v.requestParams, isNotEmpty);
      // 305643ms → 305s → 5:05
      expect(v.duration, '5:05');
      // 服务端没给宽高 → 缺省 16:9
      expect(v.aspectRatio, closeTo(16 / 9, 0.001));
    });

    test('#2 → 95574ms = 1:35', () {
      expect(parse(2)!.duration, '1:35');
    });
  });

  group('withResolved：吃掉 /v6/player/getUrl 的返回', () {
    test('成功形状 {"data":{"urlList":[...]}} → 补上地址', () {
      final v = parse(1)!;
      final resolved = v.withResolved({
        'urlList': ['https://f.video.weibocdn.com/abc.mp4?x=1'],
      });
      expect(resolved.url, 'https://f.video.weibocdn.com/abc.mp4?x=1');
      // 其余字段原样带过来
      expect(resolved.poster, v.poster);
      expect(resolved.duration, v.duration);
      expect(resolved.requestParams, v.requestParams);
    });

    test('失败形状 {} / null / 空数组 → 原样返回，绝不抛异常', () {
      final v = parse(1)!;
      // Android 原注释：解析不出来时服务端给的是 `{"data":[]}`（数组），
      // 所以这里必须自己判形状，形状不对就原样返回。
      for (final bad in <Map<String, dynamic>?>[
        null,
        {},
        {'urlList': <String>[]},
        {'url_list': <String>[]},
        {'urlList': 'not-a-list'},
      ]) {
        expect(v.withResolved(bad).url, isEmpty, reason: '$bad 不该解析出地址');
      }
    });

    test('url_list 下划线别名也认', () {
      final v = parse(1)!.withResolved({
        'url_list': ['https://x/y.m4v'],
      });
      expect(v.url, 'https://x/y.m4v');
    });

    test('http 地址被换成 https', () {
      final v = parse(1)!.withResolved({
        'urlList': ['http://x/y.mp4'],
      });
      expect(v.url, 'https://x/y.mp4');
    });
  });

  group('时长格式化（formatDuration 的几条分支）', () {
    test('服务端给成品 "1:23" 就原样用', () {
      final v = FeedVideo.from({
        'media_type': 2,
        'video_duration': '1:23',
      });
      expect(v!.duration, '1:23');
    });

    test('纯数字按秒算：95 → 1:35', () {
      final v = FeedVideo.from({'media_type': 2, 'video_duration': '95'});
      expect(v!.duration, '1:35');
    });

    test('>= 1000 的数字按毫秒算：80992 → 1:20', () {
      final v = FeedVideo.from({'media_type': 2, 'video_duration': '80992'});
      expect(v!.duration, '1:20');
    });

    test('超过一小时给 h:mm:ss', () {
      final v = FeedVideo.from({'media_type': 2, 'video_duration': '3725000'});
      expect(v!.duration, '1:02:05');
    });

    test('0 / 负数 / 非数字 → 空串（不显示角标）', () {
      for (final bad in ['0', '-5', 'abc']) {
        final v = FeedVideo.from({'media_type': 2, 'video_duration': bad});
        expect(v!.duration, isEmpty, reason: '$bad 不该给出时长');
      }
    });
  });
}

import 'dart:convert';
import 'dart:io';

import 'package:c001apk_flutter/logic/model/feed/feed_extra.dart';
import 'package:flutter_test/flutter_test.dart';

/// 附加信息槽（点评评分 / 二手）的提取逻辑测试。
///
/// ## 样本来源
/// `test/fixtures/feed_extra.json` 两条，都是从**真实抓包**里原样裁出来的：
/// | 文件 | 条目 | 类型 |
/// |---|---|---|
/// | `07_V11_HOME_NEW.json` | **feed 74276786** | 点评评分 |
/// | `30_feed_digestList.json` | **feed 74276825** | 二手 |
///
/// 这两个 id **正是 Android `util/FeedExtra.kt` 注释里点名的样本**
/// （「样本（feed 74276786）」/「样本（feed 74276825）」）——
/// 说明两边用的是同一批数据，断言的值可以直接对照 Android 的口径。
///
/// 顺带一个交叉验证：全量扫 `_rev` 下的抓包，
/// `ershou_info` 非空 **23 条**、`extra_url`+`extra_title` 非空 **24 条** ——
/// 与 Android 注释里写的那两个数字（23 / 24）**完全一致**。
void main() {
  late List<Map<String, dynamic>> samples;

  setUpAll(() {
    final text = File('test/fixtures/feed_extra.json').readAsStringSync();
    samples = (jsonDecode(text) as List).cast<Map<String, dynamic>>();
  });

  group('点评评分（feed 74276786）', () {
    late FeedExtra extra;

    setUp(() => extra = FeedExtra.from(samples[0])!);

    test('解析出 rating，且没有 ershou', () {
      expect(extra.rating, isNotNull);
      expect(extra.ershou, isNull);
      expect(extra.isEmpty, isFalse);
    });

    test('star = 5（五星制）、score = 10（十分制）', () {
      expect(extra.rating!.star, '5');
      expect(extra.rating!.score, '10');
    });

    test('抬头文案 = "★ 5  10 分"（两个空格连）', () {
      // Android `bindRating`：
      //   parts += "★ ${rating.star}" / parts += "${rating.score} 分"
      //   header = parts.joinToString("  ")   ← 两个空格
      expect(extra.rating!.header, '★ 5  10 分');
    });

    test('六个维度，名字与分值逐条对上', () {
      final items = extra.rating!.items;
      expect(items.length, 6);
      expect(
        items.map((e) => '${e.name}=${e.score}').toList(),
        ['续航=8', '影像=10', '性能=6', '屏幕=10', '外观质感=6', '性价比=6'],
      );
    });

    test('chip 文案 = "续航 8"', () {
      expect(extra.rating!.items.first.label, '续航 8');
    });

    test('抬头为空时兜底成「点评」', () {
      final r = FeedRating(star: '', score: '', items: const []);
      expect(r.header, '点评');
    });

    test('只有 star 没有 score 时只出星级', () {
      final r = FeedRating(star: '4', score: '', items: const []);
      expect(r.header, '★ 4');
    });

    test('只有孤零零的分数不算点评（返回 null）', () {
      // Android 注释：实测 `rank_score` 这种恒为 0 的字段到处都是
      expect(FeedExtra.from({'rank_score': '0'}), isNull);
      // 只有 rating_score 也不行 —— 判定看的是 star / rating_item_info
      expect(FeedExtra.from({'rating_score': '9'}), isNull);
    });
  });

  group('二手（feed 74276825）', () {
    late FeedErshou e;

    setUp(() => e = FeedExtra.from(samples[1])!.ershou!);

    test('解析出 ershou，且没有 rating', () {
      final extra = FeedExtra.from(samples[1])!;
      expect(extra.ershou, isNotNull);
      expect(extra.rating, isNull);
    });

    test('标题 = "手机 · 标准版"（product_config_data 是转义 JSON 串）', () {
      // ★ 关键：`product_config_data` 实测是**被转义的 JSON 字符串**
      //   `{"configTitle":"标准版"}`，直接当对象取会炸 ——
      //   所以实现里要能「对象本身 / 装着对象的 JSON 串」两种都吃。
      expect(e.title, '手机');
      expect(e.config, '标准版');
      expect(e.titleText, '手机 · 标准版');
    });

    test('价格 "190" → "¥190"', () {
      expect(e.price, '190');
      expect(e.priceText, '¥190');
    });

    test('价格为空 → "面议"', () {
      const empty = FeedErshou(
        title: 'x',
        price: '',
        config: '',
        area: '',
        storeType: '',
        logo: '',
        linkUrl: '',
        linkSource: '',
      );
      expect(empty.priceText, '面议');
      // 没有 config 时标题不加分隔符
      expect(empty.titleText, 'x');
    });

    test('地区 = "吉林 临江"（city != province，两个都要）', () {
      expect(e.area, '吉林 临江');
    });

    test('次要信息 = "吉林 临江 · 个人"', () {
      expect(e.metaText, '吉林 临江 · 个人');
    });

    test('city == province 时只留一个', () {
      final r = _ershouWith(province: '北京', city: '北京');
      expect(r.area, '北京');
    });

    test('city 为空时只留 province', () {
      final r = _ershouWith(province: '北京', city: '');
      expect(r.area, '北京');
    });

    test('logo 的 http 被换成 https', () {
      expect(e.logo, startsWith('https://'));
      expect(e.logo, isNot(startsWith('http://')));
    });

    test('来源 = "闲鱼"', () {
      expect(e.linkSource, '闲鱼');
      expect(e.linkUrl, contains('goofish.com'));
    });

    test('product_title 为空就不算二手（返回 null）', () {
      expect(FeedExtra.from({'ershou_info': {'product_price': '100'}}), isNull);
    });
  });

  group('整体', () {
    test('两块都没有 → null（整块隐藏，不留空占位）', () {
      expect(FeedExtra.from({'message': '普通动态'}), isNull);
      expect(FeedExtra.from(null), isNull);
    });

    test('商品清单不走这里（由 Datum 的 extra* 字段 + _extraUrl 负责）', () {
      // 只有 extra_title / extra_url / extra_pic 时不产出 FeedExtra，
      // 免得和 `_extraUrl()` 重复渲染同一块。
      expect(
        FeedExtra.from({
          'extra_title': '我的商品清单',
          'extra_url': '/goodsList/38916622',
          'extra_pic': 'https://x/y.jpg',
        }),
        isNull,
      );
    });
  });
}

/// 造一个只有 province/city 的 ershou 原始数据，走一遍真实解析路径
FeedErshou _ershouWith({required String province, required String city}) =>
    FeedExtra.from({
      'ershou_info': {
        'product_title': '手机',
        'province': province,
        'city': city,
      }
    })!
        .ershou!;

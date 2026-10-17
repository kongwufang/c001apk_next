import 'package:c001apk_flutter/logic/model/feed/datum.dart';
import 'package:c001apk_flutter/utils/stick_top.dart';
import 'package:flutter_test/flutter_test.dart';

/// 个人主页**置顶 / 取消置顶**的列表变换测试。
///
/// 这段逻辑照抄 Android `BaseAppViewModel.onPostStickTop`：
/// ```kotlin
/// // 本地先切换小标记并让置顶项排到最前，和主页的服务端顺序一致
/// val updated = list.map {
///     if (it.id == id) it.copy(isStickTop = if (isStickTop) 0 else 1) else it
/// }
/// val target = updated.firstOrNull { it.id == id }
/// dataList.postValue(if (!isStickTop && target != null) 置顶项排到最前 else updated)
/// ```
///
/// ## 为什么单独测它
/// 这条路径**没法目视验证**（要登录 + 真机点置顶 + 回列表看），
/// 但它包含三件容易写错的事：**翻面**、**前移**、**不丢项/不重复**。
Datum d(String id, {int? stick}) => Datum.fromJson({
      'id': id,
      'entityType': 'feed',
      'message': 'm$id',
      if (stick != null) 'isStickTop': stick,
    });

void main() {
  group('applyStickTop —— 置顶（wasStickTop = false）', () {
    test('标记置 1 且移到最前', () {
      final list = [d('a'), d('b'), d('c')];
      final out = applyStickTop(list, 'c', false);
      expect(out.map((e) => e.id).toList(), ['c', 'a', 'b']);
      expect(out.first.isStickTop, 1);
    });

    test('中间的项也能移到最前', () {
      final list = [d('a'), d('b'), d('c'), d('d')];
      final out = applyStickTop(list, 'b', false);
      expect(out.map((e) => e.id).toList(), ['b', 'a', 'c', 'd']);
    });

    test('本来就置顶的项，再置顶一次仍然只出现一次', () {
      final list = [d('a', stick: 1), d('b'), d('c')];
      final out = applyStickTop(list, 'a', false);
      expect(out.map((e) => e.id).toList(), ['a', 'b', 'c']);
      expect(out.where((e) => e.id == 'a').length, 1);
      expect(out.first.isStickTop, 1);
    });

    test('不丢项也不多项', () {
      final list = [d('a'), d('b'), d('c'), d('d'), d('e')];
      final out = applyStickTop(list, 'd', false);
      expect(out.length, list.length);
      expect(out.map((e) => e.id).toSet(), list.map((e) => e.id).toSet());
    });
  });

  group('applyStickTop —— 取消置顶（wasStickTop = true）', () {
    test('标记置 0，但**顺序不动**', () {
      final list = [d('a', stick: 1), d('b'), d('c')];
      final out = applyStickTop(list, 'a', true);
      expect(out.map((e) => e.id).toList(), ['a', 'b', 'c']);
      expect(out.first.isStickTop, 0);
    });

    test('取消中间项的置顶，顺序同样不动', () {
      final list = [d('a'), d('b', stick: 1), d('c')];
      final out = applyStickTop(list, 'b', true);
      expect(out.map((e) => e.id).toList(), ['a', 'b', 'c']);
      expect(out[1].isStickTop, 0);
    });
  });

  group('applyStickTop —— 边界', () {
    test('id 不在列表里：原样返回（不崩）', () {
      final list = [d('a'), d('b')];
      final out = applyStickTop(list, 'zzz', false);
      expect(out.map((e) => e.id).toList(), ['a', 'b']);
      expect(out.length, 2);
    });

    test('空列表不崩', () {
      expect(applyStickTop(const [], 'a', false), isEmpty);
      expect(applyStickTop(const [], 'a', true), isEmpty);
    });

    test('单元素列表', () {
      final out = applyStickTop([d('a')], 'a', false);
      expect(out.length, 1);
      expect(out.first.isStickTop, 1);
    });

    test('其它项的 isStickTop 不受影响', () {
      final list = [d('a', stick: 1), d('b'), d('c', stick: 1)];
      final out = applyStickTop(list, 'b', false);
      // b 排到最前，a/c 的标记保持原样
      expect(out.map((e) => e.id).toList(), ['b', 'a', 'c']);
      expect(out.firstWhere((e) => e.id == 'a').isStickTop, 1);
      expect(out.firstWhere((e) => e.id == 'c').isStickTop, 1);
      expect(out.firstWhere((e) => e.id == 'b').isStickTop, 1);
    });
  });
}

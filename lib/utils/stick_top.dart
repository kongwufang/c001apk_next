/// 个人主页**置顶 / 取消置顶**的**纯列表变换**（无 IO、无 UI）。
///
/// 从 `CommonController.postStickTop` 里抽出来的 —— 那边负责调接口，
/// 拿到成功结果后调这里的 [applyStickTop] 改列表。
///
/// 抽出来的理由很实在：**这段逻辑现在没法目视验证**（要登录 + 真机点置顶 + 刷新），
/// 但它是最容易写错的一段（翻面 + 前移 + 不能丢项/重复），所以单独抽出来上单元测试。
///
/// ## 口径（照抄 Android `BaseAppViewModel.onPostStickTop`）
/// - 命中的那条：`isStickTop` **翻面**（`if (isStickTop) 0 else 1`）；
/// - **刚置顶**（`wasStickTop == false`）时把它**移到最前** ——
///   Android 注释：「和主页的服务端顺序一致」；
/// - **取消置顶**（`wasStickTop == true`）时**不动顺序**（服务端也不会动）。
library;

import '../logic/model/feed/datum.dart';

/// 对 [list] 应用一次「置顶状态翻转」，返回**新列表**（不改原列表）。
///
/// [wasStickTop] 是**操作前**的状态：
/// - `false` → 这是「置顶」操作 ⇒ 标记置 1，并把该项移到最前；
/// - `true`  → 这是「取消置顶」⇒ 标记置 0，**顺序不变**。
///
/// 找不到 [id] 时**原样返回**（服务端可能已经把这条删了）。
List<Datum> applyStickTop(
  List<Datum> list,
  dynamic id,
  bool wasStickTop,
) {
  // 先翻面
  final updated = list.map((d) {
    if (d.id == id) {
      d.isStickTop = wasStickTop ? 0 : 1;
    }
    return d;
  }).toList();

  // 取消置顶：顺序不动
  if (wasStickTop) return updated;

  // 刚置顶：移到最前
  Datum? target;
  for (final d in updated) {
    if (d.id == id) {
      target = d;
      break;
    }
  }
  if (target == null) return updated;

  return [
    target,
    ...updated.where((d) => d.id != id),
  ];
}

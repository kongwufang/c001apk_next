import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// 桌面端：**右键 = 长按**；没有长按的地方，右键 = **返回**。
///
/// 这是用户定的交互（2026-10-17）：
/// > 当按钮有长按事件的时候，点击右键就是长按；
/// > 如果没有长按事件，那么在任意页面上点击右键就触发返回。
///
/// ## 怎么做到「有长按就用长按，没有才返回」
/// 靠 **Flutter 手势竞技场的深浅优先级**：内层的识别器优先。
///
/// - 根部（本组件）挂一个 `onSecondaryTap: → 返回`；
/// - 任何有长按的控件，在它自己的 `InkWell` / `GestureDetector` 上
///   同时挂 `onSecondaryTap: <同一个回调>`（见 [secondaryTap] 的说明）。
///
/// 右键点在那种控件上时，**内层先赢**，根部的「返回」不会触发；
/// 点在别处时，没有内层竞争，根部的「返回」才生效。
///
/// ## 为什么不用 `Listener` 收原始指针
/// `Listener` 是原始事件、**总会**收到，没法「被内层消费掉」，
/// 于是会变成「既触发长按又返回」。手势竞技场才有优先级语义。
///
/// ## 移动端
/// 手机没有右键，`onSecondaryTap` 永远不触发 —— 对 Android 完全无影响。
class SecondaryTapBack extends StatelessWidget {
  const SecondaryTapBack({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      // translucent：参与命中测试但不遮挡子控件的点击
      behavior: HitTestBehavior.translucent,
      onSecondaryTap: () {
        // 没有可返回的页面时什么也不做（比如在首页右键）
        final nav = Get.key.currentState;
        if (nav != null && nav.canPop()) {
          Get.back();
        }
      },
      child: child,
    );
  }
}

/// 把「长按」映射到「右键」的辅助说明（不是函数，只是同一条规则的占位）。
///
/// 项目里的约定：凡是写了 `onLongPress: xxx` 的 `InkWell` / `GestureDetector`，
/// **紧挨着再写一行** `onSecondaryTap: xxx`（同一个回调）。
///
/// 例：
/// ```dart
/// InkWell(
///   onTap: openDetail,
///   onLongPress: copyText,
///   onSecondaryTap: copyText,   // ← 桌面端右键 = 长按
///   child: ...,
/// )
/// ```
///
/// 这样「右键」在**有长按的控件上**是长按，在**其余地方**是返回 —— 两者不冲突。
typedef SecondaryTapHandler = VoidCallback?;

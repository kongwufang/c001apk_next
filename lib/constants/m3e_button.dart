import 'package:flutter/material.dart';

/// **M3E 按钮的按下形位**（圆角收缩一档）。
///
/// 真源：Android `res/values/themes_m3e.xml` 把
/// `materialButtonStyle` 等 12 个按钮样式全部指向
/// `Widget.Material3Expressive.Button*`（material3expressive 库），
/// 形状是**状态表** `m3expressive_button_shape_state_list`：
/// 按下 → `containerShapePressed` / 选中 → `containerShapeChecked` /
/// 默认 → `containerShapeDefault`。
///
/// 实测档位：
/// | 尺寸 | 默认(square) | 按下(pressed) | 选中(round) |
/// |---|---|---|---|
/// | xsmall / small | Corner.Medium **12dp** | Corner.Small **8dp** | 12dp |
/// | medium | Corner.Large **16dp** | Corner.Medium **12dp** | 16dp |
/// | large / xlarge | Corner.ExtraLarge **28dp** | Corner.Large **16dp** | 28dp |
///
/// 也就是 —— **按下时圆角收缩一档**，松开回弹。这是 M3 Expressive 的标志性手感，
/// 与普通 M3 的「胶囊形按钮」完全不同（M3 是 `StadiumBorder`）。
///
/// ## ★ 与 Android 的差异（如实说明）
/// Android 那边是一个**弹簧动画**（`MotionScheme` 的 spring，阻尼比 1、刚度 400 那一档），
/// 圆角是**连续变化**的。Flutter 的按钮形状是 `WidgetStateProperty<OutlinedBorder>`，
/// 只会在状态切换时**瞬间**给出新值 —— 没有插值。
///
/// 所以这里拿到的是「按下变 12dp、松开回 16dp」的**跳变**，不是弹簧过渡。
/// 要做到连续变化，得自己写一个带 `AnimationController` 的按钮控件
/// （`AnimatedContainer` + 手动跟踪按下状态）—— 属于后续打磨项。
class M3EButtonShape {
  const M3EButtonShape._();

  // ── 三档圆角（dp）──
  static const double smallDefault = 12;
  static const double smallPressed = 8;
  static const double mediumDefault = 16;
  static const double mediumPressed = 12;
  static const double largeDefault = 28;
  static const double largePressed = 16;

  /// 造一个 `WidgetState` 驱动的形状：按下用 [pressed]，其余用 [normal]。
  static WidgetStateProperty<OutlinedBorder> shape({
    double normal = mediumDefault,
    double pressed = mediumPressed,
  }) {
    return WidgetStateProperty.resolveWith<OutlinedBorder>((states) {
      final radius = states.contains(WidgetState.pressed) ? pressed : normal;
      return RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(radius),
      );
    });
  }
}

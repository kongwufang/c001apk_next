/// Material 3 / M3E 设计令牌 —— **数值全部从 material AAR 取到，不靠记忆猜**。
///
/// 来源：`themes_m3e.xml` 注释写明基于 material **1.14.0**，取法为
/// ```
/// https://dl.google.com/dl/android/maven2/com/google/android/material/material/1.14.0/material-1.14.0.aar
/// 解压读 res/interpolator/*.xml 与 res/values/values.xml
/// ```
/// 复核工具：`_rev/win_ref/dump_all_easing.py`、`dump_m3_dimens.py`（可重跑）。
///
/// ⚠️ **注意版本差异**：`.codebuddy/handover-memory/project_transition_anim_experiment.md`
/// 记录 material **1.12.0** 里 `standard_decelerate` 与 `emphasized_decelerate`
/// **都是** `<decelerateInterpolator/>`（= `1-(1-t)²`）；而 **1.14.0 已改成真贝塞尔**。
/// 引用这些曲线时必须注明版本。
///
/// 注：`library;` 必须位于所有 import **之前**（否则报
/// `library_directive_not_first`）。
library;

import 'package:flutter/animation.dart';

/// M3 动效曲线（material 1.14.0 实测值）。
///
/// 对应 `res/interpolator/m3_sys_motion_easing_*.xml` 里的控制点。
class M3Easing {
  const M3Easing._();

  /// `m3_sys_motion_easing_standard` = Cubic(0.2, 0, 0, 1)
  ///
  /// ★ 这是**用户拍板的转场曲线**（2026-10-06 实测满意的「std」档）。
  static const standard = Cubic(0.2, 0.0, 0.0, 1.0);

  /// `m3_sys_motion_easing_standard_decelerate` = Cubic(0, 0, 0, 1)
  static const standardDecelerate = Cubic(0.0, 0.0, 0.0, 1.0);

  /// `m3_sys_motion_easing_standard_accelerate` = Cubic(0.3, 0, 1, 1)
  static const standardAccelerate = Cubic(0.3, 0.0, 1.0, 1.0);

  /// `m3_sys_motion_easing_emphasized_decelerate` = Cubic(0.1, 0.7, 0.1, 1)
  static const emphasizedDecelerate = Cubic(0.1, 0.7, 0.1, 1.0);

  /// `m3_sys_motion_easing_emphasized_accelerate` = Cubic(0.3, 0, 0.8, 0.2)
  static const emphasizedAccelerate = Cubic(0.3, 0.0, 0.8, 0.2);

  /// `m3_sys_motion_easing_linear` = Cubic(0, 0, 1, 1)
  static const linear = Cubic(0.0, 0.0, 1.0, 1.0);

  /// `m3_sys_motion_easing_legacy` = Cubic(0.4, 0, 0.2, 1)
  static const legacy = Cubic(0.4, 0.0, 0.2, 1.0);

  /// `m3_sys_motion_easing_legacy_decelerate` = Cubic(0, 0, 0.2, 1)
  static const legacyDecelerate = Cubic(0.0, 0.0, 0.2, 1.0);

  /// `m3_sys_motion_easing_legacy_accelerate` = Cubic(0.4, 0, 1, 1)
  static const legacyAccelerate = Cubic(0.4, 0.0, 1.0, 1.0);
}

/// 页面转场规格。
///
/// ★ **用户 2026-10-06 真机实测拍板**（见
/// `.codebuddy/handover-memory/project_transition_anim_experiment.md`）：
/// > 满意的水平转场配置 = **曲线 M3 standard（std）+ 类型水平滑动（slide）
/// > + 进入时长 400ms**（退出按既有规则 = 400-50 = **350ms**）
///
/// 四个槽位（slide 类型是纯位移）：
/// ```
/// rin（新页进入）  100% → 0
/// lout（旧页退场）   0   → -100%
/// lin（旧页回场） -100% → 0
/// rout（新页退出）   0   → 100%
/// ```
class M3Transition {
  const M3Transition._();

  /// 进入时长
  static const enterMs = 400;

  /// 退出时长（既有规则：进入 - 50ms）
  static const exitMs = 350;

  static const enterDuration = Duration(milliseconds: enterMs);
  static const exitDuration = Duration(milliseconds: exitMs);

  /// 进出用的曲线（用户选的是 std，所以两边同一条）
  static const enterCurve = M3Easing.standard;
  static const exitCurve = M3Easing.standard;
}

/// M3 形状圆角（`m3_sys_shape_corner_value_*`，material 1.14.0）。
///
/// 这些是**角色值**：组件按语义引用（小部件用 small、卡片用 medium …），
/// 不是随便挑的像素数。
class M3Shape {
  const M3Shape._();

  static const double none = 0;
  static const double extraSmall = 4;
  static const double small = 8;
  static const double medium = 12;
  static const double large = 16;
  static const double largeIncreased = 20;
  static const double extraLarge = 28;
  static const double extraLargeIncreased = 32;
  static const double extraExtraLarge = 48;
}

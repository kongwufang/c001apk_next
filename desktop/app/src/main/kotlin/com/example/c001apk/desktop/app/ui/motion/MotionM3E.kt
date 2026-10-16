package com.example.c001apk.desktop.app.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * Material 3 Expressive 运动规格 —— 数值取自 Android 端**真正生效**的资源。
 *
 * ## 来源
 * `res/anim/exp_slide_emph_300_{rin,lout,lin,rout}.xml`（默认档：`animType=slide`、
 * `animCurve=emph`、`animDuration=300`，见 `PrefManager`）：
 * ```xml
 * <!-- 生成物，勿手改：slide / emph / 进入 300ms（进出两层同曲线同时长） -->
 * <translate android:duration="300"
 *            android:fromXDelta="100%" android:toXDelta="0"
 *            android:interpolator="@interpolator/m3_sys_motion_easing_emphasized_decelerate" />
 * ```
 * 四个 slot 全是 300ms + 同一条插值器，位移各 100% 视口宽。
 *
 * ## 「同曲线同时长」不是随手写的
 * `tools/gen_transition_anim.py` 的注释解释了原因：若 `lout`/`rout` 用退出那套
 * （accelerate + 时长 −50ms），两层位移之和就不是常量，**中途会露出底色**。
 * 所以既有实现坚持四 slot 同参数。
 *
 * ## 插值器取值
 * `emphasized_decelerate` 在 material 1.14.0 是 `cubic-bezier(0.1, 0.7, 0.1, 1)`
 * （子代理核对了该版本的 tokens.xml 与插值器 XML）。
 * 注：官方文档写的是 `0.05,0.7,0.1,1`，与 1.14.0 的 tokens.xml 不一致 —— **以仓库实际依赖的版本为准**。
 */
object MotionM3E {

    /** `m3_sys_motion_easing_emphasized_decelerate` = cubic-bezier(0.1, 0.7, 0.1, 1) */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.1f, 0.7f, 0.1f, 1f)

    /** `m3_sys_motion_easing_emphasized_accelerate` = cubic-bezier(0.3, 0, 0.8, 0.2) */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.2f)

    /** `m3_sys_motion_easing_standard_decelerate` = cubic-bezier(0, 0, 0, 1) */
    val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** `m3_sys_motion_easing_standard_accelerate` = cubic-bezier(0.3, 0, 1, 1) */
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /**
     * `motionDurationMedium2` = 300ms —— 也是默认转场时长
     * （`PrefManager.animDuration` 默认 300）。
     */
    const val DURATION_MS = 300

    /**
     * 底部升起（回复 / 发布 / 相册选择器）。
     *
     * Android 端这些走 `anim_bottom_sheet_slide_up/down`：`config_mediumAnimTime`（300ms）
     * + `accelerate_interpolator`（**注意是系统的 accelerate，即 t²**，
     * 不是 Material 的 accelerate 曲线）。
     */
    const val SHEET_DURATION_MS = 300

    /** 系统的 `accelerate_interpolator` ≈ t² */
    val SystemAccelerate: Easing = Easing { t -> t * t }
}

/**
 * Android 端转场默认档的等价实现：**整屏水平推挤**。
 *
 * ```
 * push（进入下一屏）: 新屏 100%→0    旧屏 0→-100%
 * pop （返回上一屏）: 下层 -100%→0   正在关闭的屏 0→100%
 * ```
 * 四个方向都是 300ms + [MotionM3E.EmphasizedDecelerate]，两层**同时长同曲线**、
 * 位移各 100% 视口宽 —— 位移之和恒为 100%，所以中途不会露出底色。
 *
 * @param forward true = push（深入一层），false = pop（返回）
 */
fun m3Slide(forward: Boolean): ContentTransform {
    val spec = tween<IntOffset>(MotionM3E.DURATION_MS, easing = MotionM3E.EmphasizedDecelerate)
    return if (forward) {
        slideInHorizontally(spec, initialOffsetX = { w -> w }) togetherWith
            slideOutHorizontally(spec, targetOffsetX = { w -> -w })
    } else {
        slideInHorizontally(spec, initialOffsetX = { w -> -w }) togetherWith
            slideOutHorizontally(spec, targetOffsetX = { w -> w })
    }
}

/**
 * 底部升起（回复面板 / 发布页 / 相册选择器）。
 *
 * `anim_bottom_sheet_slide_up`：`fromYDelta=100%p` → 0，300ms，系统 accelerate。
 */
fun m3SheetUp(): ContentTransform {
    val spec = tween<IntOffset>(MotionM3E.SHEET_DURATION_MS, easing = MotionM3E.SystemAccelerate)
    // 注意 togetherWith 左侧要 EnterTransition、右侧要 ExitTransition，
    // 所以右侧用 ExitTransition.None（写成 EnterTransition.None 会编译不过）。
    return slideInVertically(spec, initialOffsetY = { h -> h }) togetherWith ExitTransition.None
}

/** 底部落下（关闭上面的面板） */
fun m3SheetDown(): ContentTransform {
    val spec = tween<IntOffset>(MotionM3E.SHEET_DURATION_MS, easing = MotionM3E.SystemAccelerate)
    return EnterTransition.None togetherWith slideOutVertically(spec, targetOffsetY = { h -> h })
}

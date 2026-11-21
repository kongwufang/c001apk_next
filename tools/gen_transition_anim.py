#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成「转场动画实验」用的资源矩阵。

背景：res/anim 是编译期资源，运行时只能从「预先烘好的一批」里挑，
所以把三个实验维度（曲线 / 类型 / 速度）的所有组合都烘成独立资源，
设置页再按当前选择查表拿到对应 resId。

维度：
  曲线 curve  : linear(M3 线性) / m2(Android fast_out_slow_in，沿用 M2 键) / std(M3 标准) / emph(M3 强调)
               / spring(rikkahub 的临界阻尼弹簧近似，ease-out-cubic)
  类型 type   : slide(水平滑动) / fade(淡入淡出) / parallax(视差滑动，rikkahub 同款)；
               none(无动画) 由代码直接传 0，不需要资源
  速度 speed  : 进入时长 100/200/300/400/500ms，退出固定 = 进入 - 50ms（对齐 M3 medium2/medium1）

曲线的两个硬事实（别再照名字猜，值是从 material AAR 里挖出来的）：
  a) m3_sys_motion_easing_{standard,emphasized}_decelerate 在 MDC 里**都是**
     <decelerateInterpolator/>（= 1-(1-t)^2）；两个 _accelerate 都是 <accelerateInterpolator/>
     （= t^2）。即 std 与 emph 实际同曲线，也没有 M3 规范里的两段式 emphasized。
  b) rikkahub 本身既没有曲线也没有时长：它用 Compose 默认的
     spring(dampingRatio = 1, stiffness = 400)（临界阻尼，m = 1），解析解
     x(t) = 1 - (1 + 20t)e^(-20t)：200ms 走 91%、300ms 走 98%、约 420ms 收到 1px 内。
     形状 ≈ ease-out-cubic，所以这里的 spring 档用 cubic-bezier(0.33, 1, 0.68, 1)
     （退出取反向的 ease-in-cubic），时长配 400ms 最贴。

parallax 的形状取自 rikkahub 的 NavDisplay.transitionSpec（单 Activity Compose）：
  进入  新页 translateX 100% -> 0；旧页 translateX 0 -> -50% + scale 1 -> 0.7 + alpha 1 -> 0
  退出  下层页 translateX -50% -> 0 + scale 0.7 -> 1 + alpha 0 -> 1；当前页 translateX 0 -> 100%

产物：
  app/src/main/res/interpolator/exp_spring_decelerate.xml / exp_spring_accelerate.xml
  app/src/main/res/anim/exp_<type>_<curve>_<enter>_<slot>.xml       共 250 个
  app/src/main/java/com/example/c001apk/util/TransitionAnimTable.kt  生成式查表

选定最终方案后：把赢家的曲线/时长固化回 right_in / left_out / left_in / right_out，
删掉 res/anim/exp_*.xml、res/interpolator/exp_*.xml、TransitionAnimTable.kt 和本脚本。
"""

import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANIM_DIR = os.path.join(ROOT, "app", "src", "main", "res", "anim")
INTERP_DIR = os.path.join(ROOT, "app", "src", "main", "res", "interpolator")
KT_DIR = os.path.join(ROOT, "app", "src", "main", "java", "com", "example", "c001apk", "util")

# 曲线 -> (进入插值器, 退出插值器)
CURVES = {
    "linear": ("@android:interpolator/linear", "@android:interpolator/linear"),
    "m2": ("@android:interpolator/fast_out_slow_in", "@android:interpolator/fast_out_slow_in"),
    "std": (
        "@interpolator/m3_sys_motion_easing_standard_decelerate",
        "@interpolator/m3_sys_motion_easing_standard_accelerate",
    ),
    "emph": (
        "@interpolator/m3_sys_motion_easing_emphasized_decelerate",
        "@interpolator/m3_sys_motion_easing_emphasized_accelerate",
    ),
    # rikkahub 的弹簧近似：进入 ease-out-cubic，退出反向 ease-in-cubic
    "spring": (
        "@interpolator/exp_spring_decelerate",
        "@interpolator/exp_spring_accelerate",
    ),
}

ENTER_DURATIONS = [100, 200, 300, 400, 500]
EXIT_GAP = 50          # 退出 = 进入 - 50
EXIT_MIN = 50

SLOTS = ["rin", "lin", "lout", "rout"]

# rikkahub 的 spring(dampingRatio=1, stiffness=400) 近似：
# 进入 ease-out-cubic，退出取反向的 ease-in-cubic
SPRING_DECELERATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- 转场动画实验用：ease-out-cubic cubic-bezier(0.33, 1, 0.68, 1)，
     近似 rikkahub 的 spring(dampingRatio = 1, stiffness = 400)（生成物，勿手改） -->
<pathInterpolator xmlns:android="http://schemas.android.com/apk/res/android"
    android:controlX1="0.33"
    android:controlX2="0.68"
    android:controlY1="1.0"
    android:controlY2="1.0" />
"""

SPRING_ACCELERATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- 转场动画实验用：ease-in-cubic cubic-bezier(0.32, 0, 0.67, 0)（生成物，勿手改） -->
<pathInterpolator xmlns:android="http://schemas.android.com/apk/res/android"
    android:controlX1="0.32"
    android:controlX2="0.67"
    android:controlY1="0.0"
    android:controlY2="0.0" />
"""

SLIDE = """<?xml version="1.0" encoding="utf-8"?>
<!-- 生成物，勿手改：slide / {curve} / 进入 {enter}ms（进出两层同曲线同时长） -->
<translate xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="{duration}"
    android:fromXDelta="{frm}"
    android:interpolator="{interp}"
    android:toXDelta="{to}" />
"""

FADE = """<?xml version="1.0" encoding="utf-8"?>
<!-- 生成物，勿手改：fade / {curve} / 进入 {enter}ms（退出 {exit}ms） -->
<alpha xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="{duration}"
    android:fromAlpha="{frm}"
    android:interpolator="{interp}"
    android:toAlpha="{to}" />
"""

# 视差滑动里「单独平移」的两端：新页整屏滑入 / 返回时当前页整屏滑出
PARALLAX_SLIDE = """<?xml version="1.0" encoding="utf-8"?>
<!-- 生成物，勿手改：parallax / {curve} / 进入 {enter}ms（退出 {exit}ms） -->
<translate xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="{duration}"
    android:fromXDelta="{frm}"
    android:interpolator="{interp}"
    android:toXDelta="{to}" />
"""

# 被压在下面的那一层：进入时退到左边半屏。**纯位移，不缩放、不淡出。**
#
# 为什么缩不得 / 淡不得（2026-10-06 实测，别再照搬 rikkahub 的 Compose 参数）：
#   `lout` 作用在**旧页 window surface** 上，动画把整个 surface（含 windowBackground、
#   含 DecorView 背景）一起变换，窗口之外没有任何 canvas 可以画——旧页缩到 0.7 之后，
#   四周露出的那一层在本窗口之外（更远页面，最底下是桌面），应用侧改 windowBackground
#   或 DecorView 背景都够不到。`alpha 1 -> 0` 更直接：旧页在被新页盖住之前就透出下层，
#   观感就是用户报的「上一页缩小、黑色露出来」。
#   要「缩小 + 四周垫底色」只能回内容级 + 透明窗口，而那条路已实测否决（一卡一卡），
#   所以这里退成纯位移。
#   位移是安全的：`rin` 的 100% -> 0 与 `lout` 的 0 -> -50% 只要同曲线同时长，
#   旧页右边沿 100% - 50%·t 始终 >= 新页左边沿 100% - 100%·t，两层永远搭接、不露空。
PARALLAX_LAYER_OUT = """<?xml version="1.0" encoding="utf-8"?>
<!-- 生成物，勿手改：parallax 旧页退场（左移半屏，纯位移）/ {curve} / {duration}ms -->
<translate xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="{duration}"
    android:fromXDelta="0"
    android:interpolator="{interp}"
    android:toXDelta="-50%" />
"""

# 返回时从上面那层底下归位：-50% -> 原样，同样**纯位移**
# （起止与 `PARALLAX_LAYER_OUT` 严格对称，否则 pop 结束帧与 push 起始帧对不上，
#  下层页会跳一下；同理不缩放不淡出，原因见上）
PARALLAX_LAYER_IN = """<?xml version="1.0" encoding="utf-8"?>
<!-- 生成物，勿手改：parallax 下层页归位（从左侧半屏归位，纯位移）/ {curve} / {duration}ms -->
<translate xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="{duration}"
    android:fromXDelta="-50%"
    android:interpolator="{interp}"
    android:toXDelta="0" />
"""

KT_HEADER = """package com.example.c001apk.util

import com.example.c001apk.R

/**
 * 转场动画资源查表：**生成物，勿手改**（见 tools/gen_transition_anim.py）。
 *
 * key = "<type>_<curve>_<enterMs>_<slot>"，slot 取 rin/lin/lout/rout；
 * none 类型与查不到的 key 都返回 0，交给调用方当作「无动画」。
 */
@Suppress("MaxLineLength")
internal object TransitionAnimTable {

    fun resolve(key: String): Int = when (key) {
"""

KT_FOOTER = """        else -> 0
    }
}
"""


def write(path, content):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


def main():
    # 清掉上一轮生成物，保证脚本可重复执行
    removed = 0
    for d, prefix in ((ANIM_DIR, "exp_"), (INTERP_DIR, "exp_")):
        if not os.path.isdir(d):
            continue
        for name in os.listdir(d):
            if name.startswith(prefix) and name.endswith(".xml"):
                os.remove(os.path.join(d, name))
                removed += 1

    os.makedirs(INTERP_DIR, exist_ok=True)
    write(os.path.join(INTERP_DIR, "exp_spring_decelerate.xml"), SPRING_DECELERATE)
    write(os.path.join(INTERP_DIR, "exp_spring_accelerate.xml"), SPRING_ACCELERATE)

    entries = []      # (key, resource_name)
    files = []        # (resource_name, content)

    for curve, (enter_interp, exit_interp) in CURVES.items():
        for enter in ENTER_DURATIONS:
            exit_ms = max(EXIT_MIN, enter - EXIT_GAP)

            # 水平滑动：四个方向各自需要一份资源
            #
            # 两层都是整屏 100% 位移，拼在一起要严丝合缝就必须满足 x_旧 + x_新 ≡ 100%。
            # 一旦退场吃的是 exit 那套（std 下是 accelerate，且时长 = 进入 - 50ms），
            # 位移之和在过程中就不是常量了：后半段旧页已经离场、新页还差一截，中间露出的
            # 正是垫在旧页 DecorView 上的底色——观感就是「先空一块挡住，内容才滑进来」。
            # 所以 lout / rout 跟对应的 rin / lin 吃同一条曲线、同一个时长。
            for slot in SLOTS:
                if slot == "rin":
                    frm, to, interp, dur = "100%", "0", enter_interp, enter
                elif slot == "lin":
                    frm, to, interp, dur = "-100%", "0", enter_interp, enter
                elif slot == "lout":
                    frm, to, interp, dur = "0", "-100%", enter_interp, enter
                else:
                    frm, to, interp, dur = "0", "100%", enter_interp, enter
                name = "exp_slide_%s_%d_%s" % (curve, enter, slot)
                files.append((name, SLIDE.format(
                    curve=curve, enter=enter, exit=exit_ms,
                    duration=dur, frm=frm, to=to, interp=interp)))
                entries.append(("slide_%s_%d_%s" % (curve, enter, slot), name))

            # 淡入淡出：左右方向观感相同，只烘「进 / 出」两份，代码侧复用
            fin = "exp_fade_%s_%d_fin" % (curve, enter)
            fout = "exp_fade_%s_%d_fout" % (curve, enter)
            files.append((fin, FADE.format(
                curve=curve, enter=enter, exit=exit_ms,
                duration=enter, frm="0.0", to="1.0", interp=enter_interp)))
            files.append((fout, FADE.format(
                curve=curve, enter=enter, exit=exit_ms,
                duration=exit_ms, frm="1.0", to="0.0", interp=exit_interp)))
            for slot in SLOTS:
                key = "fade_%s_%d_%s" % (curve, enter, slot)
                entries.append((key, fin if slot in ("rin", "lin") else fout))

            # 视差滑动：四个槽位形状各不相同，逐个烘（rikkahub 同款）
            #
            # 同步规则与 slide 同源：**配对的两层必须同曲线、同时长**，
            #   push 配对 = rin（新页，上层）+ lout（旧页，下层）
            #   pop  配对 = lin（下层页）+ rout（当前页，上层）
            # 这里的 lout / rout 原先吃的是 exit 那套（accelerate、进入 - 50ms），
            # 结果旧页已经退到一半、新页还没跟上，中间空出来的那块直接露桌面（用户报的
            # 「黑色露出」的一半成因）；统一到 enter 这套后位移之和才是常量。
            p_rin = "exp_parallax_%s_%d_rin" % (curve, enter)
            p_lout = "exp_parallax_%s_%d_lout" % (curve, enter)
            p_lin = "exp_parallax_%s_%d_lin" % (curve, enter)
            p_rout = "exp_parallax_%s_%d_rout" % (curve, enter)
            files.append((p_rin, PARALLAX_SLIDE.format(
                curve=curve, enter=enter, exit=exit_ms,
                duration=enter, frm="100%", to="0", interp=enter_interp)))
            files.append((p_lout, PARALLAX_LAYER_OUT.format(
                curve=curve, enter=enter, exit=exit_ms,
                duration=enter, interp=enter_interp)))
            files.append((p_lin, PARALLAX_LAYER_IN.format(
                curve=curve, enter=enter, exit=exit_ms,
                duration=enter, interp=enter_interp)))
            files.append((p_rout, PARALLAX_SLIDE.format(
                curve=curve, enter=enter, exit=exit_ms,
                duration=enter, frm="0", to="100%", interp=enter_interp)))
            for slot, name in (("rin", p_rin), ("lout", p_lout), ("lin", p_lin), ("rout", p_rout)):
                entries.append(("parallax_%s_%d_%s" % (curve, enter, slot), name))

    for name, content in files:
        write(os.path.join(ANIM_DIR, name + ".xml"), content)

    lines = [KT_HEADER]
    for key, name in entries:
        lines.append('        "%s" -> R.anim.%s\n' % (key, name))
    lines.append(KT_FOOTER)
    write(os.path.join(KT_DIR, "TransitionAnimTable.kt"), "".join(lines))

    print("removed=%d  anim=%d  entries=%d" % (removed, len(files), len(entries)))


if __name__ == "__main__":
    main()

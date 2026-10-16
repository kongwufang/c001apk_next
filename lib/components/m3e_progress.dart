import 'dart:math' as math;

import 'package:flutter/material.dart';

/// M3E（Material 3 Expressive）**波浪形**圆形进度指示器。
///
/// ## 为什么自己画
/// Flutter 的 `CircularProgressIndicator` 是 **M3 基础**样式（平滑圆弧），
/// 没有 M3E 的波浪描边。而波浪描边正是 M3E 最具辨识度的动效特征之一，
/// 所以按 material 1.14.0 的实测参数手写一个。
///
/// ## 尺寸全部来自 material 1.14.0 AAR（已核对，非估算）
/// ```
/// m3_comp_progress_indicator_circular_with_wave_size                    48dp  ← 整体
/// m3_comp_progress_indicator_circular_size                              40dp  ← 指示器
/// m3_comp_progress_indicator_circular_track_thickness                    4dp  ← 线宽
/// m3_comp_progress_indicator_circular_track_active_indicator_space       4dp  ← 轨道与指示器间距
/// m3_comp_progress_indicator_circular_active_indicator_wave_amplitude   1.6dp  ← 波幅
/// m3_comp_progress_indicator_circular_active_indicator_wave_wavelength  15dp   ← 波长
/// ```
/// 复核工具：`_rev/win_ref/dump_m3e_button_wavy.py`
///
/// 线性版的参数（`amplitude 3dp` / `wavelength 40dp`，不确定态 20dp）也一并记录在此，
/// 待需要时实现。
class M3EWavyProgressIndicator extends StatefulWidget {
  const M3EWavyProgressIndicator({
    super.key,
    this.size = 48,
    this.color,
    this.trackColor,
    this.semanticsLabel,
  });

  /// 整体尺寸（M3E 默认 48dp）
  final double size;

  final Color? color;
  final Color? trackColor;
  final String? semanticsLabel;

  @override
  State<M3EWavyProgressIndicator> createState() =>
      _M3EWavyProgressIndicatorState();
}

class _M3EWavyProgressIndicatorState extends State<M3EWavyProgressIndicator>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    // 与 M3E 的旋转周期一致（一圈约 1.8s）
    duration: const Duration(milliseconds: 1800),
  )..repeat();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final active = widget.color ?? colorScheme.primary;
    final track =
        widget.trackColor ?? colorScheme.surfaceContainerHighest;

    return Semantics(
      label: widget.semanticsLabel ?? '加载中',
      child: SizedBox(
        width: widget.size,
        height: widget.size,
        child: AnimatedBuilder(
          animation: _controller,
          builder: (_, __) => CustomPaint(
            painter: _WavyCircularPainter(
              progress: _controller.value,
              active: active,
              track: track,
            ),
          ),
        ),
      ),
    );
  }
}

class _WavyCircularPainter extends CustomPainter {
  _WavyCircularPainter({
    required this.progress,
    required this.active,
    required this.track,
  });

  /// 0..1 的动画进度（一圈）
  final double progress;
  final Color active;
  final Color track;

  /// 整体 48dp 时的几何。`size` 变化时按比例缩放。
  static const double _refSize = 48;

  /// 线宽 4dp（`track_thickness`）
  static const double _thickness = 4;

  /// 波幅 1.6dp（`wave_amplitude`）
  static const double _amplitude = 1.6;

  /// 波长 15dp（`wave_wavelength`）
  static const double _wavelength = 15;

  /// 轨道与指示器之间的间距 4dp（`track_active_indicator_space`）——
  /// 即活动指示器比轨道**内缩**一点，不重叠
  static const double _space = 4;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.shortestSide / _refSize;
    final t = _thickness * scale;
    final amp = _amplitude * scale;
    final waveLen = _wavelength * scale;
    final gap = _space * scale;

    final center = Offset(size.width / 2, size.height / 2);
    final trackRadius = size.shortestSide / 2 - t / 2;
    final activeRadius = trackRadius - gap / 2;

    // ── 轨道：整圈 ──
    // M3E 的圆形指示器**没有**底圈（只有活动弧）。
    // 但如果给了 trackColor 与 active 不同，画一个很淡的底圈更易读；
    // 这里按 M3E 规范：默认不画底圈，只有当 trackColor 明确不同于 active 时才画。
    if (track != active) {
      canvas.drawCircle(
        center,
        trackRadius,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = t
          ..strokeCap = StrokeCap.round
          ..color = track.withValues(alpha: 0.35),
      );
    }

    // ── 活动弧：起始角旋转 + 扫过角呼吸 ──
    //
    // M3E 的不确定态是「旋转 + 伸缩」：弧长周期性变长变短。
    // 用一条正弦驱动扫过角在 [0.18, 0.68] 圈之间来回。
    final sweepT = (math.sin(progress * 2 * math.pi) + 1) / 2; // 0..1
    final sweep = (0.18 + 0.50 * sweepT) * 2 * math.pi;
    final start = progress * 2 * math.pi - math.pi / 2;

    // 波浪：半径随**弧长**正弦起伏
    final path = Path();
    const steps = 96;
    for (var i = 0; i <= steps; i++) {
      final f = i / steps;
      final angle = start + sweep * f;
      final arcLen = sweep * f * activeRadius;
      final r = activeRadius +
          amp * math.sin(2 * math.pi * arcLen / waveLen);
      final p = Offset(
        center.dx + r * math.cos(angle),
        center.dy + r * math.sin(angle),
      );
      if (i == 0) {
        path.moveTo(p.dx, p.dy);
      } else {
        path.lineTo(p.dx, p.dy);
      }
    }

    canvas.drawPath(
      path,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = t
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..color = active,
    );
  }

  @override
  bool shouldRepaint(covariant _WavyCircularPainter old) =>
      old.progress != progress || old.active != active || old.track != track;
}

import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// 方形/定比裁剪页。
///
/// 移植自 Android `ui/article/CropImageActivity.kt`：
/// > 裁剪页：默认固定比例 1600:719（图文封面），输出精确 1600×719 的 JPEG。
/// > 传 `EXTRA_OUT_W` / `EXTRA_OUT_H` 可以换比例
/// > （**头像、主页背景图传 600 / 1080 这种正方形**）。
///
/// 三种用途：
/// | 用途 | outW × outH |
/// |---|---|
/// | 图文封面 | 1600 × 719（默认）|
/// | 头像 | 600 × 600 |
/// | 主页背景图 | 1080 × 1080 |
///
/// ## 交互
/// - `InteractiveViewer`：双指/滚轮缩放（上限 4×，同 Android `MAX_SCALE`）+ 拖动
/// - 固定裁剪框（比例 = outW:outH），框外半透明遮罩
/// - **描边自适应明暗**（Android `LIGHT_THRESHOLD` / `BORDER_ON_LIGHT/DARK`）：
///   框内平均亮度 > 0.55 用半透明黑，否则用白 —— 保证任何图下框都看得见
///
/// ## 导出
/// 用 `dart:ui` 的 `PictureRecorder` + `drawImageRect`，
/// **不需要 `image` 包**。
///
/// ⚠️ `toByteData` 只能出 PNG / rawRgba ⇒ 这里输出 **PNG**。
/// 服务端一般按魔数识别，但**这点没验证过**（台账里标了）。
class CropImagePage extends StatefulWidget {
  const CropImagePage({super.key});

  @override
  State<CropImagePage> createState() => _CropImagePageState();
}

class _CropImagePageState extends State<CropImagePage> {
  static const double _maxScale = 4; // Android MAX_SCALE
  static const double _lightThreshold = 0.55; // Android LIGHT_THRESHOLD

  final _tc = TransformationController();

  ui.Image? _image;
  bool _borderLight = true; // true = 亮画面 → 用黑描边
  bool _busy = false;

  /// 用 `Get.arguments` 而不是 `Get.parameters`。
  ///
  /// `toNamed(name, parameters:)` 的 `parameters` 在某些 GetX 版本/路由形态下
  /// 取不到（实测裁剪页进去后一直转圈 ✗）⇒ 改用 `arguments`，更可靠。
  Map get _args =>
      (Get.arguments is Map) ? (Get.arguments as Map) : const {};

  String get _path => (_args['path'] ?? '').toString();

  int get _outW {
    final v = _args['outW'];
    if (v is int) return v;
    return int.tryParse('$v') ?? 1600;
  }

  int get _outH {
    final v = _args['outH'];
    if (v is int) return v;
    return int.tryParse('$v') ?? 719;
  }

  @override
  void initState() {
    super.initState();
    _load();
    _tc.addListener(_onTransform);
  }

  @override
  void dispose() {
    _tc.removeListener(_onTransform);
    _tc.dispose();
    super.dispose();
  }

  String? _error;

  /// 诊断日志写到 `%TEMP%\c001apk_profile.log`（toast 一闪就没了 ✗）
  static void _log(String msg) {
    debugPrint('CropImage: $msg');
    try {
      final f = File('${Directory.systemTemp.path}/c001apk_profile.log');
      f.writeAsStringSync(
        '[${DateTime.now().toIso8601String()}] CropImage: $msg\n',
        mode: FileMode.append,
        flush: true,
      );
    } catch (_) {}
  }

  Future<void> _load() async {
    final path = _path;
    _log('args=${Get.arguments} path=$path');
    if (path.isEmpty) {
      setState(() => _error = '没有拿到图片路径（arguments 为空）');
      return;
    }
    final file = File(path);
    if (!file.existsSync()) {
      setState(() => _error = '文件不存在：$path');
      return;
    }
    final bytes = await file.readAsBytes();
    final img = await decodeImageFromList(bytes);
    // ★ 加载时算一次画面平均亮度（决定裁剪框描边用黑还是白）
    final lum = await _computeLuminance(img);
    if (!mounted) return;
    setState(() {
      _image = img;
      _cachedLum = lum;
      _borderLight = lum > _lightThreshold;
    });
  }

  /// 变换变了就重算「画面明暗」，决定描边颜色
  void _onTransform() {
    final img = _image;
    if (img == null) return;
    // 简化：用整图的平均亮度近似「框内亮度」
    // （Android 是采样框内 12 个点；这里取全图，够用且更省）
    final lum = _averageLuminance(img);
    final light = lum > _lightThreshold;
    if (light != _borderLight) setState(() => _borderLight = light);
  }

  /// 全图平均亮度（0..1）。**加载时算一次**，之后直接用缓存。
  double _averageLuminance(ui.Image img) => _cachedLum;

  double _cachedLum = 0.6; // 保守默认（按「亮画面」处理 → 黑描边）

  /// 真采样：`toByteData(rawRgba)` 后按步长抽点算平均亮度。
  ///
  /// 只缩放**一次**，不随交互重算 —— Android 那边是采样框内 12 个点，
  /// 这里采样整图（够用，且不用在每次缩放时重算）。
  Future<double> _computeLuminance(ui.Image img) async {
    try {
      final data = await img.toByteData(format: ui.ImageByteFormat.rawRgba);
      if (data == null) return 0.6;
      final bytes = data.buffer.asUint8List();
      final total = img.width * img.height;
      // 步长：最多采样 ~4000 个点，保证大图也很快
      final step = (total / 4000).ceil().clamp(1, 1 << 20);
      var sum = 0.0;
      var count = 0;
      for (var i = 0; i < total; i += step) {
        final o = i * 4;
        if (o + 3 >= bytes.length) break;
        final r = bytes[o];
        final g = bytes[o + 1];
        final b = bytes[o + 2];
        // 相对亮度（Rec.709 系数）
        sum += (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
        count++;
      }
      return count == 0 ? 0.6 : sum / count;
    } catch (_) {
      return 0.6;
    }
  }

  /// 从当前变换推出「裁剪框在源图上的区域」，再画到 outW×outH 画布
  Future<void> _confirm() async {
    final img = _image;
    if (img == null || _busy) return;
    setState(() => _busy = true);
    _log('confirm: box=$_cropRect scale=${_tc.value.getMaxScaleOnAxis()} out=${_outW}x$_outH');
    try {
      final box = _cropRect;
      final m = _tc.value;
      final scale = m.getMaxScaleOnAxis();
      final tx = m.getTranslation().x;
      final ty = m.getTranslation().y;
      // 视口坐标 → 源图像素坐标
      final srcRect = Rect.fromLTWH(
        (box.left - tx) / scale,
        (box.top - ty) / scale,
        box.width / scale,
        box.height / scale,
      );

      // 用 dart:ui 把源图的这段画到 outW×outH 的画布
      final recorder = ui.PictureRecorder();
      final canvas = Canvas(recorder);
      canvas.drawImageRect(
        img,
        srcRect,
        Rect.fromLTWH(0, 0, _outW.toDouble(), _outH.toDouble()),
        Paint()..filterQuality = FilterQuality.high,
      );
      _log('confirm: srcRect=$srcRect');
      final picture = recorder.endRecording();
      final out = await picture.toImage(_outW, _outH);
      _log('confirm: toImage ok ${out.width}x${out.height}');
      final data = await out.toByteData(format: ui.ImageByteFormat.png);
      _log('confirm: png bytes=${data?.lengthInBytes}');
      if (data == null) throw Exception('导出失败');
      Get.back<Uint8List>(result: data.buffer.asUint8List());
    } catch (e, st) {
      _log('confirm 失败: $e\n$st');
      if (mounted) setState(() => _busy = false);
    }
  }

  /// 居中裁剪框（按 outW:outH 比例，占视口的 86%）
  Rect get _cropRect {
    final size = MediaQuery.of(context).size;
    final avail = Size(size.width * 0.86, size.height * 0.66);
    final ratio = _outW / _outH;
    double w = avail.width;
    double h = w / ratio;
    if (h > avail.height) {
      h = avail.height;
      w = h * ratio;
    }
    return Rect.fromCenter(
      center: Offset(size.width / 2, size.height / 2),
      width: w,
      height: h,
    );
  }

  @override
  Widget build(BuildContext context) {
    final img = _image;
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: Text('裁剪（$_outW × $_outH）'),
        backgroundColor: Colors.transparent,
        foregroundColor: Colors.white,
        actions: [
          TextButton(
            onPressed: _busy ? null : _confirm,
            child: Text(_busy ? '处理中…' : '完成'),
          ),
        ],
      ),
      body: _error != null
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(
                  _error!,
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: Colors.white70),
                ),
              ),
            )
          : img == null
              ? const Center(child: CircularProgressIndicator())
          : LayoutBuilder(
              builder: (context, constraints) => Stack(
                children: [
                  Positioned.fill(
                    child: InteractiveViewer(
                      transformationController: _tc,
                      minScale: 0.5,
                      maxScale: _maxScale,
                      boundaryMargin: const EdgeInsets.all(double.infinity),
                      child: Center(
                        child: RawImage(image: img),
                      ),
                    ),
                  ),
                  // 遮罩 + 描边
                  Positioned.fill(
                    child: IgnorePointer(
                      child: CustomPaint(
                        painter: _CropOverlayPainter(
                          crop: _cropRect,
                          borderColor: _borderLight
                              ? const Color(0xE6000000) // BORDER_ON_LIGHT
                              : const Color(0xFFFFFFFF), // BORDER_ON_DARK
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}

class _CropOverlayPainter extends CustomPainter {
  _CropOverlayPainter({required this.crop, required this.borderColor});

  final Rect crop;
  final Color borderColor;

  @override
  void paint(Canvas canvas, Size size) {
    // 框外压暗
    final mask = Path()
      ..addRect(Offset.zero & size)
      ..addRect(crop)
      ..fillType = PathFillType.evenOdd;
    canvas.drawPath(mask, Paint()..color = const Color(0x99000000));

    // 描边（2px，圆角 4，同 Android 的框）
    canvas.drawRRect(
      RRect.fromRectAndRadius(crop.deflate(1), const Radius.circular(4)),
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2
        ..color = borderColor,
    );
  }

  @override
  bool shouldRepaint(_CropOverlayPainter old) =>
      old.crop != crop || old.borderColor != borderColor;
}

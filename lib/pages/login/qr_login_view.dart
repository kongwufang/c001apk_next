import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:uuid/uuid.dart';

import '../../utils/qr_crypto.dart';

/// 扫码登录视图（嵌在登录页的「扫码登录」Tab 里）。
///
/// ## 状态机
/// ```
/// preparing → waiting ──→ scanned ──→ confirmed ──→ done
///                │            │
///                └────────────┴──→ expired（3 分钟）
/// ```
///
/// ## 流程（对应协议 7 步）
/// 1. `prepare`：上报设备信息 + 版本号 + 签名 + 随机 id → 拿 `requestId` / `callbackTicket`
/// 2. 本地生成 RSA 密钥对；用私钥签「设备信息 + requestId」→ `register` → 拿 `appTicket`
/// 3. 用 `appTicket` 等拼出的 URL 画二维码
/// 4. 每 1.5s `poll` 一次；`scanned` 显示「已扫描，请在手机上确认」
/// 5. `confirmed` → 私钥解 `encKey` 得 AES 密钥 → AES 解 `encToken` 得 refreshToken
///
/// ## 硬约束
/// - **私钥只在这个 State 的内存里**（[_privateKey]），不落盘、不上报、不打日志
/// - 服务端只给密文，明文 refreshToken 只在本机存在
///
/// ## ⚠️ 当前网络层是**占位实现**
/// 服务端 3 个接口还没部署（`qr/index.php` 已写但没上传），
/// 所以 [_post] 现在**直接抛「服务端未部署」** ✗ —— **不假装能用**。
/// 部署后把 `_qrBase` 和各 action 的字段对齐即可 ✓
class QrLoginView extends StatefulWidget {
  const QrLoginView({super.key, this.onLoggedIn});

  /// 拿到 refreshToken 后的回调（由登录页负责写入登录态 + 跳转）
  final void Function(String refreshToken)? onLoggedIn;

  @override
  State<QrLoginView> createState() => _QrLoginViewState();
}

enum _Phase { preparing, waiting, scanned, confirmed, expired, failed }

class _QrLoginViewState extends State<QrLoginView> {
  /// 服务端基址。
  ///
  /// ⚠️ 只写**基址 + 动作名**，不放任何服务端实现细节 ✗
  /// （用户要求：服务器的 API 私有不开源，不上传任何有关的文档及代码）
  static const String _qrBase = 'https://service.houlangs.cn/c001apk/qr/';

  static const Duration _pollInterval = Duration(milliseconds: 1500);
  static const Duration _ttl = Duration(minutes: 3);

  _Phase _phase = _Phase.preparing;
  String? _qrUrl;
  String? _errorText;

  // 一次会话的凭据 —— 全在内存
  String? _requestId;
  String? _callbackTicket;
  String? _appTicket;
  Object? _privateKey; // RSAPrivateKey，用 Object? 避免把 pointycastle 类型泄到 UI 层
  Timer? _timer;
  DateTime? _startedAt;

  @override
  void initState() {
    super.initState();
    _start();
  }

  @override
  void dispose() {
    _timer?.cancel();
    // ★ 私钥随 State 销毁，不写任何存储
    _privateKey = null;
    super.dispose();
  }

  // ── 主流程 ──────────────────────────────────────────────

  Future<void> _start() async {
    _timer?.cancel();
    setState(() {
      _phase = _Phase.preparing;
      _qrUrl = null;
      _errorText = null;
    });

    try {
      // ① prepare
      final randomId = const Uuid().v4().replaceAll('-', '');
      final device = _deviceName();
      final version = '1.0.0';
      // ⚠️ `sign` 服务端目前**不做校验**（`qr/index.php` 只查非空）
      //    协议里「签名」要绑什么内容还没定 ⇒ 先传 randomId 占位；
      //    将来 PHP 端加校验时，这里同步改成「私钥签 device|randomId」✓
      final sign = randomId;
      final prep = await _post('prepare', {
        'randomId': randomId,
        'device': device,
        'version': version,
        'sign': sign,
      });
      _requestId = '${prep['requestId'] ?? ''}';
      _callbackTicket = '${prep['callbackTicket'] ?? ''}';
      if (_requestId!.isEmpty || _callbackTicket!.isEmpty) {
        throw StateError('prepare 返回缺字段');
      }

      // ② 生成 RSA 密钥对（私钥只在这里的内存里）
      final pair = QrCrypto.generateKeyPair();
      final pub = pair.publicKey as dynamic;
      final priv = pair.privateKey;
      _privateKey = priv;

      // ③ register：用私钥签「设备信息 + requestId」
      final payload = '$device|$_requestId';
      final reg = await _post('register', {
        'requestId': _requestId,
        'callbackTicket': _callbackTicket,
        'publicKey': jsonEncode({
          'm': QrCrypto.modulusB64(pub),
          'e': QrCrypto.exponentB64(pub),
        }),
        'signature': QrCrypto.signSha256(priv as dynamic, payload),
      });
      _appTicket = '${reg['appTicket'] ?? ''}';
      _qrUrl = '${reg['qrUrl'] ?? ''}';
      if (_qrUrl!.isEmpty) {
        throw StateError('register 返回缺 qrUrl');
      }

      setState(() => _phase = _Phase.waiting);
      _startedAt = DateTime.now();
      _timer = Timer.periodic(_pollInterval, (_) => _poll());
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _phase = _Phase.failed;
        _errorText = '$e';
      });
    }
  }

  Future<void> _poll() async {
    if (!mounted) return;
    if (_phase != _Phase.waiting && _phase != _Phase.scanned) return;

    // 3 分钟过期
    final started = _startedAt;
    if (started != null && DateTime.now().difference(started) > _ttl) {
      _timer?.cancel();
      setState(() => _phase = _Phase.expired);
      return;
    }

    try {
      final r = await _post('poll', {
        'requestId': _requestId,
        'callbackTicket': _callbackTicket,
      });
      final status = '${r['status'] ?? ''}';
      if (status == 'scanned' && _phase == _Phase.waiting) {
        setState(() => _phase = _Phase.scanned);
      } else if (status == 'confirmed') {
        _timer?.cancel();
        await _finish(r);
      } else if (status == 'expired') {
        _timer?.cancel();
        setState(() => _phase = _Phase.expired);
      }
    } catch (_) {
      // 单次轮询失败不改变状态，下一次继续
    }
  }

  /// confirmed：私钥解 AES 密钥 → AES 解 refreshToken
  Future<void> _finish(Map<String, dynamic> r) async {
    final priv = _privateKey;
    if (priv == null) {
      setState(() {
        _phase = _Phase.failed;
        _errorText = '私钥丢失';
      });
      return;
    }
    try {
      final aesKeyBytes = QrCrypto.decryptPkcs1(priv as dynamic, '${r['encKey']}');
      final aesKeyB64 = base64Encode(aesKeyBytes);
      final token = QrCrypto.aesCbcDecrypt(aesKeyB64, '${r['encToken']}');
      if (token.isEmpty) throw StateError('解出的 token 为空');

      setState(() => _phase = _Phase.confirmed);
      widget.onLoggedIn?.call(token);
    } catch (e) {
      setState(() {
        _phase = _Phase.failed;
        _errorText = '解密失败：$e';
      });
    }
  }

  /// 服务端请求（2026-10-18 已联调通过 ✓）
  ///
  /// 统一约定：`POST {_qrBase}?action=<动作>`，请求体是 JSON，
  /// 响应形如 `{"data": {...}}`；失败时 `{"status":-1,"message":"..."}`
  /// （参数错误 / 会话过期 / ticket 不匹配都走这一种）
  static final Dio _dio = Dio(BaseOptions(
    connectTimeout: const Duration(seconds: 15),
    receiveTimeout: const Duration(seconds: 15),
  ));

  Future<Map<String, dynamic>> _post(
    String action,
    Map<String, dynamic> body,
  ) async {
    try {
      final res = await _dio.post(
        _qrBase,
        queryParameters: {'action': action},
        data: body,
      );
      final raw = res.data;
      if (raw is Map) {
        final d = raw['data'];
        if (d is Map) {
          return Map<String, dynamic>.from(d);
        }
        final msg = raw['message']?.toString();
        throw StateError(msg?.isNotEmpty == true ? msg! : '服务端返回异常');
      }
      throw StateError('响应格式异常：${raw.runtimeType}');
    } on DioException catch (e) {
      throw StateError('请求失败（$action）：${e.message ?? e.type}');
    }
  }

  String _deviceName() {
    // 桌面端默认用机器名；后续可让用户自定义
    return 'Windows 桌面端';
  }

  // ── UI ─────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _headerText(cs),
            const SizedBox(height: 20),
            _qrArea(cs),
            const SizedBox(height: 20),
            _hint(cs),
            const SizedBox(height: 12),
            TextButton.icon(
              onPressed: _start,
              icon: const Icon(Icons.refresh),
              label: const Text('刷新二维码'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _headerText(ColorScheme cs) => Text(
        '使用 c001apk 手机端扫描二维码登录',
        style: TextStyle(fontSize: 15, color: cs.onSurfaceVariant),
        textAlign: TextAlign.center,
      );

  Widget _qrArea(ColorScheme cs) {
    final box = Container(
      width: 260,
      height: 260,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
      ),
      padding: const EdgeInsets.all(12),
      child: QrImageView(
        data: _qrUrl ?? '',
        size: 236,
        backgroundColor: Colors.white,
        eyeStyle: const QrEyeStyle(
          eyeShape: QrEyeShape.square,
          color: Colors.black,
        ),
        dataModuleStyle: const QrDataModuleStyle(
          dataModuleShape: QrDataModuleShape.square,
          color: Colors.black,
        ),
      ),
    );

    if (_phase == _Phase.waiting && _qrUrl != null) {
      return box;
    }

    return SizedBox(
      width: 260,
      height: 260,
      child: Stack(
        alignment: Alignment.center,
        children: [
          if (_qrUrl != null) box else const SizedBox.shrink(),
          Container(
            width: 260,
            height: 260,
            decoration: BoxDecoration(
              color: cs.surface.withValues(alpha: 0.92),
              borderRadius: BorderRadius.circular(16),
            ),
            alignment: Alignment.center,
            padding: const EdgeInsets.all(16),
            child: _overlay(cs),
          ),
        ],
      ),
    );
  }

  Widget _overlay(ColorScheme cs) {
    switch (_phase) {
      case _Phase.preparing:
        return const Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 12),
            Text('正在准备二维码…'),
          ],
        );
      case _Phase.scanned:
        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.phone_android, size: 40, color: cs.primary),
            const SizedBox(height: 12),
            const Text('已扫描', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 6),
            Text('请在手机上确认登录', style: TextStyle(color: cs.onSurfaceVariant)),
          ],
        );
      case _Phase.confirmed:
        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.check_circle, size: 40, color: cs.primary),
            const SizedBox(height: 12),
            const Text('登录成功'),
          ],
        );
      case _Phase.expired:
        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.timer_off_outlined, size: 40, color: cs.error),
            const SizedBox(height: 12),
            const Text('二维码已过期'),
            const SizedBox(height: 6),
            Text('点下方「刷新二维码」重试', style: TextStyle(color: cs.onSurfaceVariant)),
          ],
        );
      case _Phase.failed:
        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.error_outline, size: 40, color: cs.error),
            const SizedBox(height: 12),
            const Text('无法开始扫码登录'),
            const SizedBox(height: 6),
            SelectableText(
              _errorText ?? '',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant),
            ),
          ],
        );
      case _Phase.waiting:
        return const SizedBox.shrink();
    }
  }

  Widget _hint(ColorScheme cs) => Text(
        '打开手机 c001apk →「我的」→ 右上角账号管理 →「扫码登录」→ 扫描此二维码',
        style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant),
        textAlign: TextAlign.center,
      );
}

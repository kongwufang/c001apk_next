import 'dart:io';

import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';

import '../../components/settings/drop_down_menu_item.dart';
import '../../components/settings/edittext_item.dart';
import '../../components/settings/switch_item.dart';
import '../../utils/http_log.dart';
import '../../utils/storage_util.dart';

/// 设置 → **高级**。
///
/// 照抄 Android `res/xml/settings.xml` 的 `advanced` 屏，顺序一致：
/// 校验 SSL 证书 → 网络传输调试模式 → 数字联盟 ID → 机型参数 → 图片画质
/// → **HTTP 请求日志**（本轮新增 ✓）
class SettingsAdvancedPage extends StatefulWidget {
  const SettingsAdvancedPage({super.key});

  @override
  State<SettingsAdvancedPage> createState() => _SettingsAdvancedPageState();
}

class _SettingsAdvancedPageState extends State<SettingsAdvancedPage> {
  /// 当前日志体积 / 文件数 / 行数
  int _logBytes = 0;
  int _logFiles = 0;
  int _logLines = 0;
  bool _logBusy = false;

  @override
  void initState() {
    super.initState();
    _refreshLogStats();
  }

  Future<void> _refreshLogStats() async {
    final (bytes, files, lines) = await HttpLog.stats();
    if (!mounted) return;
    setState(() {
      _logBytes = bytes;
      _logFiles = files;
      _logLines = lines;
    });
  }

  String get _logSizeText {
    if (_logBytes <= 0) return '暂无日志';
    final kb = _logBytes / 1024;
    final size = kb < 1024 ? '${kb.toStringAsFixed(1)} KB' : '${(kb / 1024).toStringAsFixed(2)} MB';
    return '$size · $_logFiles 个文件 · $_logLines 条';
  }

  /// 导出：先确认，再弹保存对话框
  Future<void> _exportLogs() async {
    if (_logBusy) return;
    setState(() => _logBusy = true);
    try {
      if (_logBytes <= 0) {
        SmartDialog.showToast('暂无日志可导出');
        return;
      }

      // ★ 导出前必须说清会包含什么 ✓
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('导出 HTTP 请求日志'),
          content: const SingleChildScrollView(
            child: Text(
              '导出的文件会包含这段时间内每个请求的：\n\n'
              '· 方法、地址、HTTP 状态码、耗时\n'
              '· 请求体与响应体的前 2 KB\n\n'
              '以下内容已**在本机脱敏**（只保留长度，不含明文）：\n'
              'token、Cookie、authorization、密码、会话、设备标识等。\n\n'
              '仍然可能包含你访问过的接口路径与返回的公开内容。'
              '仅在你自己要排查问题时导出，并只发给开发者。',
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.of(ctx).pop(false), child: const Text('取消')),
            TextButton(onPressed: () => Navigator.of(ctx).pop(true), child: const Text('继续导出')),
          ],
        ),
      );
      if (ok != true || !mounted) return;

      final (name, content) = await HttpLog.exportAll();

      // 桌面端：保存对话框 ✓
      final location = await getSaveLocation(
        suggestedName: name,
        acceptedTypeGroups: const [
          XTypeGroup(label: '文本', extensions: ['txt', 'log']),
        ],
      );
      if (location == null) return; // 用户取消 ✓

      await File(location.path).writeAsString(content);
      if (!mounted) return;
      SmartDialog.showToast('已导出到 ${location.path}');
    } catch (e) {
      SmartDialog.showToast('导出失败：$e');
    } finally {
      if (mounted) setState(() => _logBusy = false);
    }
  }

  /// 清空：二次确认
  Future<void> _clearLogs() async {
    if (_logBusy) return;
    if (_logBytes <= 0) {
      SmartDialog.showToast('已经是空的');
      return;
    }
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('清空 HTTP 请求日志'),
        content: Text('将删除 $_logFiles 个文件、$_logLines 条记录。此操作不可撤销。'),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(false), child: const Text('取消')),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('清空', style: TextStyle(color: Color(0xFFB3261E))),
          ),
        ],
      ),
    );
    if (ok != true) return;

    setState(() => _logBusy = true);
    try {
      final n = await HttpLog.clear();
      SmartDialog.showToast('已清空 $n 个文件');
      await _refreshLogStats();
    } finally {
      if (mounted) setState(() => _logBusy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('高级')),
      body: ListView(
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        children: [
          // 数字联盟 ID：手动填 + 点一下自动取号（Android 是同一个 preference）
          const EdittextItem(
            icon: Icons.smartphone,
            title: '数字联盟ID',
            boxKey: SettingsBoxKey.szlmId,
            needUpdateXAppDevice: true,
          ),
          ListTile(
            leading: const Icon(Icons.cloud_download_outlined),
            title: const Text('获取数字联盟ID'),
            subtitle: const Text('内置网页取号（人机验证 + 签名校验）'),
            onTap: _onFetchSzlmId,
          ),
          ListTile(
            leading: const Icon(Icons.developer_mode),
            title: const Text('机型参数'),
            onTap: () => Get.toNamed('/params'),
          ),
          // Android 的 `imageQuality`（图片画质）
          DropDownMenuItem(
            icon: Icons.image_outlined,
            title: '图片画质',
            boxKey: SettingsBoxKey.imageQuality,
            items: const [
              DropdownMenuItem<int>(value: 0, child: Text('自动')),
              DropdownMenuItem<int>(value: 1, child: Text('原图')),
              DropdownMenuItem<int>(value: 2, child: Text('缩略图')),
            ],
          ),

          // ── SSL 证书校验 ──
          //
          // 照抄 Android `settings.xml` 的 `sslVerify` / `isSslDebug` 两项。
          // 真源：`util/SslVerify.kt`。
          //
          // ★ 两项**互斥**：调试模式开了就不校验。
          // ★ 改完**需重启应用**才生效 —— OkHttpClient / HttpClient 是单例，
          //   构建时就把 TrustManager / SecurityContext 固化了（Android 同此）。
          const SwitchItem(
            icon: Icons.verified_user_outlined,
            title: '校验 SSL 证书',
            boxKey: SettingsBoxKey.sslVerify,
            defaultValue: true,
          ),
          const SwitchItem(
            icon: Icons.network_check,
            title: '网络传输调试模式',
            boxKey: SettingsBoxKey.sslDebug,
            defaultValue: false,
          ),

          // ── HTTP 请求日志（本轮新增 ✓）─────────────────────
          const Divider(height: 24),
          SwitchListTile(
            secondary: const Icon(Icons.receipt_long_outlined),
            title: const Text('记录 HTTP 请求日志'),
            subtitle: const Text(
              '默认关闭。打开后记录每个请求的方法/地址/状态码/耗时/'
              '响应体（前 2 KB），用于排查问题。\n'
              '敏感字段（token、Cookie、设备标识等）会在本机脱敏，只保留长度。',
            ),
            isThreeLine: true,
            value: GStorage.recordHttpLog,
            onChanged: (v) {
              GStorage.setRecordHttpLog(v);
              setState(() {});
              SmartDialog.showToast(v ? '已开始记录（对之后的请求生效）' : '已停止记录');
            },
          ),
          ListTile(
            leading: const Icon(Icons.data_usage),
            title: const Text('当前日志'),
            subtitle: Text(_logSizeText),
            trailing: _logBusy
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : IconButton(
                    tooltip: '刷新',
                    icon: const Icon(Icons.refresh),
                    onPressed: _refreshLogStats,
                  ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
            child: Row(
              children: [
                // 关闭记录时也允许导出/清空 ✓（用户可能想导完再关 ✓）
                FilledButton.icon(
                  onPressed: _logBusy ? null : _exportLogs,
                  icon: const Icon(Icons.ios_share),
                  label: const Text('导出日志'),
                ),
                const SizedBox(width: 12),
                TextButton.icon(
                  onPressed: _logBusy ? null : _clearLogs,
                  icon: Icon(Icons.delete_outline, color: cs.error),
                  label: Text('清空', style: TextStyle(color: cs.error)),
                ),
              ],
            ),
          ),
          // 日志目录提示（方便用户自己找 ✓）
          ListTile(
            dense: true,
            leading: Icon(Icons.folder_outlined, size: 18, color: cs.outline),
            title: Text(
              '日志按天存放，单文件超 2 MB 自动换新，保留最近 7 天',
              style: TextStyle(fontSize: 12, color: cs.outline),
            ),
          ),
        ],
      ),
    );
  }

  /// 自动取号前弹**知情同意**。
  ///
  /// 对齐 Android `util/SzlmIdPrompt.kt`：
  /// - 正文**逐条列出**会上传的设备信息（隐私必须告知）
  /// - 两个出口：「不同意并手动填入」（走上面那个输入框自己填）
  ///   和「我已知情并同意」（打开内置网页换取 DUID）
  ///
  /// 原文案见 `res/values/strings.xml` 的 `szlm_id_consent_*`。
  Future<void> _onFetchSzlmId() async {
    final agree = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('获取数字联盟ID'),
        content: const SingleChildScrollView(
          child: Text(
            '本应用创新性地内置了一套数字联盟 ID（DUID）的获取方法。'
            '为完成换取，需要把你的以下设备信息上传到本应用的服务器：\n\n'
            '· 机型参数：厂商、品牌、型号、版本号、Android 版本、API 等级\n'
            '· 安卓设备唯一标识 SSAID（ANDROID_ID）\n'
            '· 本应用的版本信息：版本名、版本号、包名\n'
            '· 本应用的真实 UA\n\n'
            '这些信息仅用于换取并缓存 DUID，不作其它用途，'
            '也不包含你的账号信息。需要你知情并同意。',
          ),
        ),
        actions: [
          TextButton(
            // 「不同意并手动填入」：关掉弹窗即可 —— 上面那个「数字联盟ID」
            // 输入框就是手动填写入口（Android 也是落同一个 preference）
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('不同意并手动填入'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('我已知情并同意'),
          ),
        ],
      ),
    );
    if (agree == true) {
      Get.toNamed('/szlmId');
    }
  }
}

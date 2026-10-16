import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';

import '../../logic/model/spam_config/spam_config.dart';
import '../../logic/repo/spam_config_repo.dart';

/// 屏蔽项页面（关键字 / 用户 / 节点）。
///
/// 对应 Android `ui/settings/SpamShieldActivity.kt` + `activity_spam_shield.xml`。
/// Android 是**按 type 分别进入**（`spamWord` / `spamUser` / `spamNode` 三个入口），
/// 这里保持同样的结构：一个页面按 [type] 切换。
///
/// 三类共用一个输入区 + 列表，两点差异：
/// - **节点**：多一个「节点名称」输入框，且添加时要 ID + 名称两个值
/// - 其余：只有一个输入框
///
/// 文案逐项取自 Android `strings.xml`，别自己改词。
class SpamShieldPage extends StatefulWidget {
  const SpamShieldPage({super.key, required this.type});

  /// `word` / `user` / `node`
  final String type;

  @override
  State<SpamShieldPage> createState() => _SpamShieldPageState();
}

class _SpamShieldPageState extends State<SpamShieldPage> {
  final _valueCtrl = TextEditingController();
  final _nameCtrl = TextEditingController();
  bool _loading = true;

  bool get _isNode => widget.type == 'node';

  /// 标题（Android `SpamShieldActivity` 的 `title = when (type)`）
  String get _title => switch (widget.type) {
        'user' => '用户屏蔽',
        'node' => '节点屏蔽',
        _ => '关键字屏蔽',
      };

  /// 主输入框提示（Android `spam_shield_*_hint`）
  String get _hint => switch (widget.type) {
        'user' => '输入要屏蔽的用户 uid',
        'node' => '节点 ID（如 7000004063）',
        _ => '输入要屏蔽的关键字',
      };

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  @override
  void dispose() {
    _valueCtrl.dispose();
    _nameCtrl.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    if (mounted) setState(() => _loading = true);
    await SpamConfigRepo.refresh();
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _add() async {
    final value = _valueCtrl.text.trim();
    if (value.isEmpty) {
      FlutterSmartDialog.toast('请先填写内容');
      return;
    }
    final name = _nameCtrl.text.trim();
    if (_isNode && name.isEmpty) {
      // Android 同样要求节点必须同时给 ID 和名称
      FlutterSmartDialog.toast('节点名称不能为空');
      return;
    }

    setState(() => _loading = true);
    final ok = switch (widget.type) {
      'user' => await SpamConfigRepo.addUser(value),
      'node' => await SpamConfigRepo.addNode(value, name),
      _ => await SpamConfigRepo.addKeyword(value),
    };
    if (!mounted) return;
    setState(() => _loading = false);
    if (ok) {
      _valueCtrl.clear();
      _nameCtrl.clear();
    } else {
      FlutterSmartDialog.toast('添加失败');
    }
  }

  Future<void> _remove(String value, String? name) async {
    setState(() => _loading = true);
    final ok = switch (widget.type) {
      'user' => await SpamConfigRepo.cancelUser(value),
      'node' => await SpamConfigRepo.cancelNode(value, name ?? ''),
      _ => await SpamConfigRepo.cancelKeyword(value),
    };
    if (!mounted) return;
    setState(() => _loading = false);
    if (!ok) FlutterSmartDialog.toast('移除失败');
  }

  /// 当前列表项（统一成 `(主值, 显示主标题, 显示副标题)`）
  ///
  /// ⚠️ 每个分支都显式标了 `<(String, String, String?)>`：`List` 在 Dart 里是
  /// **不变（invariant）**的，`List<(String, String, Null)>` 并不能赋给
  /// `List<(String, String, String?)>`，必须让 map 的泛型参数落定成目标类型，
  /// 否则报类型不匹配。
  List<(String, String, String?)> get _items {
    final cfg = SpamConfigRepo.config.value;
    return switch (widget.type) {
      'user' => cfg.user
          .map<(String, String, String?)>(
              (u) => (u.uid ?? '', u.name ?? u.uid ?? '', u.uid))
          .toList(),
      'node' => cfg.node
          .map<(String, String, String?)>((n) =>
              (n.targetFullId ?? n.id ?? '', n.title ?? '', n.targetFullId))
          .toList(),
      _ => cfg.custom
          .map<(String, String, String?)>(
              (k) => (k.title ?? '', k.title ?? '', null))
          .toList(),
    };
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: Text(_title),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: '刷新',
            onPressed: _refresh,
          ),
        ],
      ),
      body: Column(
        children: [
          // ── 输入区 ──
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
            child: Column(
              children: [
                TextField(
                  controller: _valueCtrl,
                  decoration: InputDecoration(
                    hintText: _hint,
                    isDense: true,
                    border: const OutlineInputBorder(),
                  ),
                  onSubmitted: (_) => _add(),
                ),
                // 只有节点类型需要第二个输入框（Android：`editText2.isVisible =
                // viewModel.type == "node"`）
                if (_isNode) ...[
                  const SizedBox(height: 8),
                  TextField(
                    controller: _nameCtrl,
                    decoration: const InputDecoration(
                      hintText: '节点名称（如 一加13）',
                      isDense: true,
                      border: OutlineInputBorder(),
                    ),
                    onSubmitted: (_) => _add(),
                  ),
                ],
                const SizedBox(height: 8),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: _loading ? null : _add,
                    child: const Text('添加'),
                  ),
                ),
              ],
            ),
          ),

          if (_loading) const LinearProgressIndicator(minHeight: 2),

          // ── 列表 ──
          Expanded(
            child: _items.isEmpty
                ? Center(
                    child: Text(
                      _loading ? '' : '暂无屏蔽项',
                      style: TextStyle(color: colorScheme.onSurfaceVariant),
                    ),
                  )
                : ListView.separated(
                    itemCount: _items.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (_, i) {
                      final (value, title, subtitle) = _items[i];
                      return ListTile(
                        title: Text(title),
                        subtitle: subtitle == null ? null : Text(subtitle),
                        trailing: IconButton(
                          icon: const Icon(Icons.delete_outline),
                          tooltip: '移除',
                          onPressed: () => _remove(value, subtitle),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}

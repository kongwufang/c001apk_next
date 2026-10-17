import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';

import '../../pages/home/home_page.dart' show TabType;
import '../../utils/storage_util.dart';

/// 编辑 Tab：调整首页顶部的分类标签（顺序 + 启用）。
///
/// 对应 Android：
/// - 设置项 `editTab`（在 `settings_category_layout`「布局」分类里）
/// - 跳 `CopyActivity` with `type = "homeMenu"`
/// - 页面用 `HomeMenuAdapter` + `ItemTouchHelperCallback`（拖动排序）
/// - 保存走 `HomeViewModel.updateTab()` → 写 `HomeMenu` 表 → `restart = true`
///
/// ## 数据形态
/// Android 是 Room 表 `HomeMenu(position, title, isEnable)`；
/// 这里存成**有序的启用名单**（`GStorage.homeMenu`，元素是 `TabType` 枚举名）：
/// - 不在名单里 = 已禁用 —— 一个字段表达两件事，表结构也不会随 tab 增减而错位。
///
/// ## 平台差异（如实说明）
/// 「应用」（`APP`）这个 tab 只有 Android 有（桌面端没有 `AppListPage`），
/// 所以在桌面端**不列出来** —— 与 `home_page.dart` 里
/// `if (Platform.isAndroid)` 的既有规则保持一致。
///
/// ## 一条硬约束
/// **至少要留一个启用的 tab**，否则首页 `TabController(length: 0)` 会直接崩。
/// 所以关掉最后一个时会拦下来。
class EditTabPage extends StatefulWidget {
  const EditTabPage({super.key});

  @override
  State<EditTabPage> createState() => _EditTabPageState();
}

class _EditTabPageState extends State<EditTabPage> {
  /// 当前平台可编辑的全部 tab（保持默认顺序）
  late final List<TabType> _all = GStorage.defaultHomeMenu
      .map((name) => TabType.values.firstWhere(
            (t) => t.name == name,
            orElse: () => TabType.NONE,
          ))
      .where((t) => t != TabType.NONE)
      // 桌面端没有「应用」页
      .where((t) => Platform.isAndroid || t != TabType.APP)
      .toList();

  /// 启用顺序（未启用的不在里面）
  late List<TabType> _enabled = _all
      .where((t) => GStorage.homeMenu.contains(t.name))
      .toList();

  bool _isEnabled(TabType t) => _enabled.contains(t);

  void _toggle(TabType t, bool on) {
    setState(() {
      if (on) {
        // 启用时插回「默认相对顺序」对应的位置，而不是无脑追加，
        // 免得反复开关把顺序搞乱
        final defaultIndex = _all.indexOf(t);
        var insertAt = _enabled.length;
        for (var i = 0; i < _enabled.length; i++) {
          if (_all.indexOf(_enabled[i]) > defaultIndex) {
            insertAt = i;
            break;
          }
        }
        _enabled.insert(insertAt, t);
      } else {
        // 硬约束：至少留一个
        if (_enabled.length <= 1) {
          SmartDialog.showToast('至少要保留一个标签');
          return;
        }
        _enabled.remove(t);
      }
    });
  }

  void _save() {
    if (_enabled.isEmpty) {
      SmartDialog.showToast('至少要保留一个标签');
      return;
    }
    GStorage.setHomeMenu(_enabled.map((e) => e.name).toList());
    // Android 是 `restart = true` 让 Activity 重建；
    // 这里让 GetMaterialApp 重建，首页会按新顺序重新建 tab。
    Get.forceAppUpdate();
    Get.back();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    // 未启用的项排在已启用项后面，一起列出来给用户打开
    final rows = <TabType>[
      ..._enabled,
      ..._all.where((t) => !_isEnabled(t)),
    ];

    return Scaffold(
      appBar: AppBar(
        title: const Text('编辑Tab'),
        actions: [
          TextButton(
            onPressed: _save,
            child: Text(
              '保存',
              style: TextStyle(
                color: colorScheme.primary,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          const SizedBox(width: 4),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: Text(
              '调整首页顶部的分类标签：拖动右侧手柄可排序，开关控制是否显示',
              style: TextStyle(fontSize: 12, color: colorScheme.outline),
            ),
          ),
          Expanded(
            // `ReorderableListView` 需要在拖动时重建列表；
            // 只有「已启用」的那些可以拖动（禁用项没位置可言）
            child: ReorderableListView.builder(
              buildDefaultDragHandles: false,
              itemCount: rows.length,
              onReorder: (oldIndex, newIndex) {
                // 只允许在「已启用」区间内调整
                if (oldIndex >= _enabled.length) return;
                setState(() {
                  if (newIndex > oldIndex) newIndex -= 1;
                  if (newIndex >= _enabled.length) newIndex = _enabled.length - 1;
                  final item = _enabled.removeAt(oldIndex);
                  _enabled.insert(newIndex, item);
                });
              },
              itemBuilder: (context, index) {
                final t = rows[index];
                final enabled = _isEnabled(t);
                final draggable = index < _enabled.length;
                return ListTile(
                  key: ValueKey(t.name),
                  title: Text(t.label),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Switch(
                        value: enabled,
                        onChanged: (v) => _toggle(t, v),
                      ),
                      if (draggable)
                        ReorderableDragStartListener(
                          index: index,
                          child: Padding(
                            padding: const EdgeInsets.only(left: 8),
                            child: Icon(
                              Icons.drag_handle,
                              color: colorScheme.outline,
                            ),
                          ),
                        )
                      else
                        const SizedBox(width: 32),
                    ],
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

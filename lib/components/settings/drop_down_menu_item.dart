import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../utils/storage_util.dart';

class DropDownMenuItem extends StatefulWidget {
  const DropDownMenuItem({
    super.key,
    required this.icon,
    required this.title,
    required this.boxKey,
    required this.items,
    this.forceAppUpdate = false,

    /// 第首次运行（Hive 里还没有这个 key）时显示哪一项。
    ///
    /// ⚠️ **必须落在 `items` 的 value 里** —— 原来这里是写死的 0，
    /// 于是「转场速度」这种 `items` 是 100~500 的下拉框会在首启时
    /// 因为「找不到 value=0 的项」直接 **assert 崩掉**
    /// （`DropdownButton` 要求 `value` 恰好命中一项）。
    this.defaultValue = 0,
  });

  final IconData icon;
  final String title;
  final String boxKey;
  final List<DropdownMenuItem<int>> items;
  final bool forceAppUpdate;
  final int defaultValue;

  @override
  State<DropDownMenuItem> createState() => _DropDownMenuItemState();
}

class _DropDownMenuItemState extends State<DropDownMenuItem> {
  late int value;

  @override
  void initState() {
    super.initState();
    value = GStorage.settings.get(widget.boxKey, defaultValue: widget.defaultValue);
  }

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(widget.icon),
      title: Text(widget.title),
      trailing: DropdownButton<int>(
        value: value,
        onChanged: (int? newValue) {
          if (newValue != null && newValue != value) {
            GStorage.settings.put(widget.boxKey, newValue);
            setState(() => value = newValue);
            if (widget.forceAppUpdate) {
              Get.forceAppUpdate();
            }
          }
        },
        items: widget.items,
      ),
    );
  }
}

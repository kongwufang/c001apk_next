import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../components/dialog.dart';
import '../../components/settings/drop_down_menu_item.dart';
import '../../components/settings/item_title.dart';
import '../../components/settings/switch_item.dart';
import '../../constants/constants.dart';
import '../../utils/storage_util.dart';

/// 设置 → **外观**。
///
/// 照抄 Android `res/xml/settings.xml` 的 `appearance` 屏，
/// 四个分组（主题 / 显示 / 布局 / 转场动画）与组内顺序都一致。
///
/// 未实现的项（**故意不放**，免得点了没反应）：
/// 编辑Tab、转场动画三项 —— 这两块功能本工程还没有。
class SettingsAppearancePage extends StatelessWidget {
  const SettingsAppearancePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('外观')),
      body: ListView(
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        children: [
          // ── 主题 ──
          const ItemTitle(title: '主题'),
          const SwitchItem(
            icon: Icons.palette_outlined,
            title: '系统主题色',
            boxKey: SettingsBoxKey.useMaterial,
            defaultValue: true,
            forceAppUpdate: true,
          ),
          Visibility(
            // 与 Android 一致：`themeColor` 依赖 `followSystemAccent`，
            // 系统取色开着时该项不可选
            visible: !GStorage.useMaterial,
            child: DropDownMenuItem(
              icon: Icons.format_color_fill,
              title: '主题颜色',
              boxKey: SettingsBoxKey.staticColor,
              items: Constants.themeType
                  .map((type) => DropdownMenuItem<int>(
                        value: Constants.themeType.indexOf(type),
                        child: Text(type),
                      ))
                  .toList(),
              forceAppUpdate: true,
            ),
          ),
          const DropDownMenuItem(
            icon: Icons.dark_mode_outlined,
            title: '深色主题',
            boxKey: SettingsBoxKey.selectedTheme,
            items: [
              DropdownMenuItem<int>(value: 1, child: Text('关闭')),
              DropdownMenuItem<int>(value: 2, child: Text('开启')),
              DropdownMenuItem<int>(value: 0, child: Text('跟随系统')),
            ],
            forceAppUpdate: true,
          ),
          const SwitchItem(
            icon: Icons.invert_colors,
            title: '纯黑主题',
            boxKey: SettingsBoxKey.blackDarkTheme,
            defaultValue: false,
            forceAppUpdate: true,
          ),

          // ── 显示 ──
          const ItemTitle(title: '显示'),
          ListTile(
            leading: const Icon(Icons.text_fields),
            title: const Text('字体调节'),
            subtitle: Text('${GStorage.fontScale.toStringAsFixed(2)}x'),
            onTap: () => showDialog<void>(
              context: context,
              builder: (context) => SliderDialog(
                fontScale: GStorage.fontScale,
                setData: (newValue) {
                  GStorage.setFontScale(newValue);
                  Get.forceAppUpdate();
                },
              ),
            ),
          ),
          const SwitchItem(
            icon: Icons.emoji_emotions_outlined,
            title: '显示表情',
            boxKey: SettingsBoxKey.showEmoji,
            defaultValue: true,
          ),
          const SwitchItem(
            icon: Icons.filter,
            title: '深色模式压暗图片',
            boxKey: SettingsBoxKey.isColorFilter,
            defaultValue: true,
          ),
        ],
      ),
    );
  }
}

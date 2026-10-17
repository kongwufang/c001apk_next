import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../components/dialog.dart';
import '../../components/settings/drop_down_menu_item.dart';
import '../../components/settings/item_title.dart';
import '../../components/settings/switch_item.dart';
import '../../constants/constants.dart';
import '../../utils/storage_util.dart';
import 'edit_tab_page.dart';

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

          // ── 布局 ──
          //
          // 照抄 Android `res/xml/settings.xml` 的 `settings_category_layout` 分类。
          // 原来首页工具栏有个「编辑Tab」按钮，位置让给消息入口后挪到了这里
          // （Android 注释：「2026-10-05：首页工具栏那个位置让给了消息入口，编辑 Tab 挪到这儿」）。
          const ItemTitle(title: '布局'),
          ListTile(
            leading: const Icon(Icons.menu),
            title: const Text('编辑Tab'),
            subtitle: const Text('调整首页顶部的分类标签'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Get.to(() => const EditTabPage()),
          ),

          // ── 转场动画 ──
          //
          // 照抄 Android `res/xml/settings.xml` 的 `animation` 子屏，三项：
          // 转场曲线 / 转场类型 / 转场速度。
          // 文案直接取 `arrays.xml` 的 `anim_curve_texts` / `anim_type_texts` /
          // `anim_speed_texts`。
          const ItemTitle(title: '转场动画'),
          const DropDownMenuItem(
            icon: Icons.show_chart,
            title: '转场曲线',
            boxKey: SettingsBoxKey.animCurve,
            // 与 `GStorage.animCurve` 的默认值一致（2 = std）。
            // 必须显式给 —— 组件原来的默认是 0，会与 getter 对不上。
            defaultValue: 2,
            items: [
              DropdownMenuItem<int>(value: 0, child: Text('线性（匀速）· 改造前')),
              DropdownMenuItem<int>(value: 1, child: Text('M2 标准 (0.4, 0, 0.2, 1)')),
              DropdownMenuItem<int>(value: 2, child: Text('M3 标准')),
              DropdownMenuItem<int>(value: 3, child: Text('M3 强调')),
              DropdownMenuItem<int>(value: 4, child: Text('弹簧（rikkahub 同款）')),
            ],
          ),
          const DropDownMenuItem(
            icon: Icons.animation,
            title: '转场类型',
            boxKey: SettingsBoxKey.animType,
            defaultValue: 0, // slide
            items: [
              DropdownMenuItem<int>(value: 0, child: Text('水平滑动')),
              DropdownMenuItem<int>(value: 1, child: Text('淡入淡出')),
              DropdownMenuItem<int>(value: 2, child: Text('无动画')),
            ],
          ),
          const DropDownMenuItem(
            icon: Icons.speed,
            title: '转场速度',
            boxKey: SettingsBoxKey.animDuration,
            // ★ 这个必须给，否则首启会崩：
            //   `items` 的 value 是 100~500，而组件默认读 0，
            //   `DropdownButton` 找不到 value=0 的项会直接 assert。
            defaultValue: 400,
            items: [
              DropdownMenuItem<int>(value: 100, child: Text('100 ms')),
              DropdownMenuItem<int>(value: 200, child: Text('200 ms')),
              DropdownMenuItem<int>(value: 300, child: Text('300 ms')),
              DropdownMenuItem<int>(value: 400, child: Text('400 ms')),
              DropdownMenuItem<int>(value: 500, child: Text('500 ms')),
            ],
          ),
        ],
      ),
    );
  }
}

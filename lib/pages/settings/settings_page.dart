import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:package_info_plus/package_info_plus.dart';

import 'settings_advanced_page.dart';
import 'settings_appearance_page.dart';
import 'settings_other_page.dart';
import 'settings_privacy_page.dart';
import 'settings_recommend_page.dart';

/// 设置（一级菜单）。
///
/// ## ★ 结构**完全照抄** Android `res/xml/settings.xml`
///
/// 顺序：**外观 / 推荐流相关 / 隐私 / 高级 / 其他 / 关于**
///
/// Android 那边一级菜单**只保留「入口」**，点进去才是具体开关
/// （原注释 2026-10-05）：
/// > 一级菜单只保留「入口」，点进去才是具体开关。
/// > 顺序：外观 / 推荐流相关 / 隐私 / 高级 / 其他 / 关于
/// > 「关于」已提到一级：点击直接打开 AboutActivity
///
/// 所以这里也做成同样的两级结构，不再是一大坨平铺。
///
/// ## 与上游（Flutter 原版）的差异
/// 上游是一张平铺的长列表，而且有两项**用户的 App 里没有**：
/// - **关注类型**（`followType`）
/// - **检查更新**（`checkUpdate`）—— Android 已把它挪进「关于」页里
///
/// 已按用户要求删除。
class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  String _version = '1.0.0(1)';

  @override
  void initState() {
    super.initState();
    _getVersionInfo();
  }

  Future<void> _getVersionInfo() async {
    try {
      final info = await PackageInfo.fromPlatform();
      if (!mounted) return;
      setState(() => _version = '${info.version}(${info.buildNumber})');
    } catch (_) {
      // 拿不到就保持默认值
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('设置')),
      body: ListView(
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        children: [
          _entry(
            icon: Icons.palette_outlined,
            title: '外观',
            onTap: () => Get.toNamed('/settingsAppearance'),
          ),
          _entry(
            icon: Icons.dynamic_feed_outlined,
            title: '推荐流相关',
            onTap: () => Get.toNamed('/settingsRecommend'),
          ),
          _entry(
            icon: Icons.block_outlined,
            title: '隐私',
            onTap: () => Get.toNamed('/settingsPrivacy'),
          ),
          _entry(
            icon: Icons.developer_mode_outlined,
            title: '高级',
            onTap: () => Get.toNamed('/settingsAdvanced'),
          ),
          _entry(
            icon: Icons.more_horiz_outlined,
            title: '其他',
            onTap: () => Get.toNamed('/settingsOther'),
          ),
          // 「关于」在一级，点击**直接**打开关于页
          //（Android 注释：去掉了「其他 → 关于 → 关于本应用」中间那两层无用目录）
          _entry(
            icon: Icons.info_outline,
            title: '关于',
            onTap: () => Get.toNamed('/about', parameters: {'version': _version}),
          ),
        ],
      ),
    );
  }

  Widget _entry({
    required IconData icon,
    required String title,
    required VoidCallback onTap,
  }) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    );
  }
}

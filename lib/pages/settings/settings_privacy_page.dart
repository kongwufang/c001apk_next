import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../pages/blacklist/black_list_page.dart' show BlackListType;

/// 设置 → **隐私**。
///
/// 照抄 Android `res/xml/settings.xml` 的 `privacy` 屏 —— 里面只有一项
/// 「用户黑名单」。虽然只有一项，仍然做成二级页，与用户 App 的结构一致。
class SettingsPrivacyPage extends StatelessWidget {
  const SettingsPrivacyPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('隐私')),
      body: ListView(
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        children: [
          ListTile(
            leading: const Icon(Icons.block),
            title: const Text('用户黑名单'),
            onTap: () => Get.toNamed(
              '/blacklist',
              arguments: {'type': BlackListType.user},
            ),
          ),
        ],
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../components/settings/switch_item.dart';
import '../../pages/blacklist/black_list_page.dart' show BlackListType;
import '../../utils/storage_util.dart';

/// 设置 → **推荐流相关**。
///
/// 照抄 Android `res/xml/settings.xml` 的 `recommendation` 屏，顺序一致：
/// 话题黑名单 → 关键字屏蔽 → 用户屏蔽 → 节点屏蔽 →
/// 使用外部浏览器打开链接 → 头条显示广场 → 记录浏览历史
class SettingsRecommendPage extends StatelessWidget {
  const SettingsRecommendPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('推荐流相关')),
      body: ListView(
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        children: [
          ListTile(
            leading: const Icon(Icons.block),
            title: const Text('话题黑名单'),
            onTap: () => Get.toNamed(
              '/blacklist',
              arguments: {'type': BlackListType.topic},
            ),
          ),
          // 其他屏蔽项（关键字 / 用户 / 节点）
          //
          // 对应 Android `SettingsPreferenceFragment` 里的三个 preference：
          // `spamWord` / `spamUser` / `spamNode`，各自起一个 SpamShieldActivity。
          // 读 `/v6/user/spamWordList`、写 `/v6/account/updateConfig`，
          // 配置缓存在本地，冷启动没网也能先过滤。
          ListTile(
            leading: const Icon(Icons.text_fields),
            title: const Text('关键字屏蔽'),
            subtitle: const Text('正文/标题命中即不显示'),
            onTap: () => Get.toNamed('/spamShield/word'),
          ),
          ListTile(
            leading: const Icon(Icons.block),
            title: const Text('用户屏蔽'),
            subtitle: const Text('按 uid 屏蔽'),
            onTap: () => Get.toNamed('/spamShield/user'),
          ),
          ListTile(
            leading: const Icon(Icons.block),
            title: const Text('节点屏蔽'),
            subtitle: const Text('按节点 ID / 名称屏蔽'),
            onTap: () => Get.toNamed('/spamShield/node'),
          ),
          const SwitchItem(
            icon: Icons.travel_explore,
            title: '使用外部浏览器打开链接',
            boxKey: SettingsBoxKey.openInBrowser,
            defaultValue: false,
          ),
          // Android 的 `isIconMiniCard`，字符串是「头条显示广场」
          const SwitchItem(
            icon: Icons.dynamic_feed,
            title: '头条显示广场',
            boxKey: SettingsBoxKey.showSquare,
            defaultValue: true,
          ),
          const SwitchItem(
            icon: Icons.history,
            title: '记录浏览历史',
            boxKey: SettingsBoxKey.recordHistory,
            defaultValue: true,
          ),
        ],
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../components/settings/drop_down_menu_item.dart';
import '../../components/settings/edittext_item.dart';
import '../../utils/storage_util.dart';

/// 设置 → **高级**。
///
/// 照抄 Android `res/xml/settings.xml` 的 `advanced` 屏，顺序一致：
/// 校验 SSL 证书 → 网络传输调试模式 → 数字联盟 ID → 机型参数 → 图片画质
///
/// **未实现的两项故意不放**（免得点了没反应）：
/// - 校验 SSL 证书（`verifySsl`）
/// - 网络传输调试模式（`sslDebug`）
///
/// 这两项要等 SSL 证书固定（certificate pinning）那套逻辑落地后再补 ——
/// 它们各自还有「两者互斥」「需二次确认 + 5 秒等待」的交互。
class SettingsAdvancedPage extends StatelessWidget {
  const SettingsAdvancedPage({super.key});

  @override
  Widget build(BuildContext context) {
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
            onTap: () => Get.toNamed('/szlmId'),
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
        ],
      ),
    );
  }
}

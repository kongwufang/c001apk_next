import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../../constants/constants.dart';
import '../../utils/storage_util.dart';
import '../../utils/utils.dart';

/// 关于本应用。
///
/// 对应 Android `ui/others/AboutActivity.kt`（254 行）+
/// `res/layout/activity_about.xml`（359 行），2026-10-07 整页重写的 M3E 版。
///
/// ## 页面结构（与 Android 五区块一一对应）
/// ```
/// ① 应用信息卡  图标 / 名称 / 版本
/// ② 更新卡      「检查正式版更新」开关 + 「立即检查更新」
/// ③ 开发者卡    前辈（原作者 / 协作）+ 维护者（带 GitHub bio）
/// ④ 反馈卡      查看源码（GitHub）+ 装机量统计
/// ⑤ 许可证卡    20 条，副标题「作者 · 许可证」，点了跳仓库
/// ```
///
/// ## 与 Android 的**已知差异**（没有假装有）
/// - Android 的「群组链接」是**服务端下发**的（`UpdateChecker.fetchOrgLinks()`）✗
///   桌面端没有那条通道 ⇒ **不显示** ✓
/// - Android 显示 `BuildConfig.BUILD_TIME` ✗ 桌面端 Flutter 没有编译时间常量 ⇒ 不显示 ✓
/// - 图标用 `Icons.all_inclusive`（桌面端没打包 mipmap）✓
class AboutPage extends StatefulWidget {
  const AboutPage({super.key});

  @override
  State<AboutPage> createState() => _AboutPageState();
}

class _AboutPageState extends State<AboutPage> {
  /// 维护者的 GitHub bio（异步拉，拉不到就不显示）
  String _bio = '';

  static const String _maintainer = 'kongwufang';

  /// 装机量统计页。
  ///
  /// key 与 Android `AboutActivity.statsUrl` 里那份**完全一致** ✓
  /// （那个 key 本来就会随服务端 conf.php 下发，放在这里不会多泄露什么）
  static const String _statsUrl =
      'https://service.houlangs.cn/c001apk/stats.php?key=87cf6dccca45ab5ce25530c567026109ea063f29';

  @override
  void initState() {
    super.initState();
    _fetchBio();
  }

  /// 拉维护者的 GitHub bio。失败就静默（不打扰用户 ✓）
  Future<void> _fetchBio() async {
    try {
      final dio = Dio(BaseOptions(
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 10),
      ));
      final res = await dio.get<Map<String, dynamic>>(
        'https://api.github.com/users/$_maintainer',
      );
      final bio = (res.data?['bio'] as String?)?.trim() ?? '';
      if (!mounted || bio.isEmpty) return;
      setState(() => _bio = bio);
    } catch (_) {
      // 网络不通 / 被限流：不显示 bio ✓
    }
  }

  @override
  Widget build(BuildContext context) {
    final version = Get.parameters['version'] ?? '';
    return Scaffold(
      appBar: AppBar(
        title: const Text('关于'),
        bottom: const PreferredSize(
          preferredSize: Size.zero,
          child: Divider(height: 1),
        ),
      ),
      body: ListView(
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        children: [
          _appInfoCard(context, version),
          _updateCard(context),
          _developersCard(context),
          _feedbackCard(context),
          _licensesCard(context),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  // ── ① 应用信息 ────────────────────────────────────────────

  Widget _appInfoCard(BuildContext context, String version) {
    final cs = Theme.of(context).colorScheme;
    return _card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(
                color: cs.primaryContainer,
                borderRadius: BorderRadius.circular(16),
              ),
              child: Icon(
                Icons.all_inclusive,
                size: 36,
                color: cs.onPrimaryContainer,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    Constants.APP_NAME,
                    style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    version.isEmpty ? '版本未知' : '版本 $version',
                    style: TextStyle(fontSize: 13, color: cs.onSurfaceVariant),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'c001apk 的 Windows 桌面端',
                    style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ── ② 更新 ────────────────────────────────────────────────

  Widget _updateCard(BuildContext context) {
    return _card(
      child: Column(
        children: [
          SwitchListTile(
            title: const Text('检查正式版更新'),
            subtitle: const Text('启动时检查是否有新版本'),
            value: GStorage.checkUpdate,
            onChanged: (v) => setState(() => GStorage.setCheckUpdate(v)),
          ),
          const Divider(height: 1, indent: 16, endIndent: 16),
          ListTile(
            leading: const Icon(Icons.system_update_alt),
            title: const Text('立即检查更新'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Get.toNamed('/appUpdate'), // ← 路由名是 /appUpdate ✗ 不是 /update
          ),
        ],
      ),
    );
  }

  // ── ③ 开发者 ──────────────────────────────────────────────

  Widget _developersCard(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return _card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _sectionTitle(context, '前辈'),
          _entry(
            context,
            icon: Icons.person,
            title: 'bggRGjQaUbCoE',
            subtitle: '开发者 / 设计',
            url: 'https://github.com/bggRGjQaUbCoE',
          ),
          _divider(),
          _entry(
            context,
            icon: Icons.person_outline,
            title: 'klxiaoniu',
            subtitle: '开发者 / 协作',
            url: 'https://github.com/klxiaoniu',
          ),
          _sectionTitle(context, '维护者'),
          _entry(
            context,
            icon: Icons.verified_user,
            title: _maintainer,
            subtitle: '维护者',
            url: 'https://github.com/$_maintainer',
            extra: _bio.isEmpty
                ? null
                : Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(
                      _bio,
                      style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant),
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  // ── ④ 反馈 ────────────────────────────────────────────────

  Widget _feedbackCard(BuildContext context) {
    return _card(
      child: Column(
        children: [
          _link(
            context,
            icon: Icons.code,
            title: '查看源码（GitHub）',
            subtitle: Constants.URL_SOURCE_CODE,
            onTap: () => Utils.launchURL(Constants.URL_SOURCE_CODE),
          ),
          _divider(),
          _link(
            context,
            icon: Icons.bar_chart,
            title: '装机量统计',
            subtitle: '查看本应用的安装量',
            onTap: () => Utils.launchURL(_statsUrl),
          ),
          // ⚠️ Android 这里还有「服务端下发的群组链接」（UpdateChecker.fetchOrgLinks）
          //    桌面端没有那条通道 ⇒ 不显示 ✓（见类注释）
        ],
      ),
    );
  }

  // ── ⑤ 开源许可证 ──────────────────────────────────────────

  Widget _licensesCard(BuildContext context) {
    return _card(
      child: Column(
        children: [
          for (var i = 0; i < _licenses.length; i++) ...[
            if (i > 0) _divider(),
            _link(
              context,
              icon: Icons.source_outlined,
              title: _licenses[i].name,
              subtitle: '${_licenses[i].author} · ${_licenses[i].license}',
              onTap: () => Utils.launchURL(_licenses[i].url),
            ),
          ],
        ],
      ),
    );
  }

  // ── 通用部件 ──────────────────────────────────────────────

  Widget _card({required Widget child}) => Card(
        margin: const EdgeInsets.only(bottom: 12),
        clipBehavior: Clip.antiAlias,
        child: child,
      );

  Widget _sectionTitle(BuildContext context, String text) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
        child: Text(
          text,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: Theme.of(context).colorScheme.primary,
          ),
        ),
      );

  /// 与 Android `addDivider` 一致：1px，左端缩进到头像右侧（72dp）
  Widget _divider() => const Divider(height: 1, indent: 72, endIndent: 16);

  /// `item_about_entry`：头像 + 标题 + 副标题 + 右箭头（点了开外链）
  Widget _entry(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String subtitle,
    required String url,
    Widget? extra,
  }) {
    return ListTile(
      leading: CircleAvatar(radius: 20, child: Icon(icon, size: 20)),
      title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(subtitle),
          if (extra != null) extra,
        ],
      ),
      trailing: const Icon(Icons.chevron_right),
      onTap: () => Utils.launchURL(url),
    );
  }

  /// `item_about_link`：标题 + 副标题 + 右箭头
  Widget _link(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    );
  }

  /// 与 Android `AboutActivity.LICENSES` **逐条一致**（20 条）✓
  static const List<_License> _licenses = [
    _License('kotlin', 'JetBrains', 'Apache-2.0',
        'https://github.com/JetBrains/kotlin'),
    _License('AndroidX', 'Google', 'Apache-2.0',
        'https://github.com/androidx/androidx'),
    _License('material-components-android', 'Google', 'Apache-2.0',
        'https://github.com/material-components/material-components-android'),
    _License('RikkaX', 'RikkaApps', 'MIT', 'https://github.com/RikkaApps/RikkaX'),
    _License('LSPosed', 'LSPosed', 'GPL-3.0',
        'https://github.com/LSPosed/LSPosed'),
    _License('LibChecker', 'LibChecker', 'Apache-2.0',
        'https://github.com/LibChecker/LibChecker'),
    _License('Hide-My-Applist', 'Dr-TSNG', 'GPL-3.0',
        'https://github.com/Dr-TSNG/Hide-My-Applist'),
    _License('okhttp', 'square', 'Apache-2.0',
        'https://github.com/square/okhttp'),
    _License('retrofit', 'square', 'Apache-2.0',
        'https://github.com/square/retrofit'),
    _License('glide', 'bumptech', 'Apache-2.0',
        'https://github.com/bumptech/glide'),
    _License('jBCrypt', 'jeremyh', 'Apache-2.0',
        'https://github.com/jeremyh/jBCrypt'),
    _License('flexbox-layout', 'google', 'Apache-2.0',
        'https://github.com/google/flexbox-layout'),
    _License('glide-transformations', 'wasabeef', 'Apache-2.0',
        'https://github.com/wasabeef/glide-transformations'),
    _License('jsoup', 'jhy', 'MIT', 'https://github.com/jhy/jsoup'),
    _License('NineGridImageView', 'plain-dev', 'MIT',
        'https://github.com/plain-dev/NineGridImageView'),
    _License('mojito', 'mikaelzero', 'Apache-2.0',
        'https://github.com/mikaelzero/mojito'),
    _License('CircleIndicator', 'ongakuer', 'Apache-2.0',
        'https://github.com/ongakuer/CircleIndicator'),
    _License('libraries', 'zhaobozhen', 'MIT',
        'https://github.com/zhaobozhen/libraries'),
    _License('dagger', 'google', 'Apache-2.0',
        'https://github.com/google/dagger'),
    _License('SmoothInputLayout', 'AlexMofer', 'Apache-2.0',
        'https://github.com/AlexMofer/SmoothInputLayout'),
  ];
}

class _License {
  const _License(this.name, this.author, this.license, this.url);

  final String name;
  final String author;
  final String license;
  final String url;
}

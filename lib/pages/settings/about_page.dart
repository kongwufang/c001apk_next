import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';

import '../../constants/constants.dart';
import '../../utils/storage_util.dart';
import '../../utils/update_checker.dart';
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

  /// GitHub 头像：用户名 → avatar_url（异步拉 ✓ 拉不到就用图标兜底 ✓）
  ///
  /// 三个人的头像**都从 `api.github.com/users/{user}` 取** ✓
  /// 未登录调用限流 60 次/小时 ✓ 这里只拉 3 次 ✓ 拉一次就够 ✓
  final Map<String, String> _avatars = {};

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
    _fetchGitHub();
  }

  /// 拉 GitHub 资料：**头像**（三个人）+ **bio**（只有维护者）。
  ///
  /// 失败就静默 ✓（网络不通 / 被限流 ⇒ 头像退回图标 ✓ 不打扰用户 ✓）
  ///
  /// ## 为什么要拉头像
  /// 用户反馈：关于页的作者那一栏**只有图标、没有头像** ✗
  /// 而 Android 那边每条是有头像图的 ✓ —— 桌面端从来没拉过 `avatar_url` ✗
  Future<void> _fetchGitHub() async {
    final dio = Dio(BaseOptions(
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
      // GitHub 要求带 UA，否则可能 403 ✗
      headers: {'User-Agent': 'c001apk-flutter'},
    ));
    for (final user in _githubUsers) {
      try {
        final res = await dio.get<Map<String, dynamic>>(
          'https://api.github.com/users/$user',
        );
        final data = res.data;
        if (data == null) continue;
        final avatar = (data['avatar_url'] as String?)?.trim() ?? '';
        final bio = (data['bio'] as String?)?.trim() ?? '';
        if (!mounted) return;
        setState(() {
          if (avatar.isNotEmpty) _avatars[user] = avatar;
          if (user == _maintainer && bio.isNotEmpty) _bio = bio;
        });
      } catch (_) {
        // 单个失败不影响其它 ✓
      }
    }
  }

  /// 要拉头像的三个 GitHub 用户 ✓（与开发者卡里的条目一一对应）
  static const List<String> _githubUsers = [
    'bggRGjQaUbCoE',
    'klxiaoniu',
    'kongwufang',
  ];

  /// 「立即检查更新」。
  ///
  /// 走 Android **同一个接口** ✓（`https://service.houlangs.cn/c001apk/update/`）
  /// 请求头里带 `X-Client-Platform: windows` ✓ 服务端据此下发
  /// `stable-windows.json` 而不是安卓的 `stable.json` ✓
  /// 随机用户 ID 用 `GStorage.userRandomId` ✓（生成一次、永久复用）
  Future<void> _checkUpdate() async {
    SmartDialog.showToast('正在检查更新…');
    final info = await UpdateChecker.fetchUpdate();
    if (!mounted) return;

    if (info == null) {
      SmartDialog.showToast('检查更新失败，请稍后再试');
      return;
    }
    if (!info.isNewer) {
      SmartDialog.showToast('已是最新版本');
      return;
    }

    // 有新版本：版本号 + 更新日志 + 下载线路
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('发现新版本 ${info.versionName}'),
        content: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              if (info.changelog.isNotEmpty)
                Text(info.changelog, style: const TextStyle(fontSize: 13)),
              if (info.lines.isNotEmpty) ...[
                const SizedBox(height: 16),
                const Text('下载地址', style: TextStyle(fontWeight: FontWeight.w600)),
                const SizedBox(height: 6),
                for (final line in info.lines)
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.download, size: 20),
                    title: Text(line.name.isEmpty ? '下载' : line.name),
                    onTap: () {
                      Navigator.of(ctx).pop();
                      Utils.launchURL(line.url);
                    },
                  ),
              ],
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('稍后'),
          ),
        ],
      ),
    );
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
            onTap: _checkUpdate,
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
            avatarUrl: _avatars['bggRGjQaUbCoE'],
          ),
          _divider(),
          _entry(
            context,
            icon: Icons.person_outline,
            title: 'klxiaoniu',
            subtitle: '开发者 / 协作',
            url: 'https://github.com/klxiaoniu',
            avatarUrl: _avatars['klxiaoniu'],
          ),
          _sectionTitle(context, '维护者'),
          _entry(
            context,
            icon: Icons.verified_user,
            title: _maintainer,
            subtitle: '维护者',
            url: 'https://github.com/$_maintainer',
            avatarUrl: _avatars[_maintainer],
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
  ///
  /// [avatarUrl] 传了就用**圆形头像图** ✓ 没传 / 加载失败就退回 [icon] ✓
  Widget _entry(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String subtitle,
    required String url,
    Widget? extra,
    String? avatarUrl,
  }) {
    final hasAvatar = avatarUrl != null && avatarUrl.isNotEmpty;
    return ListTile(
      leading: CircleAvatar(
        radius: 20,
        // 头像加载失败时自动退回图标 ✓（不会留一个破图 ✗）
        backgroundImage: hasAvatar ? NetworkImage(avatarUrl) : null,
        onBackgroundImageError: hasAvatar ? (_, __) {} : null,
        child: hasAvatar ? null : Icon(icon, size: 20),
      ),
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

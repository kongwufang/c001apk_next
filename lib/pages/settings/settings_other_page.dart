import 'package:flutter/material.dart';

import '../../components/dialog.dart';
import '../../utils/cache_util.dart';

/// 设置 → **其他**。
///
/// 照抄 Android `res/xml/settings.xml` 的 `other` 屏 —— 里面只有一项
/// 「清理缓存」。（Android 原注释：`clear_cache` 的字符串就是「清理缓存」。）
class SettingsOtherPage extends StatefulWidget {
  const SettingsOtherPage({super.key});

  @override
  State<SettingsOtherPage> createState() => _SettingsOtherPageState();
}

class _SettingsOtherPageState extends State<SettingsOtherPage> {
  String _cacheSize = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final res = await CacheManage().loadApplicationCache();
    if (!mounted) return;
    setState(() => _cacheSize = res);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('其他')),
      body: ListView(
        physics: const AlwaysScrollableScrollPhysics(
          parent: BouncingScrollPhysics(),
        ),
        children: [
          ListTile(
            leading: const Icon(Icons.cleaning_services_outlined),
            title: const Text('清理缓存'),
            subtitle: _cacheSize.isNotEmpty ? Text(_cacheSize) : null,
            onTap: () async {
              await _load();
              if (!context.mounted) return;
              showDialog<void>(
                context: context,
                builder: (context) => ClearDialog(
                  cacheSize: _cacheSize,
                  onClearCache: () async {
                    if (await CacheManage().clearCacheAll()) {
                      await _load();
                    }
                  },
                ),
              );
            },
          ),
        ],
      ),
    );
  }
}

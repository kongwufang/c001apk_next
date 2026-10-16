import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';

import '../../components/network_image.dart';
import '../../logic/model/offline/offline_feed.dart';
import '../../logic/repo/offline_cache_repo.dart';
import '../../utils/date_util.dart';
import '../../utils/extensions.dart';

/// 离线缓存列表：用户自己存下来的动态 / 图文，断网也能翻。
///
/// 对应 Android `ui/offline/OfflineCacheActivity.kt` + `item_offline_cache.xml`。
///
/// 点条目进的是**同一个详情页**，只是带上 `offline=true` ——
/// 那边会跳过所有网络请求、只读本机存档（见 `FeedController.offline`）。
class OfflineCachePage extends StatefulWidget {
  const OfflineCachePage({super.key});

  @override
  State<OfflineCachePage> createState() => _OfflineCachePageState();
}

class _OfflineCachePageState extends State<OfflineCachePage> {
  List<OfflineFeedEntity> _list = [];

  @override
  void initState() {
    super.initState();
    _reload();
    // 「正在缓存 N 条…」跟着 repo 的 Rx 走
    ever(OfflineCacheRepo.savingIds, (_) {
      if (mounted) setState(() {});
    });
  }

  void _reload() {
    setState(() => _list = OfflineCacheRepo.loadAll());
  }

  /// 删除单条：二次确认（文案取自 Android `offline_cache_delete_*`）
  Future<void> _confirmDelete(OfflineFeedEntity item) async {
    final ok = await Get.dialog<bool>(
      AlertDialog(
        title: const Text('删除离线缓存'),
        content: const Text('删除后这条动态的正文、图片和评论都会从本机清掉，确定删除？'),
        actions: [
          TextButton(onPressed: () => Get.back(result: false), child: const Text('取消')),
          FilledButton(onPressed: () => Get.back(result: true), child: const Text('确定')),
        ],
      ),
    );
    if (ok != true) return;
    await OfflineCacheRepo.delete(item.fid);
    _reload();
  }

  /// 清空全部：二次确认（文案取自 Android `offline_cache_clear*`）
  Future<void> _confirmClearAll() async {
    final ok = await Get.dialog<bool>(
      AlertDialog(
        title: const Text('清空全部'),
        content: const Text('清空后所有离线缓存的正文、图片和评论都会从本机清掉，确定清空？'),
        actions: [
          TextButton(onPressed: () => Get.back(result: false), child: const Text('取消')),
          FilledButton(onPressed: () => Get.back(result: true), child: const Text('确定')),
        ],
      ),
    );
    if (ok != true) return;
    await OfflineCacheRepo.deleteAll();
    _reload();
    SmartDialog.showToast('已清空离线缓存'); // offline_cache_cleared
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final savingCount = OfflineCacheRepo.savingIds.length;

    return Scaffold(
      appBar: AppBar(
        title: const Text('离线缓存'), // offline_cache
        actions: [
          // 空列表时「清空全部」没意义，直接收起来（Android 同此）
          if (_list.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.delete_sweep_outlined),
              tooltip: '清空全部',
              onPressed: _confirmClearAll,
            ),
        ],
      ),
      body: Column(
        children: [
          if (savingCount > 0)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: [
                  const SizedBox(
                    width: 14,
                    height: 14,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                  const SizedBox(width: 8),
                  // offline_cache_saving_count
                  Text('正在缓存 $savingCount 条…',
                      style: TextStyle(
                          fontSize: 13, color: colorScheme.primary)),
                ],
              ),
            ),

          Expanded(
            child: _list.isEmpty
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(32),
                      // offline_cache_empty
                      child: Text(
                        '还没有离线缓存的动态，去动态详情页「收藏到」面板里点「离线缓存」',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                            fontSize: 13,
                            color: colorScheme.onSurfaceVariant),
                      ),
                    ),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.symmetric(vertical: 6),
                    itemCount: _list.length,
                    itemBuilder: (_, i) => _item(_list[i]),
                  ),
          ),
        ],
      ),
    );
  }

  /// 单条存档。布局对应 Android `item_offline_cache.xml`：
  /// 封面 56×56dp / 标题 15sp 单行 / 摘要 12sp 两行 / 信息行 11sp 单行 / 删除按钮 36×36dp
  Widget _item(OfflineFeedEntity item) {
    final colorScheme = Theme.of(context).colorScheme;
    final hasTitle = !item.title.isNullOrEmpty;

    // info = 发布时间的相对描述 · 含 N 条评论 · 离线于<时间>
    // （Android `OfflineCacheAdapter` 的 buildString）
    final info = StringBuffer();
    if (item.dateline > 0) {
      info.write(DateUtil.fromToday(item.dateline));
    }
    if (item.savedReplyCount > 0) {
      if (info.isNotEmpty) info.write(' · ');
      info.write('含 ${item.savedReplyCount} 条评论'); // offline_cache_reply_count
    }
    if (info.isNotEmpty) info.write(' · ');
    // savedAt 是本机毫秒，喂给 fromToday 前要换算成秒
    info.write('离线于${DateUtil.fromToday(item.savedAt ~/ 1000)}');

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      clipBehavior: Clip.antiAlias,
      color: colorScheme.surfaceContainer,
      elevation: 0,
      child: InkWell(
        onTap: () => Get.toNamed('/feed/${
          item.fid
        }', arguments: {'offline': true}),
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 没有封面时用占位（保持卡片高度一致，Android 用 INVISIBLE 同理）
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: SizedBox(
                  width: 56,
                  height: 56,
                  child: item.cover.isNullOrEmpty
                      ? Container(
                          color: colorScheme.surfaceContainerHigh,
                          child: Icon(Icons.article_outlined,
                              color: colorScheme.onSurfaceVariant),
                        )
                      : networkImage(item.cover!, fit: BoxFit.cover),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      hasTitle ? item.title! : (item.message ?? ''),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          fontSize: 15, fontWeight: FontWeight.w500),
                    ),
                    if (hasTitle && !item.message.isNullOrEmpty) ...[
                      const SizedBox(height: 2),
                      Text(
                        item.message!,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                            fontSize: 12,
                            color: colorScheme.onSurfaceVariant),
                      ),
                    ],
                    const SizedBox(height: 4),
                    Text(
                      info.toString(),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          fontSize: 11,
                          color: colorScheme.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
              IconButton(
                iconSize: 20,
                visualDensity: VisualDensity.compact,
                tooltip: '删除',
                icon: const Icon(Icons.delete_outline),
                onPressed: () => _confirmDelete(item),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

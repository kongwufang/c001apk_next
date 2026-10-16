import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';

import '../../components/m3e_progress.dart';
import '../../components/network_image.dart';
import '../../logic/model/history/hit_history.dart';
import '../../logic/network/network_repo.dart';
import '../../utils/date_util.dart';
import '../../utils/extensions.dart';
import '../../utils/utils.dart';

/// 浏览历史（**云端**）。
///
/// 对应 Android `ui/history/HistoryActivity.kt` + `HistoryViewModel.kt` +
/// `item_history_cloud.xml`。
///
/// ## ★ 为什么是「云端」而不是本地
/// Android 端 2026-09-19 由**本地 Room 历史改为酷安云端历史**
/// （`GET /v6/user/hitHistoryList`）。原注释：
/// > 浏览记录由服务端在打开详情（feed/topic/product/apk detail）时**自动写入**，
/// > 客户端无需（也没有）显式上报接口（已实测验证）。
///
/// 所以这里**没有「上报浏览记录」的代码** —— 打开过详情服务端就记下了。
///
/// ## ★ 分页参数照抄官方
/// 首页只带 `page`；后续页带**首页首条**的 id 当 `firstItem` +
/// **上一页末条**的 id 当 `lastItem`。
class CloudHistoryPage extends StatefulWidget {
  const CloudHistoryPage({super.key});

  @override
  State<CloudHistoryPage> createState() => _CloudHistoryPageState();
}

class _CloudHistoryPageState extends State<CloudHistoryPage> {
  final _scroll = ScrollController();

  List<HitHistoryData> _list = [];
  bool _initialLoading = true;
  bool _loading = false;
  bool _isEnd = false;

  int _page = 1;
  String? _firstItem;
  String? _lastItem;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(() {
      if (_scroll.position.pixels >=
          _scroll.position.maxScrollExtent - 300) {
        _loadMore();
      }
    });
    _refresh();
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  void _refresh() {
    _page = 1;
    _firstItem = null;
    _lastItem = null;
    _isEnd = false;
    _load();
  }

  void _loadMore() {
    if (_isEnd || _loading || _initialLoading) return;
    _load();
  }

  Future<void> _load() async {
    if (_loading) return;
    _loading = true;
    if (_page == 1 && mounted) setState(() => _initialLoading = true);

    List<HitHistoryData>? data;
    try {
      final res = await NetworkRepo.getHitHistoryList(
        page: _page,
        firstItem: _firstItem,
        lastItem: _lastItem,
      );
      final body = res.data;
      if (body is Map) {
        data = HitHistoryListResponse.fromJson(
                Map<String, dynamic>.from(body))
            .data;
      }
    } catch (_) {
      data = null;
    }

    if (!mounted) {
      _loading = false;
      return;
    }

    setState(() {
      if (data != null && data!.isNotEmpty) {
        if (_page == 1) _firstItem = data!.first.id;
        _lastItem = data!.last.id;
        _list = _page == 1 ? data! : [..._list, ...data!];
        _page++;
      } else if (_page == 1) {
        // 首页就空（失败或真没有）
        _list = [];
        _isEnd = true;
        if (data == null) {
          SmartDialog.showToast('加载失败，下拉重试');
        }
      } else {
        _isEnd = true; // 翻到头
      }
      _initialLoading = false;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('浏览历史')),
      body: RefreshIndicator(
        onRefresh: () async => _refresh(),
        child: _initialLoading
            ? const Center(child: M3EWavyProgressIndicator())
            : _list.isEmpty
                ? ListView(
                    // 空态也要能下拉刷新
                    children: const [
                      SizedBox(height: 160),
                      Center(child: Text('暂无浏览历史')),
                    ],
                  )
                : ListView.separated(
                    controller: _scroll,
                    itemCount: _list.length + 1,
                    separatorBuilder: (_, __) => const Divider(
                        height: 1, indent: 74, endIndent: 16),
                    itemBuilder: (_, i) {
                      if (i == _list.length) {
                        return Padding(
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          child: Center(
                            child: _isEnd
                                ? Text(
                                    '没有更多了',
                                    style: TextStyle(
                                      fontSize: 13,
                                      color: Theme.of(context)
                                          .colorScheme
                                          .onSurfaceVariant,
                                    ),
                                  )
                                : const M3EWavyProgressIndicator(size: 32),
                          ),
                        );
                      }
                      return _item(_list[i]);
                    },
                  ),
      ),
    );
  }

  /// 单条。布局对应 Android `item_history_cloud.xml`：
  /// padding 水平 16dp / 垂直 10dp；logo **46×46dp**；
  /// title 15sp 单行（间距 12dp）；desc 12sp 两行（上间距 3dp）；
  /// info 11sp（上间距 3dp）。
  Widget _item(HitHistoryData item) {
    final colorScheme = Theme.of(context).colorScheme;
    // ★ 服务端下发的 title / description **带 HTML 标签**
    //   （如 `<a class="feed-link-tag">#话题#</a>`），要转纯文本
    final title = richToPlain(item.title);
    final desc = richToPlain(item.description);

    // info = 类型名 · 相对时间
    final info = StringBuffer();
    if (!item.typeName.isNullOrEmpty) info.write(item.typeName);
    final dateline = item.dateline;
    if (dateline != null) {
      if (info.isNotEmpty) info.write(' · ');
      info.write(DateUtil.fromToday(dateline));
    }

    final url = item.url ?? '';

    return InkWell(
      onTap: url.isEmpty ? null : () => Utils.onOpenLink(url, title),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (!item.logo.isNullOrEmpty)
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: SizedBox(
                  width: 46,
                  height: 46,
                  child: networkImage(item.logo!, fit: BoxFit.cover),
                ),
              ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (title.isNotEmpty)
                    Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 15),
                    ),
                  if (desc.isNotEmpty) ...[
                    const SizedBox(height: 3),
                    Text(
                      desc,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          fontSize: 12, color: colorScheme.onSurfaceVariant),
                    ),
                  ],
                  if (info.isNotEmpty) ...[
                    const SizedBox(height: 3),
                    Text(
                      info.toString(),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          fontSize: 11, color: colorScheme.onSurfaceVariant),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

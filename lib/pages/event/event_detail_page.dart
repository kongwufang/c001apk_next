import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart';

import '../../logic/model/feed/datum.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/state/loading_state.dart';
import '../../utils/utils.dart';
import '../../components/network_image.dart';
import '../../components/m3e_progress.dart';

/// 众测 / 活动详情（原生）。
///
/// 对应 Android `ui/event/EventDetailActivity.kt`（96 行）
/// + `res/layout/activity_event_detail.xml`。
///
/// ## 为什么必须原生化
/// Android 注释：
/// > 众测入口的 url 是 `/event/<id>`，但 `www.coolapk.com/event/<id>` 只是
/// > 一个「活动分享」落地页（**引导下载 App**），没有 H5 详情。
/// > 真正的详情数据走原生接口 `GET /v6/event/detail?id=<id>`。
///
/// 也就是说：不原生化的话，点众测卡片会跳到一个「请下载酷安 App」的页面
/// —— release-notes 里那条「众测详情原生化，不再误跳下载页」说的就是它。
///
/// ## 布局（照抄 XML）
/// | 元素 | 规格 |
/// |---|---|
/// | 顶栏 | 「众测详情」 |
/// | 横幅 | `logo`，非空才显示 |
/// | 标题 | **20sp 加粗**、内边距 16 |
/// | 赞助方 | **14sp**、上间距 8 = 「赞助方：xxx」 |
/// | 报名人数 | **14sp**、上间距 4 = 「报名人数：xxx」 |
/// | 报名时间 | **14sp**、上间距 4 = 「报名时间：yyyy-MM-dd ~ yyyy-MM-dd」 |
/// | 规则 | **15sp**、上间距 16 = `notice_rule` |
/// | 奖品 | **15sp**、上间距 16 = 「奖品：A、B」 |
/// | 申请按钮 | 外边距 16/24/16/24，点击开 `action_url` |
class EventDetailPage extends StatefulWidget {
  const EventDetailPage({super.key, required this.id});

  final String id;

  @override
  State<EventDetailPage> createState() => _EventDetailPageState();
}

class _EventDetailPageState extends State<EventDetailPage> {
  LoadingState _state = LoadingState.loading();

  static final _dateFmt = DateFormat('yyyy-MM-dd');

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _state = LoadingState.loading());
    final response = await NetworkRepo.getDataFromUrl(
      url: '/v6/event/detail',
      data: {'id': widget.id},
    );
    if (!mounted) return;
    setState(() => _state = response);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('众测详情')),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    final state = _state;
    if (state is Success) {
      // `getDataFromUrl` 的返回体恒为 `Datum`
      final datum = state.response;
      final raw = datum is Datum ? datum.raw : null;
      return _body(context, raw ?? const {});
    }
    if (state is Empty) {
      return GestureDetector(
        onTap: _load,
        child: const Center(child: Text('暂无内容，点一下重试')),
      );
    }
    if (state is Error) {
      return GestureDetector(
        onTap: _load,
        child: Center(child: Text(state.errMsg)),
      );
    }
    return const Center(child: M3EWavyProgressIndicator());
  }

  Widget _body(BuildContext context, Map<String, dynamic> data) {
    final colorScheme = Theme.of(context).colorScheme;

    String str(String key) => data[key]?.toString() ?? '';

    final logo = str('logo');
    final title = str('title');

    // 赞助方：`sponsor_user` 优先，其次 `sponsorUser[0].username`
    var sponsor = str('sponsor_user');
    if (sponsor.isEmpty) {
      final list = data['sponsorUser'];
      if (list is List && list.isNotEmpty && list.first is Map) {
        sponsor = (list.first as Map)['username']?.toString() ?? '';
      }
    }

    // 报名人数：`show_reg_num` 优先
    final regNum = str('show_reg_num').isNotEmpty ? str('show_reg_num') : str('reg_num');

    // 报名时间
    final start = data['time_reg_start'];
    final end = data['time_reg_end'];
    String regTime = '';
    if (start is num && end is num) {
      String fmt(num ts) =>
          _dateFmt.format(DateTime.fromMillisecondsSinceEpoch((ts * 1000).toInt()));
      regTime = '报名时间：${fmt(start)} ~ ${fmt(end)}';
    }

    // 奖品：`sponsorPrize` 数组的 title 用「、」连
    final prizes = <String>[];
    final prizeList = data['sponsorPrize'];
    if (prizeList is List) {
      for (final p in prizeList) {
        if (p is Map) {
          final t = p['title']?.toString() ?? '';
          if (t.isNotEmpty) prizes.add(t);
        }
      }
    }

    final rule = str('notice_rule');
    final applyUrl = str('action_url');

    return ListView(
      children: [
        if (logo.isNotEmpty)
          networkImage(
            logo,
            width: double.infinity,
            fit: BoxFit.fitWidth,
            borderRadius: null,
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
          child: Text(
            title,
            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
          ),
        ),
        if (sponsor.isNotEmpty)
          _line('赞助方：$sponsor', top: 8),
        if (regNum.isNotEmpty)
          _line('报名人数：$regNum', top: 4),
        if (regTime.isNotEmpty)
          _line(regTime, top: 4),
        if (rule.isNotEmpty)
          _line(rule, top: 16, fontSize: 15),
        if (prizes.isNotEmpty)
          _line('奖品：${prizes.join('、')}', top: 16, fontSize: 15),
        if (applyUrl.isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 24, 16, 24),
            child: FilledButton(
              onPressed: () => Utils.onOpenLink(applyUrl, title),
              child: const Text('申请'),
            ),
          ),
        // 底部留白，免得最后一行贴边
        SizedBox(height: 24, child: ColoredBox(color: colorScheme.surface)),
      ],
    );
  }

  Widget _line(String text, {required double top, double fontSize = 14}) {
    return Padding(
      padding: EdgeInsets.fromLTRB(16, top, 16, 0),
      child: Text(text, style: TextStyle(fontSize: fontSize)),
    );
  }
}

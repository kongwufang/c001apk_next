import 'package:flutter/material.dart';

/// 点评表单的**数据快照**（由 [RatingPanel] 通过 `onChanged` 抛出）。
class RatingFormValue {
  const RatingFormValue({
    required this.overall,
    required this.itemStars,
    required this.good,
    required this.bad,
    required this.buyStatus,
  });

  /// 总体评分（**1~5 星**，原值提交到 `rating_score_1`）
  final int overall;

  /// 各子项的星数（1~5）；提交时要 **× 2** 变成 0~10 量纲
  final List<int> itemStars;

  final String good;
  final String bad;
  final bool buyStatus;

  /// 总体和**每个**子项都打过分才算完整。
  ///
  /// Android 原文：
  /// > 6 个子项必须全部 >0，漏一个或传 0 会回「-48 子项xx没有评分」。
  bool get valid =>
      overall > 0 && itemStars.isNotEmpty && itemStars.every((e) => e > 0);
}

/// 点评评分面板：总体评分 + 各子项评分 + 优点/缺点 + 是否已购买。
///
/// 对应 Android `ReplyActivity` 的 `type == "rating"` 分支
/// （`initRatingItems()` + `activity_reply.xml` 的 `ratingLayout`）。
///
/// ## ★ 分值量纲（实测结论，容易搞错）
/// Android 原文：
/// > `rating_score_1` 是 **1~5 星**，原值提交；实测 **6 及以上一律回「请正确打分」**，
/// > 所以总体分只能用 5 星控件。
/// > `v4_score_item_1..n` 才是 **0~10 量纲，每星 2 分**（真机提交 10/8/6/4/2），
/// > 且**子项必须全部 >0**，漏一个或传 0 会回「-48 子项xx没有评分」。
///
/// 所以面板里所有控件都是 **5 星**，提交时：总体原样、子项 ×2。
class RatingPanel extends StatefulWidget {
  const RatingPanel({
    super.key,
    required this.target,
    required this.items,
    required this.onChanged,
  });

  /// 被点评的产品名（Android 的 `ratingTarget`）
  final String target;

  /// 子项定义：`[{id: 'v4_score_item_1', name: '续航', …}, …]`
  final List<Map<String, dynamic>> items;

  final ValueChanged<RatingFormValue> onChanged;

  @override
  State<RatingPanel> createState() => _RatingPanelState();
}

class _RatingPanelState extends State<RatingPanel> {
  int _overall = 0;
  late final List<int> _itemStars = List<int>.filled(widget.items.length, 0);
  bool _buyStatus = false;
  final _goodController = TextEditingController();
  final _badController = TextEditingController();

  @override
  void dispose() {
    _goodController.dispose();
    _badController.dispose();
    super.dispose();
  }

  void _notify() {
    widget.onChanged(RatingFormValue(
      overall: _overall,
      itemStars: List<int>.from(_itemStars),
      good: _goodController.text.trim(),
      bad: _badController.text.trim(),
      buyStatus: _buyStatus,
    ));
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
      color: colorScheme.surface,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          // 被点评的产品
          Text(
            widget.target,
            style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 10),
          // 总体评分
          _starRow(
            context,
            label: '总体评分',
            value: _overall,
            onChanged: (v) {
              setState(() => _overall = v);
              _notify();
            },
          ),
          // 各子项
          for (var i = 0; i < widget.items.length; i++)
            _starRow(
              context,
              label: widget.items[i]['name']?.toString() ?? '',
              value: _itemStars[i],
              onChanged: (v) {
                setState(() => _itemStars[i] = v);
                _notify();
              },
            ),
          const SizedBox(height: 6),
          _textField(_goodController, '优点'),
          const SizedBox(height: 8),
          _textField(_badController, '缺点'),
          const SizedBox(height: 4),
          Row(
            children: [
              Checkbox(
                value: _buyStatus,
                onChanged: (v) {
                  setState(() => _buyStatus = v ?? false);
                  _notify();
                },
              ),
              const Text('已购买', style: TextStyle(fontSize: 14)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _textField(TextEditingController controller, String hint) {
    return TextField(
      controller: controller,
      onChanged: (_) => _notify(),
      maxLines: 2,
      minLines: 1,
      style: const TextStyle(fontSize: 14),
      decoration: InputDecoration(
        hintText: hint,
        isDense: true,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
      ),
    );
  }

  /// 一行「名称 + 5 颗星」
  Widget _starRow(
    BuildContext context, {
    required String label,
    required int value,
    required ValueChanged<int> onChanged,
  }) {
    final colorScheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(
            width: 80,
            child: Text(label, style: const TextStyle(fontSize: 14)),
          ),
          for (var i = 1; i <= 5; i++)
            IconButton(
              // 点击区撑大一点，桌面端好点
              padding: const EdgeInsets.symmetric(horizontal: 2),
              constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
              onPressed: () => onChanged(i),
              icon: Icon(
                i <= value ? Icons.star : Icons.star_border,
                size: 22,
                color: i <= value ? const Color(0xFFFFB300) : colorScheme.outline,
              ),
            ),
          const SizedBox(width: 6),
          Text(
            value == 0 ? '未评分' : '$value 星',
            style: TextStyle(fontSize: 12, color: colorScheme.outline),
          ),
        ],
      ),
    );
  }
}

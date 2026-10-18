import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';

import '../logic/network/api_error.dart';

/// 通用错误占位（整页 / 列表底部都能用 ✓）。
///
/// ## 为什么要它
/// 原来 `common_body.dart` 的 `_bodyState` 只有一行 `Text` ✗
/// 用户反馈：「什么都不显示，就一个刷新按钮」✗
///
/// ## 现在
/// ```
///            ⚠️
///    内容不存在（可能已被删除）        ← 主文案（来自 JSON 的 message ✓）
///   HTTP 404 · /v6/feed/detail       ← 次要信息（状态码 + 路径）
///
///        [复制详情]  [重试]
/// ```
///
/// ## 文案约定
/// `errMsg` 可能带换行 ✓ —— 第一行当标题、其余合并成次要信息 ✓
/// 这样 `LoadingState.error(String)` 的签名不用改 ✓（跨文件改动风险最小 ✓）
class ErrorView extends StatelessWidget {
  const ErrorView({
    super.key,
    required this.message,
    this.onRetry,
    this.compact = false,
  });

  /// 错误文案（可含换行 ✓ 见类注释）
  final String message;

  /// 重试回调（null ⇒ 不显示按钮 ✓）
  final VoidCallback? onRetry;

  /// 紧凑模式（列表底部用 ✓ 只显示一行 + 重试 ✓）
  final bool compact;

  /// 拆成 (标题, 次要信息)
  (String, String?) _split() {
    final lines = message
        .split('\n')
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    if (lines.isEmpty) return ('出错了', null);
    if (lines.length == 1) return (lines.first, null);
    return (lines.first, lines.skip(1).join(' · '));
  }

  Future<void> _copy(String title, String? meta) async {
    // 尽量用 ApiError 的格式（多带时间与响应片段的话在外面已经拼好了 ✓）
    final text = StringBuffer()
      ..writeln('c001apk 错误详情')
      ..writeln('时间：${DateTime.now().toIso8601String()}')
      ..writeln('标题：$title');
    if (meta != null) text.writeln('位置：$meta');
    await Clipboard.setData(ClipboardData(text: text.toString()));
    SmartDialog.showToast('已复制');
  }

  @override
  Widget build(BuildContext context) {
    final (title, meta) = _split();
    final cs = Theme.of(context).colorScheme;

    if (compact) {
      return GestureDetector(
        onTap: onRetry,
        behavior: HitTestBehavior.opaque,
        child: Container(
          height: 80,
          alignment: Alignment.center,
          padding: const EdgeInsets.all(10),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.error_outline, size: 16, color: cs.error),
              const SizedBox(width: 6),
              Flexible(
                child: Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: cs.error, fontSize: 13),
                ),
              ),
              if (onRetry != null) ...[
                const SizedBox(width: 8),
                Text(
                  '点此重试',
                  style: TextStyle(fontSize: 12, color: cs.primary),
                ),
              ],
            ],
          ),
        ),
      );
    }

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.error_outline, size: 48, color: cs.error),
            const SizedBox(height: 14),
            SelectableText(
              title,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
            ),
            if (meta != null) ...[
              const SizedBox(height: 8),
              SelectableText(
                meta,
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant),
              ),
            ],
            const SizedBox(height: 18),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                OutlinedButton.icon(
                  onPressed: () => _copy(title, meta),
                  icon: const Icon(Icons.copy_all_outlined, size: 18),
                  label: const Text('复制详情'),
                ),
                if (onRetry != null) ...[
                  const SizedBox(width: 12),
                  FilledButton.tonalIcon(
                    onPressed: onRetry,
                    icon: const Icon(Icons.refresh, size: 18),
                    label: const Text('重试'),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// 把 `ApiError` 拼成 `ErrorView` 期望的两行文本 ✓
///
/// （`LoadingState.error(String)` 只能收字符串 ✗ ⇒ 在这里做一次性转换 ✓）
String apiErrorToMessage(ApiError e) {
  final meta = e.meta;
  return meta.isEmpty ? e.title : '${e.title}\n$meta';
}

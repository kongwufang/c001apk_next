import 'package:flutter/material.dart';

import '../logic/state/loading_state.dart';
import 'error_view.dart';
import 'm3e_progress.dart';

Widget footerWidget(LoadingState footerState, Function() onReload) {
  switch (footerState) {
    case Empty():
      return Container(
        height: 80,
        alignment: Alignment.center,
        padding: const EdgeInsets.all(10.0),
        child: const Text('没有更多了'),
      );
    case Error():
      // 紧凑版：图标 + 第一行文案 + 「点此重试」✓（详情看整页的那个 ErrorView）
      return ErrorView(
        message: footerState.errMsg,
        onRetry: () => onReload(),
        compact: true,
      );
    default:
      return Container(
        height: 80,
        alignment: Alignment.center,
        padding: const EdgeInsets.all(10.0),
        child: const M3EWavyProgressIndicator(),
      );
  }
}

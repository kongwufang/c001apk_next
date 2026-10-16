import 'package:flutter/material.dart';

import '../logic/state/loading_state.dart';
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
      return GestureDetector(
        onTap: onReload,
        child: Container(
          height: 80,
          alignment: Alignment.center,
          padding: const EdgeInsets.all(10.0),
          child: Text(footerState.errMsg),
        ),
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

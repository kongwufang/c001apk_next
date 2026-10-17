import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

/// 桌面端键盘快捷键。
///
/// ## 用户要的映射
/// | 键 | 作用 | 作用域 |
/// |---|---|---|
/// | `b` (back) | 返回 | 全局（输入框里不生效）|
/// | `l` (like) | 点赞 / 取消点赞 | 鼠标悬停的帖子 · 详情页 |
/// | `c` (collect) | 弹收藏夹选择（可按数字直选）| 同上 |
/// | `s` (share) | 复制分享链接 | 同上 |
/// | `a` | 跳作者主页 | 同上 |
/// | `s` | **关注 / 取消关注** | 作者主页（覆盖 share）|
/// | `m` | 进入私信 | 作者主页 |
///
/// ## 两层注册表
/// - **hover 层**：列表卡片在鼠标进入时注册、离开时清掉；
/// - **page 层**：详情页 / 作者主页在 `initState` 注册、`dispose` 清掉。
///
/// 查找顺序 `page` → `hover` —— 详情页的 `l` 不该受鼠标在不在正文上影响，
/// 而作者主页的 `s` 要覆盖列表里的 share。
class ShortcutBus {
  ShortcutBus._();

  static const back = 'back';
  static const like = 'like';
  static const collect = 'collect';
  static const share = 'share';
  static const author = 'author';
  static const follow = 'follow';
  static const message = 'message';

  /// 鼠标悬停的那张卡注册的动作
  static final Map<String, VoidCallback> hover = <String, VoidCallback>{};

  /// 当前页面注册的动作（优先于 hover）
  static final Map<String, VoidCallback> page = <String, VoidCallback>{};

  static VoidCallback? find(String action) => page[action] ?? hover[action];

  /// 页面 `dispose` 时清掉自己注册的动作，避免串页
  static void unregisterPage(Iterable<String> actions) {
    for (final a in actions) {
      page.remove(a);
    }
  }

  static void clearHover() => hover.clear();

  /// 动作名 → 键名（给提示文案用）
  static String keyOf(String action) {
    switch (action) {
      case like:
        return 'L';
      case collect:
        return 'C';
      case share:
        return 'S';
      case author:
        return 'A';
      case follow:
        return 'S';
      case message:
        return 'M';
      default:
        return 'B';
    }
  }
}

/// 装在 `MaterialApp.builder` 里的键盘拦截器。
///
/// ★ 在**输入框里**一律不拦：`FocusManager.instance.primaryFocus` 的 widget 是
///   `EditableText` 就认为用户在打字（搜索框 / 回复框 / 收藏夹命名 …）。
class ShortcutScope extends StatefulWidget {
  const ShortcutScope({super.key, required this.child});

  final Widget child;

  @override
  State<ShortcutScope> createState() => _ShortcutScopeState();
}

class _ShortcutScopeState extends State<ShortcutScope> {
  final _focus = FocusNode(debugLabel: 'ShortcutScope');

  /// 打字中就不拦
  bool get _typing {
    final w = FocusManager.instance.primaryFocus?.context?.widget;
    return w is EditableText;
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent) return KeyEventResult.ignored;
    // 带修饰键的一律放行（Ctrl+V / Ctrl+C / Alt+… 交给系统）
    final keys = HardwareKeyboard.instance;
    if (keys.isControlPressed || keys.isAltPressed || keys.isMetaPressed) {
      return KeyEventResult.ignored;
    }
    if (_typing) return KeyEventResult.ignored;

    final k = event.logicalKey;

    // b = 返回（唯一不依赖上下文的）
    if (k == LogicalKeyboardKey.keyB) {
      final back = ShortcutBus.find(ShortcutBus.back);
      if (back != null) {
        back();
        return KeyEventResult.handled;
      }
      // 不在某个页面自己处理，就走路由后退
      if (Navigator.of(context).canPop()) {
        Navigator.of(context).maybePop();
        return KeyEventResult.handled;
      }
      return KeyEventResult.ignored;
    }

    final action = switch (k) {
      LogicalKeyboardKey.keyL => ShortcutBus.like,
      LogicalKeyboardKey.keyC => ShortcutBus.collect,
      LogicalKeyboardKey.keyS => ShortcutBus.share,
      LogicalKeyboardKey.keyA => ShortcutBus.author,
      LogicalKeyboardKey.keyM => ShortcutBus.message,
      _ => null,
    };
    if (action == null) return KeyEventResult.ignored;

    // 作者主页的 `s` 是「关注」—— 那边会把 follow 注册到 share 这个槽位，
    // 这里再兜一层：share 槽位为空但 follow 有值就用 follow
    var cb = ShortcutBus.find(action);
    if (cb == null && action == ShortcutBus.share) {
      cb = ShortcutBus.find(ShortcutBus.follow);
    }
    if (cb == null) return KeyEventResult.ignored;
    cb();
    return KeyEventResult.handled;
  }

  @override
  void dispose() {
    _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Focus(
      focusNode: _focus,
      autofocus: true,
      onKeyEvent: _onKey,
      child: widget.child,
    );
  }
}

/// 给卡片用的便捷封装：鼠标进入时注册、离开时清掉。
///
/// 用法（`feed_card.dart` 里）：
/// ```dart
/// ShortcutHover(
///   actions: {
///     ShortcutBus.like: () => onLike!(data.id, data.userAction?.like),
///     ShortcutBus.share: () => Utils.copyText(...),
///   },
///   child: ...,
/// )
/// ```
class ShortcutHover extends StatelessWidget {
  const ShortcutHover({
    super.key,
    required this.actions,
    required this.child,
  });

  final Map<String, VoidCallback?> actions;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      onEnter: (_) {
        ShortcutBus.hover
          ..clear()
          ..addAll({
            for (final e in actions.entries)
              if (e.value != null) e.key: e.value!,
          });
      },
      onExit: (_) => ShortcutBus.clearHover(),
      child: child,
    );
  }
}

/// 页面级注册：`initState` 写入、`dispose` 清掉。
///
/// 详情页 / 作者主页用它 —— 这样 `l` `c` `s` `a` `m` 不依赖鼠标是否停在正文上。
class ShortcutPage extends StatefulWidget {
  const ShortcutPage({
    super.key,
    required this.actions,
    required this.child,
  });

  final Map<String, VoidCallback?> actions;
  final Widget child;

  @override
  State<ShortcutPage> createState() => _ShortcutPageState();
}

class _ShortcutPageState extends State<ShortcutPage> {
  @override
  void initState() {
    super.initState();
    ShortcutBus.page
      ..clear()
      ..addAll({
        for (final e in widget.actions.entries)
          if (e.value != null) e.key: e.value!,
      });
  }

  @override
  void dispose() {
    ShortcutBus.unregisterPage(widget.actions.keys);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

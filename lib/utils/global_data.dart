import 'package:get/get.dart';

class GlobalData {
  // 私有构造函数
  GlobalData._();

  // 单例实例
  static final GlobalData _instance = GlobalData._();

  // 获取全局实例
  factory GlobalData() => _instance;

  /// 登录态 / 用户信息变化后，让**整棵界面**重建一次。
  ///
  /// `GlobalData` 是普通单例，没有可监听的信号 ⇒ 改了 [isLogin] / [username]
  /// 之后界面**不会自己重建** ✗
  /// 所以登录成功、退出登录、换头像这类「全局可见状态」变了，都调它一次。
  ///
  /// 用 `Get.forceAppUpdate()`（`transitionDuration` 那边也是这个手段）。
  void refresh() {
    try {
      Get.forceAppUpdate();
    } catch (_) {
      // 还没初始化完（比如启动阶段读本地登录态）就忽略
    }
  }

  // ignore: non_constant_identifier_names
  String SESSID = '';
  bool isLogin = false;
  String uid = '';
  String username = '';
  String token = '';
}

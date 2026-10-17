class GlobalData {
  // 私有构造函数
  GlobalData._();

  // 单例实例
  static final GlobalData _instance = GlobalData._();

  // 获取全局实例
  factory GlobalData() => _instance;

  // ignore: non_constant_identifier_names
  String SESSID = '';
  bool isLogin = false;
  String uid = '';
  String username = '';
  String token = '';

  /// ★ 启动时用 [token] 调 `checkLoginInfo` 换来的**第二凭据**。
  ///
  /// 早期研究结论（`_rev/win_ref/verify_token_live.py` + `DETAIL_5902.md:145`）：
  /// `login token` 与 `refreshToken` 在业务接口上**表现逐项一致** ——
  /// 它不是 OAuth 那种「刷新令牌」，而是**第二个等效凭据** ✓
  ///
  /// 所以：**非空时业务请求优先用它**（见 `interceptor.dart` 拼 Cookie 那行）。
  /// 生命周期 = 本次进程；下次启动重新换一次。
  String refreshToken = '';
}

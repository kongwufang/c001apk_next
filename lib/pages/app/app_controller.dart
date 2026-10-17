import 'package:get/get.dart' hide Response;

import '../../logic/model/feed/datum.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/state/loading_state.dart';
import '../../pages/common/common_controller.dart';
import '../../utils/storage_util.dart';

class AppController extends CommonController {
  AppController({required this.packageName});
  final String packageName;

  // ⚠️ 这几个**不能写 `late`**（原来写的是 `late final`）：
  //   它们只在 `_getAppData` 的 `if (response is Success)` 分支里赋值，
  //   请求失败时没人赋值 —— 而 UI 读它们会抛 `LateInitializationError`
  //   （`late` 字段在赋值前被读是**抛异常**，不是返回 null）✗
  //   而且 `late final` 只能赋一次，`regetData()` 刷新后值不会更新 ✗
  //
  //   同类 bug 的实证见 `topic_content.dart` 的 `_topicOrderController`
  //   （产品页除「讨论」外所有 tab 整页空白）。
  String? id;
  int? commentStatus;
  String? commentStatusText;
  String? entityType;

  Rx<LoadingState> appState = LoadingState.loading().obs;
  String? appName;

  RxDouble scrollRatio = 0.0.obs;

  bool isBlocked = false;
  bool isFollow = false;

  Future<void> _getAppData() async {
    LoadingState response = await NetworkRepo.getAppInfo(id: packageName);
    if (response is Success) {
      Datum data = response.response as Datum;
      id = data.id.toString();
      commentStatus = data.commentStatus;
      commentStatusText = data.commentStatusText;
      entityType = data.entityType ?? '';
      appName = data.title;
      isFollow = data.userAction?.follow == 1;
      isBlocked = GStorage.checkTopic(appName!);
      appState.value = LoadingState.success(response.response);
    } else {
      appState.value = response;
    }
  }

  void regetData() {
    appState.value = LoadingState.loading();
    _getAppData();
  }

  @override
  void onInit() {
    super.onInit();
    _getAppData();
  }

  @override
  void handleGetFollow() {
    isFollow = !isFollow;
  }

  @override
  Future<LoadingState> customGetData() {
    // TODO: implement customGetData
    throw UnimplementedError();
  }
}

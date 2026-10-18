import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart' hide Response, FormData;

import '../../logic/model/feed/datum.dart';
import '../../logic/model/feed/user_action.dart';
import '../../logic/model/login/login_response.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/state/loading_state.dart';
import '../../pages/home/return_top_controller.dart';
import '../../utils/extensions.dart';
import '../../utils/global_data.dart';
import '../../utils/stick_top.dart';

abstract class CommonController extends GetxController {
  int page = 1;
  String? firstItem;
  String? lastItem;
  bool isLoading = false;
  bool isEnd = false;

  /// 上一次「加载更多」开始的时间 —— 用来节流。
  ///
  /// ## 为什么需要
  /// 列表底部的 footer 是在 **build 里**触发翻页的（见 `common_body.dart` 的
  /// `footer()`）：只要它被构建就视作「翻到底了」⇒ 立刻请求下一页 ✗
  /// 结果：列表不满一屏 / 内容很短时，底部 footer **一直在视野里** ✗
  /// ⇒ 一页接一页地连发 ✗
  ///
  /// 实测（2026-10-18，消息中心）：1 秒内发出 30+ 个分页请求
  /// （4 个接口 × page=2…8 ✗）—— 白白耗流量，也更容易被风控盯上 ✗
  DateTime _lastPageLoadAt = DateTime.fromMillisecondsSinceEpoch(0);

  /// 两次「加载更多」之间的最小间隔
  static const Duration _minPageGap = Duration(milliseconds: 350);

  Rx<LoadingState> loadingState = LoadingState.loading().obs;
  Rx<LoadingState> footerState = LoadingState.empty().obs;
  GlobalKey<RefreshIndicatorState>? refreshKey;
  ScrollController? scrollController;
  ReturnTopController? returnTopController;

  List<Datum>? handleResponse(List<Datum> dataList) {
    return null;
  }

  List<Datum>? handleUnique(List<Datum> dataList) {
    return dataList.unique((data) => data.entityId);
  }

  LoadingState? handleExtraResponse() {
    return null;
  }

  Future<LoadingState> customGetData();

  Future<void> onGetData([bool isRefresh = true]) async {
    if (isLoading) return;
    // ★ 翻页节流：footer 是在 build 里触发翻页的，列表不满一屏时会连发 ✗
    //   这里强制两次翻页至少隔 _minPageGap ✓（下拉刷新不受影响 ✓）
    if (!isRefresh) {
      final gap = DateTime.now().difference(_lastPageLoadAt);
      if (gap < _minPageGap) {
        await Future<void>.delayed(_minPageGap - gap);
      }
    }
    if (!isLoading) {
      _lastPageLoadAt = DateTime.now();
      isLoading = true;
      LoadingState response = await customGetData();
      if (response is Success) {
        List<Datum> dataList = response.response;
        firstItem ??= dataList.firstOrNull?.id.toString();
        lastItem = dataList.lastOrNull?.id.toString();
        List<Datum>? responseList = handleResponse(dataList);
        if (responseList != null) {
          dataList = responseList;
        }
        if (!isRefresh) {
          dataList = (loadingState.value as Success).response + dataList;
        }
        List<Datum>? uniqueList = handleUnique(dataList);
        if (uniqueList != null) {
          dataList = uniqueList;
        }
        loadingState.value = LoadingState.success(dataList);
        page++;
      } else {
        isEnd = true;
        LoadingState? extraResponse = handleExtraResponse();
        if (extraResponse != null) {
          response = extraResponse;
        }
        if (isRefresh) {
          loadingState.value = response;
        } else {
          footerState.value = response;
        }
      }
      isLoading = false;
    }
  }

  void onReset() {
    page = 1;
    isEnd = false;
    firstItem = null;
    lastItem = null;
    footerState.value = LoadingState.loading();
  }

  void setFooterState(LoadingState footerState) {
    this.footerState.value = footerState;
  }

  void setLoadingState(LoadingState loadingState) {
    this.loadingState.value = loadingState;
  }

  Future<void> animateToTop() async {
    scrollController?.animToTop();
    returnTopController?.setIndex(999);
    refreshKey?.currentState?.show();
  }

  void onBlock(dynamic uid) {
    List<Datum> dataList = (loadingState.value as Success).response;
    loadingState.value = LoadingState.success(
        dataList.where((item) => item.uid != uid).toList());
  }

  Future<void> postLikeDeleteFollow(
    dynamic id,
    dynamic fid, {
    bool isFollow = false,
    bool isFeed = false,
    bool isReply = false,
    bool isNoti = false,
    bool isProduct = false,
  }) async {
    String url = isFeed
        ? '/v6/feed/deleteFeed'
        : isReply
            ? '/v6/feed/deleteReply'
            : isNoti
                ? '/v6/notification/delete'
                : '/v6/product/changeFollowStatus';
    try {
      Response response = await NetworkRepo.postLikeDeleteFollow(
        url,
        id: isProduct ? null : id,
        data: isProduct
            ? FormData.fromMap({'id': id, 'status': isFollow ? 0 : 1})
            : null,
      );
      LoginResponse data = LoginResponse.fromJson(response.data);
      if (!data.message.isNullOrEmpty) {
        SmartDialog.showToast(data.message!);
        if (isProduct) {
          handleGetFollow();
        }
      } else if (data.data != null) {
        List<Datum> dataList = (loadingState.value as Success).response;
        if (fid != null) {
          dataList = dataList.map((data) {
            if (data.id == fid) {
              return data
                ..replyRows =
                    data.replyRows!.where((data) => data.id != id).toList();
            } else {
              return data;
            }
          }).toList();
        } else {
          dataList = dataList.where((data) => data.id != id).toList();
        }
        loadingState.value = LoadingState.success(dataList);
        SmartDialog.showToast(data.data!);
      }
    } catch (e) {
      debugPrint(e.toString());
    }
  }

  bool handleLike(dynamic like, dynamic likenum) {
    return false;
  }

  Future<void> onLike(
    dynamic id,
    dynamic like, {
    bool isFeed = false,
    bool isReply = false,
  }) async {
    try {
      String isLike = isFeed
          ? (like == 1 ? 'unlike' : 'like')
          : (like == 1 ? 'unLikeReply' : 'likeReply');
      Response response =
          await NetworkRepo.postLikeDeleteFollow('/v6/feed/$isLike', id: id);
      LoginResponse datum = LoginResponse.fromJson(response.data);
      if (!datum.message.isNullOrEmpty) {
        SmartDialog.showToast(datum.message!);
      } else {
        if (isFeed && handleLike(like, datum.data['count'])) {
          return;
        }
        List<Datum> dataList = (loadingState.value as Success).response;
        dataList = dataList.map((data) {
          if (data.id == id) {
            return data
              ..likenum = isFeed ? datum.data['count'] : datum.data
              ..userAction = UserAction(like: like == 1 ? 0 : 1);
          } else {
            return data;
          }
        }).toList();
        loadingState.value = LoadingState.success(dataList);
      }
    } catch (e) {
      debugPrint(e.toString());
    }
  }


  /// 个人主页**置顶 / 取消置顶**，并在成功后**本地即时生效**。
  ///
  /// 对齐 Android `BaseAppViewModel.onPostStickTop`：
  /// - 未登录 / uid 为空直接返回；
  /// - 已置顶 → 调取消接口，否则调置顶接口（见 `NetworkRepo.postStickTop`）；
  /// - `message` 非空 → 只弹提示（失败）；
  /// - 否则**本地把小标记翻过来**，并把**置顶项排到列表最前**
  ///   （「和主页的服务端顺序一致」——Android 原注释）。
  ///
  /// 之前只在 `_MorePanel` 里调接口 + toast，置顶后界面毫无变化，
  /// 徽标和顺序都得刷新才更新 —— 这里补上。
  Future<void> postStickTop(dynamic id, bool isStickTop) async {
    final myUid = GlobalData().uid;
    if (myUid.isEmpty) return;
    try {
      final response = await NetworkRepo.postStickTop(
        feedId: id.toString(),
        isStickTop: isStickTop,
        myUid: myUid,
      );
      final data = LoginResponse.fromJson(response.data);
      if (!data.message.isNullOrEmpty) {
        SmartDialog.showToast(data.message!);
        return;
      }
      final state = loadingState.value;
      if (state is! Success) return;
      // 列表变换抽在 `utils/stick_top.dart` 里（纯函数，有单元测试）——
      // 翻面 + 刚置顶时前移 + 不丢项，都在那儿验证过。
      final dataList = applyStickTop(
        state.response as List<Datum>,
        id,
        isStickTop,
      );
      loadingState.value = LoadingState.success(dataList);
      final ok = data.data;
      if (ok != null) SmartDialog.showToast(ok);
    } catch (e) {
      debugPrint(e.toString());
    }
  }

  void handleGetFollow() {}

  Future<void> onGetFollow(
    bool isFollow,
    String url, {
    dynamic tag,
    dynamic id,
  }) async {
    try {
      Response response = await NetworkRepo.getFollow(url, tag: tag, id: id);
      LoginResponse data = LoginResponse.fromJson(response.data);
      if (!data.message.isNullOrEmpty) {
        SmartDialog.showToast(data.message!);
        if (tag != null) {
          handleGetFollow();
        }
      } else if (data.data != null) {
        SmartDialog.showToast(isFollow ? '取消关注成功' : '关注成功');
        handleGetFollow();
      }
    } catch (e) {
      debugPrint(e.toString());
    }
  }

  void onPM(dynamic id) {
    List<Datum> dataList = (loadingState.value as Success).response;
    dataList = dataList.map((data) {
      if (data.id == id) {
        return data..unreadNum = null;
      } else {
        return data;
      }
    }).toList();
    loadingState.value = LoadingState.success(dataList);
  }
}

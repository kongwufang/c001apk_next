import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';

import '../logic/model/oss_upload/datum.dart';
import '../logic/model/oss_upload/oss_upload_model.dart';
import '../logic/model/oss_upload/oss_upload_response.dart';
import '../logic/network/network_repo.dart';
import '../utils/extensions.dart';

class OssUtil {
  static Future<OssDatum?> onPostOSSUploadPrepare(
    String uploadBucket,
    String uploadDir,
    List<OssUploadModel> dataList, {
    String? uid,

    /// 是否为**图文发布**。
    ///
    /// Android `ArticleBlock.kt` 的注释：
    /// > `ossUploadPrepare` 的 `uploadFileList` 单项（**图文用，比普通动态多 hdr 字段**）。
    /// > `hdr=0` 表示普通图片，`hdr=1` 表示 Ultra HDR 图片。
    ///
    /// 图文发布还会多传一个 `feed_type=feed`
    /// （Android `ArticlePublishViewModel.onPostOSSUploadPrepare`）。
    bool forArticle = false,
  }) async {
    try {
      SmartDialog.showLoading(msg: '准备上传图片');
      final uploadFileList = dataList
          .map((data) {
            final json = data.toJson();
            if (forArticle) json['hdr'] = 0;
            return jsonEncode(json);
          })
          .toList()
          .toString();
      Response response = await NetworkRepo.postOSSUploadPrepare(
        data: FormData.fromMap(
          {
            'uploadBucket': uploadBucket,
            'uploadDir': uploadDir,
            'is_anonymous': '0',
            'uploadFileList': uploadFileList,
            'toUid': uid,
            if (forArticle) 'feed_type': 'feed',
          },
        ),
      );
      OssUploadResponse data = OssUploadResponse.fromJson(response.data);
      if (!data.message.isNullOrEmpty) {
        SmartDialog.dismiss();
        SmartDialog.showToast(data.message!);
        return null;
      } else if (data.data != null) {
        // SmartDialog.dismiss();
        return data.data!;
      }
      return null;
    } catch (e) {
      SmartDialog.dismiss();
      SmartDialog.showToast(e.toString());
      return null;
    }
  }
}

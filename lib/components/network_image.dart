import 'dart:io';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:gif_view/gif_view.dart';
import 'package:flutter/material.dart';

import '../constants/constants.dart';
import '../logic/repo/offline_cache_repo.dart';

/// 统一的取图入口。
///
/// ★ 支持**离线缓存**：地址以 `file://` 开头时直接读本地文件。
/// 离线存档里所有图片 URL 都被换成了 `file://` + 本地路径 + 原 URL 的尺寸后缀
/// （见 `OfflineCacheRepo`），所以详情页**不必为「离线」再写一套渲染** ——
/// 把存档解回 `Datum` 后走的就是这一条路径。
Widget networkImage(
  String imageUrl, {
  BoxFit? fit,
  double? width,
  double? height,
  bool isAvatar = false,
  ImageWidgetBuilder? imageBuilder,
  BorderRadiusGeometry? borderRadius =
      const BorderRadius.all(Radius.circular(12)),
}) {
  // 离线：本地文件。尺寸后缀（`@1000x1000.jpg`）要剥掉才能打开文件。
  if (OfflineCacheRepo.isLocal(imageUrl)) {
    return Image.file(
      File(OfflineCacheRepo.localFilePath(imageUrl)),
      fit: fit,
      width: width,
      height: height,
      errorBuilder: (context, _, __) => _placeHolder(
        context,
        isAvatar: isAvatar,
        width: width,
        borderRadius: borderRadius,
      ),
    );
  }

  return imageUrl.endsWith(Constants.SUFFIX_GIF)
      ? GifView.network(
          imageUrl,
          fit: fit,
          width: width,
          height: height,
          errorBuilder: (context, url, error) => _placeHolder(
            context,
            isAvatar: isAvatar,
            width: width,
            borderRadius: borderRadius,
          ),
          fadeDuration: const Duration(milliseconds: 200),
          loop: false,
        )
      : CachedNetworkImage(
          fit: fit,
          width: width,
          height: height,
          imageUrl: imageUrl,
          imageBuilder: imageBuilder,
          placeholder: (context, url) => _placeHolder(
            context,
            isAvatar: isAvatar,
            width: width,
            borderRadius: borderRadius,
          ),
          errorWidget: (context, url, error) => _placeHolder(
            context,
            isAvatar: isAvatar,
            width: width,
            borderRadius: borderRadius,
          ),
          fadeOutDuration: const Duration(milliseconds: 200),
          fadeInDuration: const Duration(milliseconds: 200),
        );
}

/// 占位图（离线 / 网络失败 / 加载中都用它）
Widget _placeHolder(
  BuildContext context, {
  required bool isAvatar,
  double? width,
  BorderRadiusGeometry? borderRadius,
}) {
  return Container(
    alignment: Alignment.center,
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.outline.withValues(alpha: 0.25),
      borderRadius: borderRadius,
    ),
    child: Icon(
      isAvatar ? Icons.person : Icons.all_inclusive,
      color: Theme.of(context).colorScheme.outline,
      size: width != null && width < 35
          ? width - 5
          : width != null && width < 70
              ? width - 10
              : 45,
    ),
  );
}

Widget clipNetworkImage(
  String imageUrl, {
  BoxFit? fit,
  double? radius,
  double? width,
  double? height,
  bool isAvatar = false,
  ImageWidgetBuilder? imageBuilder,
  BorderRadiusGeometry? clipBorderRadius,
}) {
  return ClipRRect(
    borderRadius:
        clipBorderRadius ?? BorderRadius.circular(isAvatar ? 50 : radius ?? 12),
    child: networkImage(
      imageUrl,
      fit: fit,
      width: width,
      height: height,
      isAvatar: isAvatar,
      imageBuilder: imageBuilder,
      borderRadius: null,
    ),
  );
}

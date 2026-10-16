import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart' show DioMediaType, FormData, MultipartFile;
import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
// ⚠️ get 经 get_connect 也导出了 `FormData` / `MultipartFile`（它自己那套），
// 与 dio 的同名类冲突 —— 这里两个都 hide，用 dio 的。
import 'package:get/get.dart' hide FormData, MultipartFile;
import 'package:image_picker/image_picker.dart';

import '../../components/network_image.dart';
import '../../logic/model/collection/collection.dart';
import '../../logic/network/network_repo.dart';
import '../../utils/extensions.dart';

/// 打开「收藏到」面板。
///
/// 对应 Android `ui/feed/CollectionPickBottomSheet.kt` +
/// `CollectionPickViewModel.kt`。
///
/// [onAction] 在每次收藏/取消成功后回调，带上**本次的净结果**；
/// 详情页拿它改底栏数字与星标（见 [CollectionAction] 的注释）。
///
/// ## ★ 为什么用回调而不是让 `Get.bottomSheet` 返回
/// 底部弹窗可以被「点外部 / 下滑」关掉，那种情况下 `Get.back` 不带 result，
/// 返回值就是 null —— 而收藏数更新不能丢。回调不依赖关闭路径，更可靠。
///
/// ## ★ 为什么不重拉列表/详情
/// 多收藏夹下**本地加减算不准** —— 同一个夹重复点、别人同时收藏都会偏；
/// 详情页那份 `favnum` 还有**服务端缓存滞后**，重拉反而更不准。
/// 所以数字只认 `addItem` 的响应。
Future<void> showCollectionPickSheet({
  required String feedId,
  ValueChanged<CollectionAction>? onAction,
}) async {
  await Get.bottomSheet(
    _CollectionPickSheet(feedId: feedId, onAction: onAction),
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
  );
}

class _CollectionPickSheet extends StatefulWidget {
  const _CollectionPickSheet({required this.feedId, this.onAction});

  final String feedId;
  final ValueChanged<CollectionAction>? onAction;

  @override
  State<_CollectionPickSheet> createState() => _CollectionPickSheetState();
}

class _CollectionPickSheetState extends State<_CollectionPickSheet> {
  List<CollectionData> _list = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  /// 拉列表。带 `id=<动态id>&type=feed&showDefault=1`，
  /// 服务端会给每个夹下发 `isBeCollected`（1=该动态已在此夹）。
  Future<void> _load() async {
    if (mounted) setState(() => _loading = true);
    var loaded = <CollectionData>[];
    try {
      final res = await NetworkRepo.getCollectionList(
        id: widget.feedId,
        type: 'feed',
        showDefault: 1,
        page: 1,
      );
      final body = res.data;
      if (body is Map) {
        loaded = CollectionListResponse.fromJson(
                Map<String, dynamic>.from(body))
            .data ??
            [];
      }
    } catch (_) {
      loaded = [];
    }
    if (!mounted) return;
    setState(() {
      _list = loaded;
      _loading = false;
    });
  }

  /// 点某个收藏夹：已在里面就取消，否则收藏进去。
  ///
  /// ★ 只发这一枪，**不再跟一次列表刷新** —— 响应里就把最新收藏数和本夹状态给全了，
  /// 面板按结果**就地改这一项的勾**。
  Future<void> _toggle(CollectionData item) async {
    // ⚠️ `id` 取成非空局部变量：`isNullOrEmpty` 是扩展 getter，
    // Dart **不会**据此把 `String?` 提升成 `String`，直接用会报
    // argument_type_not_assignable。
    final id = item.id ?? '';
    if (id.isEmpty) return;
    final collected = item.collected;

    setState(() => _loading = true);
    CollectionActionResponse? parsed;
    try {
      final res = await NetworkRepo.postCollectionAddItem(
        id: collected ? '' : id,
        cancelId: collected ? id : '',
        targetId: widget.feedId,
        type: 'feed',
      );
      final body = res.data;
      if (body is Map) {
        parsed =
            CollectionActionResponse.fromJson(Map<String, dynamic>.from(body));
      }
    } catch (_) {
      parsed = null;
    }
    if (!mounted) return;

    // ⚠️ addItem **成功体只有 `data`、没有 `status`**：
    // `{"data":{"favnum":95,"collect":1}}`；失败才有 `status`/`message`
    // （-5「该内容已存在」、-7「该内容不存在于该收藏单」）。
    // ⇒ 判成功要看 `data` 在不在，**不能看 status**。
    final action = parsed?.data;
    final msg = action == null
        ? (parsed?.message ?? '操作失败')
        : (collected ? '已取消收藏' : '已收藏到「${item.title.orEmpty}」');
    SmartDialog.showToast(msg);

    if (action != null) {
      // 就地改这一项的勾，不重拉列表
      item.isBeCollected = collected ? 0 : 1;
      widget.onAction?.call(CollectionAction(
        collectionId: id,
        favnum: action.favnum,
        // 服务端没回 collect 就按点击方向推
        collect: action.collect ?? (collected ? 0 : 1),
      ));
    }
    setState(() => _loading = false);
  }

  /// 新建收藏夹；建好后若当前有动态，就顺带收藏进去
  Future<void> _create() async {
    final result = await showDialog<_NewCollectionResult>(
      context: context,
      builder: (_) => const _CollectionEditDialog(),
    );
    if (result == null || !mounted) return;

    setState(() => _loading = true);
    var pic = '';
    var coverFailed = false;
    if (result.cover != null) {
      final uploaded = await uploadCollectionCover(result.cover!);
      if (uploaded == null) {
        coverFailed = true;
      } else {
        pic = uploaded;
      }
    }

    String? newId;
    var ok = false;
    try {
      final res = await NetworkRepo.postCollectionCreate(
        isOpen: result.isOpen.toString(),
        pic: pic,
        description: result.description,
        title: result.title,
      );
      final body = res.data;
      if (body is Map) {
        final parsed = CollectionDetailResponse.fromJson(
            Map<String, dynamic>.from(body));
        newId = parsed.data?.id;
        ok = parsed.data != null;
      }
    } catch (_) {
      ok = false;
    }

    // 新建后顺带收藏进去（Android 同此）
    if (ok && !newId.isNullOrEmpty) {
      try {
        final res = await NetworkRepo.postCollectionAddItem(
          id: newId!,
          cancelId: '',
          targetId: widget.feedId,
          type: 'feed',
        );
        final body = res.data;
        if (body is Map) {
          final added = CollectionActionResponse.fromJson(
                  Map<String, dynamic>.from(body))
              .data;
          if (added != null) {
            // 新夹还没进列表，面板只把结果转发给详情页
            widget.onAction?.call(CollectionAction(
              collectionId: newId!,
              favnum: added.favnum,
              collect: added.collect ?? 1,
            ));
          }
        }
      } catch (_) {
        // 收藏失败不影响「创建成功」的判定
      }
    }

    SmartDialog.showToast(
      !ok
          ? '创建失败'
          : coverFailed
              ? '创建成功，但封面上传失败'
              : '创建成功',
    );

    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final maxH = MediaQuery.of(context).size.height * 0.7;

    return Container(
      constraints: BoxConstraints(maxHeight: maxH),
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainerLow,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // ── 头部：收藏到 / 新建收藏夹 ──
            // 对应 Android `fragment_collection_pick.xml` 的头部行。
            // （那一行还有个「离线缓存」入口，本工程尚未实现离线缓存，先不放。）
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 6),
              child: Row(
                children: [
                  const Text(
                    '收藏到',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                  const Spacer(),
                  InkWell(
                    onTap: _loading ? null : _create,
                    borderRadius: BorderRadius.circular(8),
                    child: Padding(
                      padding:
                          const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      child: Text(
                        '新建收藏夹',
                        style:
                            TextStyle(fontSize: 14, color: colorScheme.primary),
                      ),
                    ),
                  ),
                ],
              ),
            ),

            if (_loading) const LinearProgressIndicator(minHeight: 2),

            // ── 列表 ──
            Flexible(
              child: _list.isEmpty
                  ? Padding(
                      // 文案取自 Android `fragment_collection_pick.xml` 的 emptyTip
                      padding: const EdgeInsets.symmetric(vertical: 28),
                      child: Text(
                        _loading ? '' : '还没有收藏夹，先新建一个吧',
                        style: TextStyle(
                            fontSize: 13, color: colorScheme.onSurfaceVariant),
                      ),
                    )
                  : ListView.builder(
                      shrinkWrap: true,
                      itemCount: _list.length,
                      itemBuilder: (_, i) => _item(_list[i]),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  /// 单个收藏夹项。布局对应 Android `item_collection_pick.xml`：
  /// 封面 56×56dp / 名称 15sp / 说明 12sp / 勾选 22×22dp
  Widget _item(CollectionData item) {
    final colorScheme = Theme.of(context).colorScheme;
    // desc = "N 条内容" + （有公开状态时）" · 公开/私密"
    final desc = StringBuffer('${item.itemNum ?? 0} 条内容');
    if (!item.isOpenTitle.isNullOrEmpty) {
      desc.write(' · ${item.isOpenTitle}');
    }

    return InkWell(
      // 点一下 = 收藏/取消
      onTap: _loading ? null : () => _toggle(item),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Row(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: SizedBox(
                width: 56,
                height: 56,
                child: item.coverPic.isNullOrEmpty
                    ? Container(
                        color: colorScheme.surfaceContainerHigh,
                        child: Icon(Icons.folder_outlined,
                            color: colorScheme.onSurfaceVariant),
                      )
                    : networkImage(item.coverPic!, fit: BoxFit.cover),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.title.orEmpty,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 15),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    desc.toString(),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                        fontSize: 12, color: colorScheme.onSurfaceVariant),
                  ),
                ],
              ),
            ),
            // 已在该夹就显示勾
            if (item.collected)
              Icon(Icons.check, size: 22, color: colorScheme.primary),
          ],
        ),
      ),
    );
  }
}

/// 新建收藏夹对话框的返回值
class _NewCollectionResult {
  const _NewCollectionResult({
    required this.title,
    required this.description,
    required this.isOpen,
    this.cover,
  });

  final String title;
  final String description;
  final int isOpen;
  final XFile? cover;
}

/// 新建收藏夹对话框。
///
/// 对应 Android `dialog_collection_edit.xml`：封面 + 名称 + 简介 + 「公开」开关。
class _CollectionEditDialog extends StatefulWidget {
  const _CollectionEditDialog();

  @override
  State<_CollectionEditDialog> createState() => _CollectionEditDialogState();
}

class _CollectionEditDialogState extends State<_CollectionEditDialog> {
  final _titleCtrl = TextEditingController();
  final _descCtrl = TextEditingController();
  bool _isOpen = true;
  XFile? _cover;

  @override
  void dispose() {
    _titleCtrl.dispose();
    _descCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickCover() async {
    final picked = await ImagePicker().pickImage(source: ImageSource.gallery);
    if (picked != null && mounted) setState(() => _cover = picked);
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return AlertDialog(
      title: const Text('新建收藏夹'), // collection_create
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            InkWell(
              onTap: _pickCover,
              borderRadius: BorderRadius.circular(8),
              child: Container(
                width: 88,
                height: 88,
                decoration: BoxDecoration(
                  color: colorScheme.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: _cover == null
                    ? Icon(Icons.add_photo_alternate_outlined,
                        color: colorScheme.onSurfaceVariant)
                    : ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Image.file(File(_cover!.path), fit: BoxFit.cover),
                      ),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _titleCtrl,
              decoration: const InputDecoration(
                labelText: '收藏夹名称', // collection_title_hint
                isDense: true,
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: _descCtrl,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: '简介',
                isDense: true,
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                const Expanded(child: Text('公开收藏夹')),
                Switch(
                  value: _isOpen,
                  onChanged: (v) => setState(() => _isOpen = v),
                ),
              ],
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Get.back(),
          child: const Text('取消'),
        ),
        FilledButton(
          onPressed: () {
            if (_titleCtrl.text.trim().isEmpty) {
              SmartDialog.showToast('请填写收藏夹名称');
              return;
            }
            Get.back(
              result: _NewCollectionResult(
                title: _titleCtrl.text.trim(),
                description: _descCtrl.text.trim(),
                isOpen: _isOpen ? 1 : 0,
                cover: _cover,
              ),
            );
          },
          child: const Text('确定'),
        ),
      ],
    );
  }
}

/// 上传封面图，成功返回图片 URL，失败返回 null。
///
/// 对应 Android `CollectionPickViewModel.upload`。
///
/// ## ★ 服务端的类型校验规则（Android 端 curl 实测，务必遵守）
/// 服务端按「文件名扩展名 / part 的 Content-Type」**两者之一**校验：
///
/// | Content-Type | 文件名 | 结果 |
/// |---|---|---|
/// | `image/jpeg` | 无扩展名 | 成功 |
/// | `image/*`（通配） | 无扩展名 | **103「请选择正确的文件类型」** |
/// | `image/*`（通配） | `xxx.jpg` | 成功 |
///
/// ⇒ **不能再用「通配类型 + md5 当文件名」那套**。
/// 这里两头都做对：文件名带扩展名，且 Content-Type 给具体的 `image/xxx`。
///
/// ⚠️ Android 还要把 HEIF/AVIF 解码重编码成 JPEG（服务端不认）；
/// Flutter 侧 `image_picker` 选出来的通常是 jpg/png，
/// 遇到不认识的扩展名就退回 jpg 并把类型报成 `image/jpeg`。
Future<String?> uploadCollectionCover(XFile file) async {
  try {
    final bytes = await file.readAsBytes();
    if (bytes.isEmpty) return null;

    final ext = _extensionOf(file.path);
    final md5hex = md5.convert(bytes).toString();
    final form = FormData.fromMap({
      'picFile': MultipartFile.fromBytes(
        Uint8List.fromList(bytes),
        // 文件名**必须带扩展名**
        filename: '$md5hex.$ext',
        // Content-Type **必须具体**，不能是 image/*
        contentType: DioMediaType('image', _mimeOf(ext)),
      ),
    });

    final res = await NetworkRepo.postCollectionUploadImage(
      fileMd5: md5hex,
      data: form,
    );
    final body = res.data;
    if (body is Map) {
      return CollectionUploadResponse.fromJson(
              Map<String, dynamic>.from(body))
          .data;
    }
    return null;
  } catch (_) {
    return null;
  }
}

String _extensionOf(String path) {
  final name = path.split(RegExp(r'[/\\]')).last;
  final dot = name.lastIndexOf('.');
  if (dot < 0 || dot == name.length - 1) return 'jpg';
  final ext = name.substring(dot + 1).toLowerCase();
  return const {'jpg', 'jpeg', 'png', 'webp', 'gif'}.contains(ext) ? ext : 'jpg';
}

String _mimeOf(String ext) => switch (ext) {
      'png' => 'png',
      'webp' => 'webp',
      'gif' => 'gif',
      _ => 'jpeg',
    };

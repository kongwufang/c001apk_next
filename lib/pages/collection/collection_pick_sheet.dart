import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart' show DioMediaType, FormData, MultipartFile;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
// ⚠️ get 经 get_connect 也导出了 `FormData` / `MultipartFile`（它自己那套），
// 与 dio 的同名类冲突 —— 这里两个都 hide，用 dio 的。
import 'package:get/get.dart' hide FormData, MultipartFile;
import 'package:image_picker/image_picker.dart';

import '../../components/network_image.dart';
import '../../logic/model/collection/collection.dart';
import '../../logic/network/network_repo.dart';
import '../../logic/repo/offline_cache_repo.dart';
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
    // 「缓存中…」跟着 repo 的 Rx 走
    ever(OfflineCacheRepo.savingIds, (_) {
      if (mounted) setState(() {});
    });
  }

  bool get _savingOffline => OfflineCacheRepo.isSaving(widget.feedId);

  /// 把当前这条动态存成离线存档。
  ///
  /// **不 await** —— 缓存要下几十张图，用户点完就把面板划走了，
  /// 下载不能跟面板的生命周期陪葬（`saveAsync` 里有同样的说明）。
  void _saveOffline() {
    SmartDialog.showToast('缓存中…'); // offline_cache_saving
    OfflineCacheRepo.saveAsync(
      widget.feedId,
      onDone: (error) => SmartDialog.showToast(
        error ?? '已离线缓存', // offline_cache_saved
      ),
    );
    setState(() {});
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

  /// 打开编辑对话框。[item] 为 null → 新建；否则 → 编辑。
  ///
  /// 对应 Android `CollectionPickBottomSheet.showEditDialog(item)`：
  /// **同一个对话框两用**，只有保存时走的分支不同。
  Future<void> _openEdit([CollectionData? item]) async {
    final result = await showDialog<CollectionEditResult>(
      context: context,
      builder: (_) => CollectionEditDialog(item: item),
    );
    if (result == null || !mounted) return;

    setState(() => _loading = true);

    // 上传新封面（选了才传）
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

    if (item == null) {
      await _doCreate(result, pic, coverFailed);
    } else {
      await _doUpdate(item, result, pic, coverFailed);
    }

    await _load();
  }

  /// 新建：建好后若当前有动态，就顺带收藏进去
  Future<void> _doCreate(
    CollectionEditResult result,
    String pic,
    bool coverFailed,
  ) async {
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
  }

  /// 修改：标题 / 简介 / 公开私密 / 封面
  Future<void> _doUpdate(
    CollectionData item,
    CollectionEditResult result,
    String uploadedPic,
    bool coverFailed,
  ) async {
    SmartDialog.showToast(
      await applyCollectionEdit(item, result, uploadedPic, coverFailed),
    );
  }

  @override

  /// 面板内的键盘处理（只在这个面板里生效）。
  ///
  /// - 数字 `1`–`9`：直选第 N 个收藏夹（等价于点它）
  /// - `l`：本地（离线）保存
  ///
  /// ★ 处理了就返回 `handled`，**不冒泡**给全局 `ShortcutScope` ——
  /// 否则按 `l` 会连带触发全局「点赞」。
  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent) return KeyEventResult.ignored;
    final k = event.logicalKey;

    // l = 本地保存
    if (k == LogicalKeyboardKey.keyL) {
      if (!_savingOffline) _saveOffline();
      return KeyEventResult.handled;
    }

    // 数字 1–9 = 直选第 N 个收藏夹
    const digits = [
      LogicalKeyboardKey.digit1, LogicalKeyboardKey.digit2,
      LogicalKeyboardKey.digit3, LogicalKeyboardKey.digit4,
      LogicalKeyboardKey.digit5, LogicalKeyboardKey.digit6,
      LogicalKeyboardKey.digit7, LogicalKeyboardKey.digit8,
      LogicalKeyboardKey.digit9,
    ];
    // 小键盘也要
    const pads = [
      LogicalKeyboardKey.numpad1, LogicalKeyboardKey.numpad2,
      LogicalKeyboardKey.numpad3, LogicalKeyboardKey.numpad4,
      LogicalKeyboardKey.numpad5, LogicalKeyboardKey.numpad6,
      LogicalKeyboardKey.numpad7, LogicalKeyboardKey.numpad8,
      LogicalKeyboardKey.numpad9,
    ];
    var idx = digits.indexOf(k);
    if (idx < 0) idx = pads.indexOf(k);
    if (idx < 0) return KeyEventResult.ignored;

    // `_list` 是异步加载的，可能还是空的
    if (_loading || idx >= _list.length) return KeyEventResult.handled;
    _toggle(_list[idx]);
    return KeyEventResult.handled;
  }


  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final maxH = MediaQuery.of(context).size.height * 0.7;

    // ★ 键盘：数字 1–9 直选收藏夹，`l` 本地保存（见 `_onKey`）
    return Focus(
      autofocus: true,
      onKeyEvent: _onKey,
      child: Container(
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
            // ── 头部：收藏到 / 离线缓存 / 新建收藏夹 ──
            // 对应 Android `fragment_collection_pick.xml` 的头部行（三项齐全）。
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 6),
              child: Row(
                children: [
                  const Text(
                    '收藏到',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                  const Spacer(),
                  // ★ 离线缓存：这个面板里**唯一不依赖联网**的一步 ——
                  // 收藏夹列表拉不到它也能用（Android 原注释）。
                  // 保存中时禁用并把文案换成「缓存中…」。
                  InkWell(
                    onTap: _savingOffline ? null : _saveOffline,
                    borderRadius: BorderRadius.circular(8),
                    child: Padding(
                      padding:
                          const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      child: Text(
                        _savingOffline ? '缓存中…' : '离线缓存',
                        style: TextStyle(
                          fontSize: 14,
                          color: _savingOffline
                              ? colorScheme.onSurfaceVariant
                              : colorScheme.primary,
                        ),
                      ),
                    ),
                  ),
                  InkWell(
                    onTap: _loading ? null : () => _openEdit(),
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
            // ★ 桌面端：提示可以按数字直选 / L 本地保存
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 6),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  '按数字直接选择收藏夹 · L 本地保存',
                  style: TextStyle(
                    fontSize: 12,
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
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
      // **长按 = 编辑**（与 Android `root.setOnLongClickListener { showEditDialog(item) }` 一致）
      onLongPress: _loading ? null : () => _openEdit(item),
      // 桌面端：右键 = 长按（= 编辑收藏夹）
      onSecondaryTap: _loading ? null : () => _openEdit(item),
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

/// 新建 / 编辑收藏夹对话框的返回值
class CollectionEditResult {
  const CollectionEditResult({
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

/// 新建 / 编辑收藏夹对话框。
///
/// 对应 Android `dialog_collection_edit.xml` + `CollectionPickBottomSheet.showEditDialog`：
/// **同一个对话框两用** —— [item] 为 null 是「新建收藏夹」，否则是「编辑收藏夹」。
///
/// 字段顺序与 hint 都取自 Android：
/// 封面（`collection_cover`=封面（可选），120dp）→ 名称（`collection_title_hint`）→
/// 简介（`collection_desc_hint`=简介（可选））→ 公开开关（`collection_open`=公开）。
class CollectionEditDialog extends StatefulWidget {
  const CollectionEditDialog({this.item});

  final CollectionData? item;

  @override
  State<CollectionEditDialog> createState() => CollectionEditDialogState();
}

class CollectionEditDialogState extends State<CollectionEditDialog> {
  late final _titleCtrl =
      TextEditingController(text: widget.item?.title ?? '');
  late final _descCtrl =
      TextEditingController(text: widget.item?.description ?? '');
  late bool _isOpen = (widget.item?.isOpen ?? 1) == 1;
  XFile? _cover;

  bool get _isEdit => widget.item != null;

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
    final oldCover = widget.item?.coverPic ?? '';
    return AlertDialog(
      // collection_create / collection_edit
      title: Text(_isEdit ? '编辑收藏夹' : '新建收藏夹'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 封面：Android 高 120dp
            InkWell(
              onTap: _pickCover,
              borderRadius: BorderRadius.circular(8),
              child: Container(
                width: double.infinity,
                height: 120,
                decoration: BoxDecoration(
                  color: colorScheme.surfaceContainerHigh,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: _cover != null
                    ? ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Image.file(File(_cover!.path), fit: BoxFit.cover),
                      )
                    : !oldCover.isNullOrEmpty
                        ? ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: networkImage(oldCover, fit: BoxFit.cover),
                          )
                        : Icon(Icons.add_photo_alternate_outlined,
                            color: colorScheme.onSurfaceVariant),
              ),
            ),
            const SizedBox(height: 6),
            Text(
              '封面（可选）', // collection_cover
              style: TextStyle(
                  fontSize: 12, color: colorScheme.onSurfaceVariant),
            ),
            const SizedBox(height: 10),
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
                labelText: '简介（可选）', // collection_desc_hint
                isDense: true,
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                const Expanded(child: Text('公开')), // collection_open
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
            final title = _titleCtrl.text.trim();
            if (title.isEmpty) {
              SmartDialog.showToast('请填写收藏夹名称');
              return;
            }
            Get.back(
              result: CollectionEditResult(
                title: title,
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

/// 保存收藏夹修改（标题 / 简介 / 公开私密 / 封面）。返回**给用户看的提示**。
///
/// 抽成公共函数是为了让「收藏到」面板与**收藏夹详情页**共用同一套逻辑
/// （Android 那边也是共用 `CollectionPickViewModel.update`）。
///
/// ★ **没换封面就沿用旧封面** —— Android 是 `pic = uploaded ?: oldCover.orEmpty()`。
/// 不这么做的话，「只改标题」会把封面清掉。
Future<String> applyCollectionEdit(
  CollectionData item,
  CollectionEditResult result,
  String uploadedPic,
  bool coverFailed,
) async {
  final pic = uploadedPic.isNotEmpty ? uploadedPic : (item.coverPic ?? '');

  var ok = false;
  try {
    final res = await NetworkRepo.postCollectionUpdate(
      id: item.id ?? '',
      title: result.title,
      description: result.description,
      pic: pic,
      isOpen: result.isOpen,
    );
    final body = res.data;
    ok = body is Map &&
        CollectionDetailResponse.fromJson(Map<String, dynamic>.from(body))
                .data !=
            null;
  } catch (_) {
    ok = false;
  }

  if (!ok) return '保存失败';
  return coverFailed ? '已保存，但封面上传失败' : '已保存';
}

/// 上传封面图，成功返回图片 URL，失败返回 null。///
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

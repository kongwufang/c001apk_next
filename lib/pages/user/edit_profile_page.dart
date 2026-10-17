import 'package:flutter/material.dart';
import 'package:get/get.dart' hide FormData, MultipartFile;
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';

import '../../logic/model/feed/data_model.dart';
import '../../logic/model/feed/datum.dart';
import '../../constants/constants.dart';
import '../../logic/network/network_repo.dart';
import '../../utils/global_data.dart';
import '../../utils/oss_upload.dart';
import '../../utils/storage_util.dart';

/// 编辑资料。
///
/// 移植自 Android `ui/user/EditProfileActivity.kt`。
/// 官方字段顺序（见那边的注释）：
/// **头像 → 个人主页背景图 → 用户名 → 性别 → 生日 → 城市 → 个人签名**。
///
/// ## 本页已实现
/// 性别 / 生日 / 地区 / 个人签名 —— 四项都走
/// `NetworkRepo.postChangeProfile`（逐项提交，只发改动过的那一项）。
///
/// ## ⚠️ 本页尚未实现（需要图片上传，见台账）
/// - **头像**：`postChangeAvatar`（multipart，part 名 `imgFile`，filename 用 md5）
/// - **背景图**：`postOSSUploadPrepare` → `postChangeAvatarCover`
/// - **用户名**：接口文档里**没有** `username` 这个 key，必须先实测确认
///
/// ## 接口要点（`ApiService.kt:554`，抓包核对过）
/// - 性别：`key=gender`，`value=1|0|-1`（男/女/保密）
/// - 生日：`key=""`（**空字符串**），`value={"birthyear":y,"birthmonth":m,"birthday":d}`
/// - 地区：`key=""`，`value={"province":"..","city":".."}`
/// - 签名：`key=bio`
/// - 恢复保密：`key=birth` / `key=location` 走 `postResetProfile`
class EditProfilePage extends StatefulWidget {
  const EditProfilePage({super.key});

  @override
  State<EditProfilePage> createState() => _EditProfilePageState();
}

class _EditProfilePageState extends State<EditProfilePage> {
  final _bioController = TextEditingController();
  final _provinceController = TextEditingController();
  final _cityController = TextEditingController();

  bool _loading = true;
  bool _saving = false;

  /// 服务端当前值（用来判断「哪一项改动过」—— 接口是逐项提交的）
  int _gender = -1;
  int? _birthYear;
  int? _birthMonth;
  int? _birthDay;
  String _province = '';
  String _city = '';
  String _bio = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _bioController.dispose();
    _provinceController.dispose();
    _cityController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final res = await NetworkRepo.getProfile(GlobalData().uid);
      final Datum? data = DataModel.fromJson(res.data).data;
      // ⚠️ `UserInfo` 里**没有** gender / birth* / province / city / bio 字段
      //   （生成代码里全被注释掉了）⇒ 从 `raw` 读。
      //   JSON key 取自 Android `UserProfileResponse.kt:17-44`：
      //   gender / bio / province / city / birthyear / birthmonth / birthday
      final raw = data?.raw ?? const <String, dynamic>{};
      int? asInt(Object? v) {
        if (v is int) return v;
        if (v is num) return v.toInt();
        if (v is String) return int.tryParse(v);
        return null;
      }

      setState(() {
        _gender = asInt(raw['gender']) ?? -1;
        _birthYear = asInt(raw['birthyear']);
        _birthMonth = asInt(raw['birthmonth']);
        _birthDay = asInt(raw['birthday']);
        _province = (raw['province'] ?? '').toString();
        _city = (raw['city'] ?? '').toString();
        _bio = (raw['bio'] ?? '').toString();
        _bioController.text = _bio;
        _provinceController.text = _province;
        _cityController.text = _city;
        _loading = false;
      });
    } catch (e) {
      debugPrint('EditProfile.load: $e');
      if (mounted) setState(() => _loading = false);
    }
  }

  /// 逐项提交，只发改动过的那一项（对齐 Android）
  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      // 性别
      final profile = await _fetchGender();
      if (profile != null && profile != _gender) {
        await NetworkRepo.postChangeProfile(
            key: 'gender', value: '${profile}');
      }

      // 生日
      if (_birthYear != null &&
          _birthMonth != null &&
          _birthDay != null) {
        await NetworkRepo.postChangeProfile(
          key: '',
          value:
              '{"birthyear":$_birthYear,"birthmonth":$_birthMonth,"birthday":$_birthDay}',
        );
      }

      // 地区
      final p = _provinceController.text.trim();
      final c = _cityController.text.trim();
      if (p.isNotEmpty && c.isNotEmpty) {
        await NetworkRepo.postChangeProfile(
          key: '',
          value: '{"province":"$p","city":"$c"}',
        );
      }

      // 签名
      final bio = _bioController.text.trim();
      if (bio != _bio) {
        await NetworkRepo.postChangeProfile(key: 'bio', value: bio);
      }

      SmartDialog.showToast('已保存');
      if (mounted) Get.back(result: true);
    } catch (e) {
      debugPrint('EditProfile.save: $e');
      SmartDialog.showToast('保存失败：$e');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  /// 性别是本地单选，这里直接返回当前选中值
  Future<int?> _fetchGender() async => _gender;

  /// 换头像。
  ///
  /// 对齐 Android `EditProfileActivity`：
  /// > 头像：相册选图 → 方形裁剪 600x600 → multipart 上传（imgFile）→ 返回新头像地址
  ///
  /// ⚠️ **没做裁剪**：Android 会先弹 600×600 的方形裁剪页，
  /// Flutter 侧还没有裁剪页，这里直接传原图（服务端一般会自己缩）。
  ///
  /// multipart 规则（`ApiService.kt` 抓包核对）：
  /// part 名 **`imgFile`**、filename 用**图片 md5（无扩展名）**、Content-Type `image/jpeg`
  Future<void> _pickAvatar() async {
    final picked = await FilePicker.platform.pickFiles(type: FileType.image);
    final path = picked?.files.firstOrNull?.path;
    if (path == null) return;

    // ★ 先进裁剪页（600×600 方形，对齐 Android EditProfileActivity 的注释）
    final cropped = await Get.toNamed<Uint8List>('/cropImage', parameters: {
      'path': path,
      'outW': '600',
      'outH': '600',
    });
    final bytes = cropped;
    if (bytes == null || bytes.isEmpty) return;

    final md5Name = md5.convert(bytes).toString();
    setState(() => _saving = true);
    try {
      final form = FormData.fromMap({
        'imgFile': MultipartFile.fromBytes(
          bytes,
          filename: md5Name, // 无扩展名
          contentType: DioMediaType('image', 'jpeg'),
        ),
      });
      final res = await NetworkRepo.postChangeAvatar(data: form);
      final url = (res.data is Map) ? res.data['data']?.toString() : null;
      if (url != null && url.isNotEmpty) {
        GStorage.setUserAvatar(url);
        SmartDialog.showToast('头像已更新');
        if (mounted) setState(() {});
      } else {
        SmartDialog.showToast('头像上传失败');
      }
    } catch (e) {
      debugPrint('EditProfile.avatar: $e');
      SmartDialog.showToast('头像上传失败：$e');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  /// 换主页背景图。
  ///
  /// 对齐 Android `EditProfileActivity`：
  /// > 背景图：相册选图 → 方形裁剪 1080x1080 → OSS 上传 → changeAvatarCover
  ///
  /// ⚠️ 同样**没做裁剪**（1080×1080）；上传流程见 `OssUpload` 的类注释。
  Future<void> _pickCover() async {
    final picked = await FilePicker.platform.pickFiles(type: FileType.image);
    final path = picked?.files.firstOrNull?.path;
    if (path == null) return;

    // ★ 先进裁剪页（1080×1080 方形）
    final bytes = await Get.toNamed<Uint8List>('/cropImage', parameters: {
      'path': path,
      'outW': '1080',
      'outH': '1080',
    });
    if (bytes == null || bytes.isEmpty) return;

    setState(() => _saving = true);
    try {
      // ① prepare：uploadBucket=avatar / uploadDir=cover（Android EditProfileViewModel:176）
      final md5Hex = md5.convert(bytes).toString();
      final prepareForm = FormData.fromMap({
        'uploadBucket': 'avatar',
        'uploadDir': 'cover',
        'is_anonymous': '0',
        'uploadFileList': '[{"name":"cover.jpg","size":${bytes.length},'
            '"type":"image/jpeg","md5":"$md5Hex"}]',
        'toUid': '',
      });
      final prep = await NetworkRepo.postOSSUploadPrepare(data: prepareForm);
      final prepData = (prep.data is Map) ? prep.data['data'] : null;
      final info = prepData?['uploadPrepareInfo'];
      final fileInfo = (prepData?['fileInfo'] is List)
          ? (prepData['fileInfo'] as List).first
          : null;
      if (info == null || fileInfo == null) {
        SmartDialog.showToast('背景图上传失败：prepare 无数据');
        return;
      }

      // ② 直传 OSS
      final objectName = await OssUpload.put(
        endPoint: info['endPoint']?.toString() ?? '',
        bucket: info['bucket']?.toString() ?? '',
        objectName: fileInfo['uploadFileName']?.toString() ?? '',
        accessKeyId: info['accessKeyId']?.toString() ?? '',
        accessKeySecret: info['accessKeySecret']?.toString() ?? '',
        securityToken: info['securityToken']?.toString() ?? '',
        bytes: bytes,
        mime: 'image/jpeg',
        versionCode: Constants.VERSION_CODE,
      );

      // ③ 回传完整 CDN 地址
      final url = 'http://avatar.coolapk.com/$objectName';
      await NetworkRepo.postChangeAvatarCover(url: url);
      SmartDialog.showToast('背景图已更新');
      if (mounted) setState(() {});
    } catch (e) {
      debugPrint('EditProfile.cover: $e');
      SmartDialog.showToast('背景图上传失败：$e');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _pickBirthday() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: (_birthYear != null && _birthMonth != null && _birthDay != null)
          ? DateTime(_birthYear!, _birthMonth!, _birthDay!)
          : DateTime(now.year - 20, 1, 1),
      firstDate: DateTime(1900),
      lastDate: now,
    );
    if (picked != null) {
      setState(() {
        _birthYear = picked.year;
        _birthMonth = picked.month;
        _birthDay = picked.day;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('编辑资料'),
        actions: [
          TextButton(
            onPressed: _saving ? null : _save,
            child: Text(_saving ? '保存中…' : '保存'),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              children: [
                // ── 头像 / 背景图：尚未实现，明确写出来而不是假装能用 ──
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('头像'),
                  subtitle: Text(
                    _saving ? '上传中…' : '点击更换（先裁剪为 600×600）',
                  ),
                  onTap: _saving ? null : _pickAvatar,
                ),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('个人主页背景图'),
                  subtitle: Text(
                    _saving ? '上传中…' : '点击更换（先裁剪为 1080×1080）',
                  ),
                  onTap: _saving ? null : _pickCover,
                ),
                const Divider(),

                // ── 性别 ──
                const Padding(
                  padding: EdgeInsets.only(top: 8, bottom: 4),
                  child: Text('性别', style: TextStyle(fontSize: 14)),
                ),
                SegmentedButton<int>(
                  segments: const [
                    ButtonSegment(value: 1, label: Text('男')),
                    ButtonSegment(value: 0, label: Text('女')),
                    ButtonSegment(value: -1, label: Text('保密')),
                  ],
                  selected: {_gender},
                  onSelectionChanged: (v) => setState(() => _gender = v.first),
                ),

                // ── 生日 ──
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('生日'),
                  subtitle: Text(_birthYear == null
                      ? '保密'
                      : '$_birthYear-$_birthMonth-$_birthDay'),
                  onTap: _pickBirthday,
                  trailing: TextButton(
                    onPressed: () async {
                      // 恢复「保密」：走 resetProfile
                      await NetworkRepo.postResetProfile(key: 'birth');
                      setState(() {
                        _birthYear = null;
                        _birthMonth = null;
                        _birthDay = null;
                      });
                      SmartDialog.showToast('已设为保密');
                    },
                    child: const Text('保密'),
                  ),
                ),

                // ── 地区 ──
                TextField(
                  controller: _provinceController,
                  decoration: const InputDecoration(labelText: '省份'),
                ),
                TextField(
                  controller: _cityController,
                  decoration: const InputDecoration(labelText: '城市'),
                ),
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton(
                    onPressed: () async {
                      await NetworkRepo.postResetProfile(key: 'location');
                      _provinceController.clear();
                      _cityController.clear();
                      setState(() {
                        _province = '';
                        _city = '';
                      });
                      SmartDialog.showToast('已设为保密');
                    },
                    child: const Text('地区保密'),
                  ),
                ),

                // ── 个人签名 ──
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: TextField(
                    controller: _bioController,
                    maxLines: 4,
                    maxLength: 200,
                    decoration: const InputDecoration(
                      labelText: '个人签名',
                      border: OutlineInputBorder(),
                      alignLabelWithHint: true,
                    ),
                  ),
                ),
              ],
            ),
    );
  }
}

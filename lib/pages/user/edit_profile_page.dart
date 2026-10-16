import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart' show DioMediaType, FormData, MultipartFile;
import 'package:flutter/material.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart';
import 'package:image_picker/image_picker.dart';

import '../../components/m3e_progress.dart';
import '../../components/network_image.dart';
import '../../logic/model/oss_upload/oss_upload_model.dart';
import '../../logic/model/user_profile/user_profile.dart';
import '../../logic/network/network_repo.dart';
import '../../utils/global_data.dart';
import '../../utils/oss/aliyunoss_client.dart';
import '../../utils/oss/aliyunoss_config.dart';
import '../../utils/oss_util.dart';
import '../../utils/region_data.dart';

/// 编辑资料（个人主页）。
///
/// 对应 Android `ui/user/EditProfileActivity.kt` + `EditProfileViewModel.kt` +
/// `activity_edit_profile.xml`。
///
/// ## ★ 所有改动都是「点一下存一下」
/// 和官方一样，**每项单独走一次接口**（Android 原注释）：
/// | 项 | 接口 |
/// |---|---|
/// | 头像 | `POST /v6/account/changeAvatar`（multipart，part 名 **`imgFile`**） |
/// | 背景图 | 先 OSS 上传（`uploadBucket=avatar` & `uploadDir=cover`）再 `POST /v6/account/changeAvatarCover` |
/// | 性别 / 签名 | `POST /v6/account/changeProfile`（`key` + `value`） |
/// | 生日 / 地区 | 同上，但 **`key` 是空字符串**，`value` 才是 JSON |
/// | 生日 / 地区设保密 | `POST /v6/account/resetProfile`（`key=birth` / `location`） |
///
/// ## ★ 成功判据
/// `message` 为空就是成功（服务端失败时一定带 `message`）。
class EditProfilePage extends StatefulWidget {
  const EditProfilePage({super.key});

  @override
  State<EditProfilePage> createState() => _EditProfilePageState();
}

class _EditProfilePageState extends State<EditProfilePage> {
  UserProfileData? _profile;
  bool _loading = true;

  /// 这次进来有没有改过东西（决定返回时刷不刷主页）
  bool _changed = false;

  @override
  void initState() {
    super.initState();
    _fetchProfile();
  }

  Future<void> _fetchProfile() async {
    if (mounted) setState(() => _loading = true);
    UserProfileData? data;
    try {
      final res = await NetworkRepo.getProfile(GlobalData().uid.toString());
      final body = res.data;
      if (body is Map) {
        data = UserProfile.fromJson(Map<String, dynamic>.from(body)).data;
      }
    } catch (_) {
      data = null;
    }
    if (!mounted) return;
    setState(() {
      _profile = data;
      _loading = false;
    });
  }

  /// 统一的「改一项」：走 `changeProfile(key, value)`，成功后重新拉资料。
  ///
  /// ★ 成功判据是 **`message` 为空**（服务端失败时一定带 message）。
  Future<void> _changeProfile(String key, String value, {String? okToast}) async {
    SmartDialog.showLoading();
    String? fail;
    var ok = false;
    try {
      final res = await NetworkRepo.postChangeProfile(key: key, value: value);
      final body = res.data;
      if (body is Map) {
        fail = body['message']?.toString();
        ok = (fail == null || fail.isEmpty);
      }
    } catch (_) {
      ok = false;
    }
    SmartDialog.dismiss();
    if (!ok) {
      SmartDialog.showToast(fail?.isNotEmpty == true ? fail! : '修改失败');
      return;
    }
    _changed = true;
    if (okToast != null) SmartDialog.showToast(okToast);
    await _fetchProfile();
  }

  /// 生日 / 地区设成「保密」（`key=birth` / `location`）
  Future<void> _resetProfile(String key) async {
    SmartDialog.showLoading();
    String? fail;
    var ok = false;
    try {
      final res = await NetworkRepo.postResetProfile(key: key);
      final body = res.data;
      if (body is Map) {
        fail = body['message']?.toString();
        ok = (fail == null || fail.isEmpty);
      }
    } catch (_) {
      ok = false;
    }
    SmartDialog.dismiss();
    if (!ok) {
      SmartDialog.showToast(fail?.isNotEmpty == true ? fail! : '修改失败');
      return;
    }
    _changed = true;
    await _fetchProfile();
  }

  // ────────────────── 头像 ──────────────────

  /// 换头像。
  ///
  /// ★ 与收藏夹封面上传的规则**不一样**（两边都是抓包实测的）：
  /// part 名固定 **`imgFile`**、filename 用 **md5（无扩展名）**、Content-Type `image/jpeg`。
  Future<void> _pickAvatar() async {
    final picked = await ImagePicker().pickImage(source: ImageSource.gallery);
    if (picked == null) return;

    SmartDialog.showLoading();
    try {
      final bytes = await picked.readAsBytes();
      if (bytes.isEmpty) {
        SmartDialog.dismiss();
        SmartDialog.showToast('读取图片失败');
        return;
      }
      final md5hex = md5.convert(bytes).toString();
      final form = FormData.fromMap({
        'imgFile': MultipartFile.fromBytes(
          Uint8List.fromList(bytes),
          // 文件名 = md5，**不带扩展名**
          filename: md5hex,
          contentType: DioMediaType('image', 'jpeg'),
        ),
      });
      final res = await NetworkRepo.postChangeAvatar(data: form);
      final body = res.data;
      String? newAvatar;
      String? fail;
      if (body is Map) {
        newAvatar = body['data']?.toString();
        fail = body['message']?.toString();
      }
      SmartDialog.dismiss();
      if (newAvatar == null || newAvatar.isEmpty) {
        SmartDialog.showToast(
            (fail != null && fail.isNotEmpty) ? fail : '头像修改失败');
        return;
      }
      _changed = true;
      await _fetchProfile();
    } catch (_) {
      SmartDialog.dismiss();
      SmartDialog.showToast('头像修改失败');
    }
  }

  // ────────────────── 背景图 ──────────────────

  /// 换主页背景图。
  ///
  /// 两步（Android 抓包核对 2026-09-24）：
  /// 1. `ossUploadPrepare`（`uploadBucket=avatar` & `uploadDir=cover`）拿凭证
  /// 2. PUT 到 OSS，再把 `$prefix/$fileName`（**完整 CDN 地址**）回传
  ///    `POST /v6/account/changeAvatarCover`
  Future<void> _pickCover() async {
    final picked = await ImagePicker().pickImage(source: ImageSource.gallery);
    if (picked == null) return;

    SmartDialog.showLoading(msg: '正在上传背景图');
    try {
      final bytes = await picked.readAsBytes();
      final md5hex = md5.convert(bytes).toString();
      final name = picked.name.isNotEmpty ? picked.name : '$md5hex.jpg';

      final prepare = await OssUtil.onPostOSSUploadPrepare(
        'avatar',
        'cover',
        [OssUploadModel(name: name, resolution: '', md5: md5hex)],
      );
      if (prepare == null) {
        SmartDialog.dismiss();
        SmartDialog.showToast('背景图上传失败');
        return;
      }
      final info = prepare.uploadPrepareInfo!;
      final fileInfo = prepare.fileInfo!.first;
      final client = AliyunOssClient(
        accessKeyId: info.accessKeyId!,
        accessKeySecret: info.accessKeySecret!,
        securityToken: info.securityToken!,
      );
      final config = AliyunOssConfig(
        endpoint: info.endPoint!,
        bucket: info.bucket!,
        directory: 'cover',
      );
      final result = await client.upload(
        id: '0',
        config: config,
        // 与 feed 上传同一套做法：OSS 对象名要去掉目录前缀
        ossFileName: fileInfo.uploadFileName!.replaceFirst('cover', ''),
        filePath: picked.path,
      );
      if (result.state != AliyunOssResultState.success) {
        SmartDialog.dismiss();
        SmartDialog.showToast('背景图上传失败');
        return;
      }

      final url = '${info.uploadImagePrefix}/${fileInfo.uploadFileName}';
      final res = await NetworkRepo.postChangeAvatarCover(url: url);
      final body = res.data;
      final ok = body is Map && body['data'] != null;
      SmartDialog.dismiss();
      if (!ok) {
        SmartDialog.showToast('背景图修改失败');
        return;
      }
      _changed = true;
      await _fetchProfile();
    } catch (_) {
      SmartDialog.dismiss();
      SmartDialog.showToast('背景图修改失败');
    }
  }

  // ────────────────── 其余字段 ──────────────────

  /// 性别：1 男 / 0 女 / -1 保密
  ///
  /// 用 `SimpleDialogOption` 而不是 `RadioListTile` —— 后者在新版 Flutter 里
  /// `groupValue` / `onChanged` 正在被废弃，跨版本不稳。
  Future<void> _pickGender() async {
    final current = switch (_profile?.gender) {
      1 => 1,
      0 => 0,
      _ => -1,
    };
    final picked = await showDialog<int>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('性别'),
        children: [
          for (final (label, value) in const [('男', 1), ('女', 0), ('保密', -1)])
            SimpleDialogOption(
              onPressed: () => Get.back(result: value),
              child: Row(
                children: [
                  Expanded(child: Text(label)),
                  if (value == current)
                    Icon(Icons.check,
                        size: 20, color: Theme.of(ctx).colorScheme.primary),
                ],
              ),
            ),
        ],
      ),
    );
    if (picked == null) return;
    await _changeProfile('gender', picked.toString());
  }

  Future<void> _pickBirthday() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: DateTime(now.year - 20, 1, 1),
      firstDate: DateTime(1900),
      lastDate: now,
    );
    if (picked == null) return;
    // ★ key 是空字符串，值才是 JSON
    await _changeProfile(
      '',
      jsonEncode({
        'birthyear': picked.year,
        'birthmonth': picked.month,
        'birthday': picked.day,
      }),
    );
  }

  /// 城市：先「选择城市 / 保密」，再选省份，再选市/区
  Future<void> _pickCity() async {
    final mode = await showDialog<int>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('城市'),
        children: [
          SimpleDialogOption(
            onPressed: () => Get.back(result: 0),
            child: const Text('选择城市'),
          ),
          SimpleDialogOption(
            onPressed: () => Get.back(result: 1),
            child: const Text('保密'),
          ),
        ],
      ),
    );
    if (mode == null) return;
    if (mode == 1) {
      await _resetProfile('location');
      return;
    }

    final province = await showDialog<String>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('省份'),
        children: [
          for (final p in RegionData.provinceList)
            SimpleDialogOption(
              onPressed: () => Get.back(result: p),
              child: Text(p),
            ),
        ],
      ),
    );
    if (province == null) return;

    final cities = RegionData.citiesOf(province);
    if (cities.isEmpty) {
      // 没有下级：省市同名提交
      await _changeProfile('', jsonEncode({'province': province, 'city': province}));
      return;
    }
    final city = await showDialog<String>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Text(province),
        children: [
          for (final c in cities)
            SimpleDialogOption(
              onPressed: () => Get.back(result: c),
              child: Text(c),
            ),
        ],
      ),
    );
    if (city == null) return;
    await _changeProfile('', jsonEncode({'province': province, 'city': city}));
  }

  Future<void> _editBio() async {
    final ctrl = TextEditingController(text: _profile?.bio ?? '');
    final text = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('个人签名'),
        content: TextField(
          controller: ctrl,
          maxLines: 3,
          autofocus: true,
          decoration: const InputDecoration(
            hintText: '介绍一下自己',
            border: OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Get.back(), child: const Text('取消')),
          FilledButton(
            onPressed: () => Get.back(result: ctrl.text.trim()),
            child: const Text('确定'),
          ),
        ],
      ),
    );
    ctrl.dispose();
    if (text == null) return;
    await _changeProfile('bio', text);
  }

  // ────────────────── 渲染 ──────────────────

  @override
  Widget build(BuildContext context) {
    return PopScope(
      // ⚠️ 必须是 `canPop: false` + 手动 pop 才能带上结果：
      // `onPopInvokedWithResult(didPop: true)` 表示**已经弹完了**，
      // 那时再调 `Get.back` 会把**上一个页面**也弹掉。
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        // 改过就把 changed 带回去，让主页决定要不要重拉
        Get.back(result: _changed);
      },
      child: Scaffold(
        appBar: AppBar(title: const Text('编辑资料')),
        body: _loading
            ? const Center(child: M3EWavyProgressIndicator())
            : RefreshIndicator(
                onRefresh: _fetchProfile,
                child: ListView(
                  children: [
                    _avatarRow(),
                    const Divider(height: 1, indent: 16),
                    _coverRow(),
                    const Divider(height: 1, indent: 16),
                    _textRow(
                      label: '用户名',
                      value: _profile?.username ?? '',
                      onTap: () => SmartDialog.showToast('用户名修改功能暂不提供'),
                    ),
                    const Divider(height: 1, indent: 16),
                    _textRow(
                      label: '性别',
                      value: switch (_profile?.gender) {
                        1 => '男',
                        0 => '女',
                        _ => '保密',
                      },
                      onTap: _pickGender,
                    ),
                    const Divider(height: 1, indent: 16),
                    _textRow(
                      label: '生日',
                      value: _birthdayText(),
                      onTap: _pickBirthday,
                    ),
                    const Divider(height: 1, indent: 16),
                    _textRow(
                      label: '城市',
                      value: _cityText(),
                      onTap: _pickCity,
                    ),
                    const Divider(height: 1, indent: 16),
                    _textRow(
                      label: '个人签名',
                      value: (_profile?.bio == null || _profile!.bio!.trim().isEmpty)
                          ? '未填写'
                          : _profile!.bio!.trim(),
                      onTap: _editBio,
                    ),
                    const Divider(height: 1, indent: 16),
                  ],
                ),
              ),
      ),
    );
  }

  String _birthdayText() {
    final p = _profile;
    if (p?.birthyear == null || p?.birthmonth == null || p?.birthday == null) {
      return '保密';
    }
    return '${p!.birthyear}-${p.birthmonth}-${p.birthday}';
  }

  String _cityText() {
    final p = _profile;
    if ((p?.province ?? '').isEmpty && (p?.city ?? '').isEmpty) return '保密';
    final province = p?.province ?? '';
    final city = p?.city ?? '';
    if (city.isEmpty || city == province) return province;
    return '$province $city';
  }

  /// 头像行（右侧是圆形预览）
  Widget _avatarRow() {
    return InkWell(
      onTap: _pickAvatar,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            const Expanded(
              child: Text('头像', style: TextStyle(fontSize: 16)),
            ),
            ClipOval(
              child: SizedBox(
                width: 44,
                height: 44,
                child: (_profile?.userAvatar ?? '').isEmpty
                    ? const ColoredBox(color: Colors.black12)
                    : networkImage(_profile!.userAvatar, fit: BoxFit.cover),
              ),
            ),
            const _Arrow(),
          ],
        ),
      ),
    );
  }

  /// 背景图行（右侧是宽幅预览）
  Widget _coverRow() {
    return InkWell(
      onTap: _pickCover,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            const Expanded(
              child: Text('个人主页背景图', style: TextStyle(fontSize: 16)),
            ),
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: SizedBox(
                width: 72,
                height: 40,
                child: (_profile?.cover ?? '').isEmpty
                    ? const ColoredBox(color: Colors.black12)
                    : networkImage(_profile!.cover, fit: BoxFit.cover),
              ),
            ),
            const _Arrow(),
          ],
        ),
      ),
    );
  }

  /// 普通文本行：标签 16sp + 值 15sp（次要色）+ `›`
  Widget _textRow({
    required String label,
    required String value,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        child: Row(
          children: [
            Expanded(
              child: Text(label, style: const TextStyle(fontSize: 16)),
            ),
            Flexible(
              child: Text(
                value,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.right,
                style: TextStyle(
                  fontSize: 15,
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            ),
            const _Arrow(),
          ],
        ),
      ),
    );
  }
}

class _Arrow extends StatelessWidget {
  const _Arrow();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 6),
      child: Text(
        '›',
        style: TextStyle(
          fontSize: 15,
          color: Theme.of(context).colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }
}

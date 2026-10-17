import 'dart:async';
import 'dart:io';
import 'dart:math';
import 'dart:ui' as ui;

import 'package:chat_bottom_container/chat_bottom_container.dart';
import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_smart_dialog/flutter_smart_dialog.dart';
import 'package:get/get.dart' hide Response, FormData;
import 'package:image_picker/image_picker.dart';
import 'package:mime/mime.dart';
import 'package:uuid/uuid.dart';

import '../../../components/no_splash_factory.dart';
import '../../../logic/model/feed/data_model.dart';
import '../../../logic/model/login/login_response.dart';
import '../../../logic/model/oss_upload/datum.dart';
import '../../../logic/model/oss_upload/oss_upload_model.dart';
import '../../../logic/network/network_repo.dart';
import '../../../pages/feed/reply/emoji_panel.dart';
import '../../../pages/feed/reply/rating_panel.dart';
import '../../../pages/feed/reply/reply_dialog.dart' show ReplyType;
import '../../../pages/feed/reply/toolbar_icon_button.dart';
import '../../../utils/extensions.dart';
import '../../../utils/oss/aliyunoss_client.dart';
import '../../../utils/oss/aliyunoss_config.dart';
import '../../../utils/oss_util.dart';
import '../../../utils/utils.dart';

enum PanelType { none, keyboard, emoji }

class ReplyPage extends StatefulWidget {
  const ReplyPage({
    super.key,
    this.type,
    this.id,
    this.username,
    this.targetType,
    this.targetId,
    this.title,
    this.ratingItems,
  });

  final ReplyType? type;
  final dynamic id;
  final String? username;
  final String? targetType;
  final dynamic targetId;
  final String? title;

  /// **发表点评**（`type == ReplyType.rating`）用的评分子项定义。
  ///
  /// 来自 `product/detail` 的 `rating_item_info`：
  /// `[{id: 'v4_score_item_1', name: '续航', …}, …]`。
  /// 提交时子项字段名直接取 `id`（`v4_score_item_1..n`）。
  final List<Map<String, dynamic>>? ratingItems;

  @override
  State<ReplyPage> createState() => _ReplyPageState();
}

class _ReplyPageState extends State<ReplyPage>
    with SingleTickerProviderStateMixin {
  late final _focusNode = FocusNode();
  late final _controller = ChatBottomPanelContainerController<PanelType>();
  late final _replyContentController = TextEditingController(
      text: widget.targetType == 'tag' ? '#${widget.title}# ' : null);
  // PanelType _currentPanelType = PanelType.none;
  bool _readOnly = false;
  final _readOnlyStream = StreamController<bool>();
  late final _enableSend = StreamController<bool>();
  bool _enablePublish = false;
  final _publishStream = StreamController<bool>();
  bool _selectKeyboard = true;
  final _keyboardStream = StreamController<bool>.broadcast();

  final _checkBoxStream = StreamController<bool>();
  bool _checkBoxValue = false;
  final TextEditingController _captchaController = TextEditingController();

  late final _imagePicker = ImagePicker();
  late final _pathStream = StreamController<List<String>>();
  late final _pathList = <String>[];
  late final _modelList = <OssUploadModel>[];
  String? _pic;

  late final _scrollController = ScrollController();

  /// 发表点评时的表单快照（由 [RatingPanel] 回填）
  RatingFormValue? _ratingValue;

  bool get _isRating => widget.type == ReplyType.rating;

  @override
  void initState() {
    super.initState();
    () async {
      await Future.delayed(const Duration(milliseconds: 300));
      if (mounted) {
        _focusNode.requestFocus();
      }
    }();
  }

  @override
  void dispose() async {
    _readOnlyStream.close();
    _enableSend.close();
    _focusNode.dispose();
    _publishStream.close();
    _checkBoxStream.close();
    _pathStream.close();
    _replyContentController.dispose();
    _captchaController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MediaQuery.removePadding(
        removeTop: true,
        context: context,
        child: Scaffold(
          resizeToAvoidBottomInset: false,
          backgroundColor: Colors.transparent,
          body: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Expanded(
                child: InkWell(
                  highlightColor: Colors.transparent,
                  splashColor: Colors.transparent,
                  splashFactory: NoSplashFactory(),
                  onTap: () {
                    FocusScope.of(context).unfocus();
                    Get.back();
                  },
                ),
              ),
              _buildInputView(),
              _buildImagePreview(),
              _buildPanelContainer(),
            ],
          ),
        ));
  }

  Widget _buildPanelContainer() {
    return ChatBottomPanelContainer<PanelType>(
      controller: _controller,
      inputFocusNode: _focusNode,
      otherPanelWidget: (type) {
        if (type == null) return const SizedBox.shrink();
        switch (type) {
          case PanelType.emoji:
            return _buildEmojiPickerPanel();
          default:
            return const SizedBox.shrink();
        }
      },
      // onPanelTypeChange: (panelType, data) {
      //   debugPrint('panelType: $panelType');
      //   switch (panelType) {
      //     case ChatBottomPanelType.none:
      //       _currentPanelType = PanelType.none;
      //       break;
      //     case ChatBottomPanelType.keyboard:
      //       _currentPanelType = PanelType.keyboard;
      //       break;
      //     case ChatBottomPanelType.other:
      //       if (data == null) return;
      //       switch (data) {
      //         case PanelType.emoji:
      //           _currentPanelType = PanelType.emoji;
      //           break;
      //         default:
      //           _currentPanelType = PanelType.none;
      //           break;
      //       }
      //       break;
      //   }
      // },
      panelBgColor: Theme.of(context).colorScheme.surface,
    );
  }

  Widget _buildEmojiPickerPanel() {
    double height = 200;
    final keyboardHeight = _controller.keyboardHeight;
    if (keyboardHeight != 0) {
      height = max(height, keyboardHeight);
    }
    return SizedBox(
      height: height,
      child: Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.of(context).padding.bottom),
        child: EmotePanel(
          index: 1,
          onClick: (emoji) {
            _onChooseEmote(emoji);
            if (!_enablePublish) {
              _enablePublish = true;
              _publishStream.add(true);
            }
          },
        ),
      ),
    );
  }

  Widget _buildImagePreview() {
    return StreamBuilder(
      stream: _pathStream.stream,
      builder: (context, snapshot) {
        if (snapshot.hasData && snapshot.data.isSafeNotEmpty) {
          return Container(
            height: 85,
            color: Theme.of(context).colorScheme.surface,
            padding: const EdgeInsets.only(bottom: 10),
            child: Scrollbar(
              controller: _scrollController,
              thumbVisibility: Utils.isDesktop,
              interactive: Utils.isDesktop,
              thickness: Utils.isDesktop ? 8 : 0,
              child: ListView.separated(
                controller: _scrollController,
                scrollDirection: Axis.horizontal,
                physics: const AlwaysScrollableScrollPhysics(
                  parent: BouncingScrollPhysics(),
                ),
                padding: const EdgeInsets.symmetric(horizontal: 15),
                itemCount: _pathList.length,
                itemBuilder: (context, index) => GestureDetector(
                  onTap: () {
                    _pathList.removeAt(index);
                    _modelList.removeAt(index);
                    _pathStream.add(_pathList);
                  },
                  child: Image(
                    height: 75,
                    fit: BoxFit.fitHeight,
                    filterQuality: FilterQuality.low,
                    image: FileImage(File(_pathList[index])),
                  ),
                ),
                separatorBuilder: (_, index) => const SizedBox(width: 10),
              ),
            ),
          );
        } else {
          return const SizedBox.shrink();
        }
      },
    );
  }

  Widget _buildInputView() {
    return Container(
      clipBehavior: Clip.hardEdge,
      decoration: BoxDecoration(
        borderRadius: const BorderRadius.only(
          topLeft: Radius.circular(12),
          topRight: Radius.circular(12),
        ),
        color: Theme.of(context).colorScheme.surface,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // ★ 发表点评：评分面板占住上半部分，下面的输入框只写正文
          //   （Android 原注释：「评分面板占住主区域，输入框只写正文」）
          if (_isRating)
            RatingPanel(
              target: widget.title ?? '',
              items: widget.ratingItems ?? const [],
              onChanged: (value) {
                _ratingValue = value;
                // 评分面板齐了才允许发布
                if (value.valid != _enablePublish) {
                  _enablePublish = value.valid;
                  _publishStream.add(value.valid);
                }
              },
            ),
          Container(
            padding:
                const EdgeInsets.only(top: 12, right: 15, left: 15, bottom: 10),
            child: Form(
              autovalidateMode: AutovalidateMode.onUserInteraction,
              child: Listener(
                onPointerUp: (event) {
                  if (_readOnly) {
                    updatePanelType(PanelType.keyboard);
                    if (!_selectKeyboard) {
                      _selectKeyboard = true;
                      _keyboardStream.add(true);
                    }
                  }
                },
                child: StreamBuilder(
                  initialData: false,
                  stream: _readOnlyStream.stream,
                  builder: (context, snapshot) => TextField(
                    minLines: 4,
                    maxLines: 8,
                    maxLength: 500,
                    autofocus: false,
                    focusNode: _focusNode,
                    readOnly: snapshot.data ?? false,
                    controller: _replyContentController,
                    onChanged: (value) {
                      bool isNotEmpty = value.replaceAll('\n', '').isNotEmpty;
                      if (isNotEmpty && !_enablePublish) {
                        _enablePublish = true;
                        _publishStream.add(true);
                      } else if (!isNotEmpty && _enablePublish) {
                        _enablePublish = false;
                        _publishStream.add(false);
                      }
                    },
                    decoration: InputDecoration(
                      hintText: widget.username != null
                          ? '回复: ${widget.username}'
                          : '发布${widget.title != null ? '于: ${widget.title}' : '动态'}',
                      border: InputBorder.none,
                      hintStyle: const TextStyle(fontSize: 14),
                    ),
                    style: Theme.of(context).textTheme.bodyLarge,
                  ),
                ),
              ),
            ),
          ),
          Divider(
            height: 1,
            color: Theme.of(context).dividerColor.withValues(alpha: 0.1),
          ),
          Container(
            height: 52,
            padding: const EdgeInsets.only(left: 12, right: 12),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                StreamBuilder(
                  initialData: true,
                  stream: _keyboardStream.stream,
                  builder: (_, snapshot) => ToolbarIconButton(
                    onPressed: () {
                      if (!_selectKeyboard) {
                        _selectKeyboard = true;
                        _keyboardStream.add(true);
                        updatePanelType(PanelType.keyboard);
                      }
                    },
                    icon: const Icon(Icons.keyboard, size: 22),
                    selected: snapshot.data!,
                  ),
                ),
                const SizedBox(width: 10),
                StreamBuilder(
                  initialData: true,
                  stream: _keyboardStream.stream,
                  builder: (_, snapshot) => ToolbarIconButton(
                    onPressed: () {
                      if (_selectKeyboard) {
                        _selectKeyboard = false;
                        _keyboardStream.add(false);
                        updatePanelType(PanelType.emoji);
                      }
                    },
                    icon: const Icon(Icons.emoji_emotions, size: 22),
                    selected: !snapshot.data!,
                  ),
                ),
                const SizedBox(width: 10),
                ToolbarIconButton(
                  selected: false,
                  icon: const Icon(Icons.image, size: 22),
                  onPressed: () async {
                    List<XFile> pickedFiles = await _imagePicker.pickMultiImage(
                      limit: 9,
                      imageQuality: 100,
                    );
                    if (pickedFiles.isNotEmpty) {
                      try {
                        for (int i = 0; i < pickedFiles.length; i++) {
                          if (_pathList.length == 9) {
                            SmartDialog.dismiss();
                            SmartDialog.showToast('最多选择9张图片');
                            if (i != 0) {
                              _pathStream.add(_pathList);
                            }
                            break;
                          } else {
                            SmartDialog.showLoading(
                                msg: '正在加载图片: $i/${pickedFiles.length}');
                            Uint8List imageBytes =
                                await pickedFiles[i].readAsBytes();
                            ui.Image image =
                                await decodeImageFromList(imageBytes);
                            int width = image.width;
                            int height = image.height;
                            Digest md5Hash = md5.convert(imageBytes);
                            String mimeType =
                                lookupMimeType(pickedFiles[i].path) ??
                                    'image/png';
                            String name =
                                '${const Uuid().v1().replaceAll('-', '')}.${mimeType.replaceFirst('image/', '')}';
                            OssUploadModel uploadModel = OssUploadModel(
                              name: name,
                              resolution: '${width}x$height',
                              md5: md5Hash.toString(),
                            );
                            _pathList.add(pickedFiles[i].path);
                            _modelList.add(uploadModel);
                            if (i == pickedFiles.length - 1) {
                              SmartDialog.dismiss();
                              _pathStream.add(_pathList);
                            }
                          }
                        }
                      } catch (e) {
                        SmartDialog.dismiss();
                        debugPrint(e.toString());
                      }
                    }
                  },
                ),
                const Spacer(),
                // ★ 点评模式隐藏「回复并转发」
                //   （Android：`binding.checkBox.isVisible = false` 在 rating 分支里）
                if (!_isRating)
                  StreamBuilder(
                    initialData: false,
                    stream: _checkBoxStream.stream,
                    builder: (_, snapshot) => TextButton(
                    onPressed: () {
                      _checkBoxValue = !_checkBoxValue;
                      _checkBoxStream.add(_checkBoxValue);
                    },
                    style: FilledButton.styleFrom(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 9),
                      visualDensity: const VisualDensity(
                        horizontal: -2,
                        vertical: -2,
                      ),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Checkbox(
                          side: BorderSide(
                              width: 2,
                              color: Theme.of(context).colorScheme.outline),
                          fillColor: WidgetStateProperty.resolveWith((states) {
                            if (states.contains(WidgetState.selected)) {
                              return Theme.of(context).colorScheme.primary;
                            }
                            return null;
                          }),
                          value: _checkBoxValue,
                          onChanged: null,
                          visualDensity:
                              const VisualDensity(horizontal: -4, vertical: -4),
                        ),
                        Text(
                          widget.type == null ? '仅自己可见' : '回复并转发',
                          style: TextStyle(
                            height: 1,
                            color: _checkBoxValue
                                ? Theme.of(context).colorScheme.onSurface
                                : Theme.of(context).colorScheme.outline,
                          ),
                          strutStyle: const StrutStyle(height: 1, leading: 0),
                        ),
                      ],
                    ),
                  ),
                ),
                const Spacer(),
                StreamBuilder(
                  initialData: false,
                  stream: _publishStream.stream,
                  builder: (_, snapshot) => FilledButton.tonal(
                    onPressed: snapshot.data == true ? _onPublish : null,
                    style: FilledButton.styleFrom(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 20,
                        vertical: 10,
                      ),
                      visualDensity: const VisualDensity(
                        horizontal: -2,
                        vertical: -2,
                      ),
                    ),
                    child: const Text('发布'),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void updatePanelType(PanelType type) async {
    final isSwitchToKeyboard = PanelType.keyboard == type;
    final isSwitchToEmojiPanel = PanelType.emoji == type;
    bool isUpdated = false;
    switch (type) {
      case PanelType.keyboard:
        updateInputView(isReadOnly: false);
        break;
      case PanelType.emoji:
        isUpdated = updateInputView(isReadOnly: true);
        break;
      default:
        break;
    }

    updatePanelTypeFunc() {
      _controller.updatePanelType(
        isSwitchToKeyboard
            ? ChatBottomPanelType.keyboard
            : ChatBottomPanelType.other,
        data: type,
        forceHandleFocus: isSwitchToEmojiPanel
            ? ChatBottomHandleFocus.requestFocus
            : ChatBottomHandleFocus.none,
      );
    }

    if (isUpdated) {
      // Waiting for the input view to update.
      WidgetsBinding.instance.addPostFrameCallback((timeStamp) {
        updatePanelTypeFunc();
      });
    } else {
      updatePanelTypeFunc();
    }
  }

  void hidePanel() async {
    if (_focusNode.hasFocus) {
      await Future.delayed(const Duration(milliseconds: 100));
      _focusNode.unfocus();
    }
    updateInputView(isReadOnly: false);
    if (ChatBottomPanelType.none == _controller.currentPanelType) return;
    _controller.updatePanelType(ChatBottomPanelType.none);
  }

  bool updateInputView({
    required bool isReadOnly,
  }) {
    if (_readOnly != isReadOnly) {
      _readOnly = isReadOnly;
      _readOnlyStream.add(_readOnly);
      return true;
    }
    return false;
  }

  Future _onPublish() async {
    try {
      if (_pathList.isNotEmpty) {
        OssDatum? data = await OssUtil.onPostOSSUploadPrepare(
          'image',
          'feed',
          _modelList,
        );
        if (data != null) {
          _pic = data.fileInfo!
              .map((item) =>
                  '${data.uploadPrepareInfo!.uploadImagePrefix}/${item.uploadFileName}')
              .toList()
              .join(',');
          AliyunOssClient client = AliyunOssClient(
            accessKeyId: data.uploadPrepareInfo!.accessKeyId!,
            accessKeySecret: data.uploadPrepareInfo!.accessKeySecret!,
            securityToken: data.uploadPrepareInfo!.securityToken!,
          );
          AliyunOssConfig config = AliyunOssConfig(
            endpoint: data.uploadPrepareInfo!.endPoint!,
            bucket: data.uploadPrepareInfo!.bucket!,
            directory: "feed",
          );
          for (int i = 0; i < data.fileInfo!.length; i++) {
            SmartDialog.showLoading(msg: '正在上传图片 $i/${data.fileInfo!.length}');
            AliyunOssResult result = await client.upload(
              id: i.toString(),
              config: config,
              ossFileName:
                  data.fileInfo![i].uploadFileName!.replaceFirst('feed', ''),
              filePath: _pathList[i],
            );
            if (result.state == AliyunOssResultState.fail) {
              SmartDialog.dismiss();
              SmartDialog.showToast('#$i 上传失败: ${result.msg}');
              return;
            }
          }
        }
      }
      SmartDialog.showLoading(msg: '正在发布');
      if (_isRating) {
        // ★ 发表点评：字段口径照抄 Android `ReplyActivity` 的 rating 分支
        //
        //   - `rating_score_1` = 总体分（**1~5 星，原值**；6+ 服务端回「请正确打分」）
        //   - `v4_score_item_n` = 子项分（**星数 × 2**，0~10 量纲；
        //     实测真机提交 10/8/6/4/2）
        //   - `publish_status` **固定 0**（点评不公开，与动态的 1 不同）
        //   - `comment_general` 固定空串（安卓的上手点评不填总结）
        final value = _ratingValue;
        if (value == null || !value.valid) {
          SmartDialog.dismiss();
          SmartDialog.showToast('请给总体评分和每个分项都打分');
          return;
        }
        final form = <String, dynamic>{
          'id': '',
          'message': _replyContentController.text,
          'type': 'rating',
          'status': '1',
          'publish_status': '0',
          if (widget.targetType != null) 'targetType': widget.targetType,
          if (widget.targetId != null) 'targetId': widget.targetId,
          'rating_score_1': value.overall.toString(),
          'comment_good': value.good,
          'comment_general': '',
          'comment_bad': value.bad,
          'buy_status': value.buyStatus ? '1' : '0',
          'pic': _pic,
        };
        final items = widget.ratingItems ?? const [];
        for (var i = 0; i < value.itemStars.length && i < items.length; i++) {
          // 字段名取子项自己的 id（服务端给的就是 v4_score_item_n）
          final key = items[i]['id']?.toString() ?? 'v4_score_item_${i + 1}';
          form[key] = (value.itemStars[i] * 2).toString();
        }
        final response = await NetworkRepo.postCreateFeed(
          FormData.fromMap(form),
        );
        LoginResponse data = LoginResponse.fromJson(response.data);
        SmartDialog.dismiss();
        if (!data.message.isNullOrEmpty) {
          SmartDialog.showToast(data.message!);
          if (data.messageStatus == 'err_request_captcha') {
            _onGetCaptcha();
          }
        } else {
          SmartDialog.showToast('点评成功');
          Get.back();
        }
        return;
      }
      if (widget.id == null) {
        Response response = await NetworkRepo.postCreateFeed(
          FormData.fromMap(
            {
              'type': 'feed',
              if (widget.targetType != null) 'targetType': widget.targetType,
              if (widget.targetType == 'apk') 'type': 'comment',
              if (widget.targetId != null) 'targetId': widget.targetId,
              'message': _replyContentController.text,
              'status': _checkBoxValue ? -1 : 1,
              'pic': _pic,
            },
          ),
        );
        LoginResponse data = LoginResponse.fromJson(response.data);
        if (!data.message.isNullOrEmpty) {
          SmartDialog.dismiss();
          SmartDialog.showToast(data.message!);
          if (data.messageStatus == 'err_request_captcha') {
            _onGetCaptcha();
          }
        } else if (data.data != null) {
          SmartDialog.dismiss();
          SmartDialog.showToast('发布成功');
          Get.back();
        }
      } else {
        Response response = await NetworkRepo.postReply(
          FormData.fromMap({
            'message': _replyContentController.text,
            'replyAndForward': _checkBoxValue ? 1 : 0,
            'pic': _pic,
          }),
          widget.id,
          widget.type!.name,
        );
        DataModel data = DataModel.fromJson(response.data);
        if (!data.message.isNullOrEmpty) {
          SmartDialog.dismiss();
          SmartDialog.showToast(data.message!);
          if (data.messageStatus == 'err_request_captcha') {
            _onGetCaptcha();
          }
        } else if (response.data != null) {
          SmartDialog.dismiss();
          SmartDialog.showToast('回复成功');
          Get.back(result: {'data': data.data});
        }
      }
    } catch (e) {
      SmartDialog.dismiss();
      debugPrint(e.toString());
    }
  }

  Future<void> _onGetCaptcha() async {
    try {
      Response response = await NetworkRepo.getValidateCaptcha();
      if (mounted) {
        _captchaController.clear();
        showDialog(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('验证码'),
            content: IntrinsicHeight(
              child: Row(
                children: [
                  Expanded(
                    flex: 1,
                    child: Image.memory(response.data),
                  ),
                  Expanded(
                    flex: 1,
                    child: TextField(
                      controller: _captchaController,
                      autofocus: true,
                      inputFormatters: [
                        LengthLimitingTextInputFormatter(4),
                        FilteringTextInputFormatter.allow(
                            RegExp("[0-9a-zA-Z]")),
                      ],
                      decoration: const InputDecoration(
                        border: UnderlineInputBorder(),
                        filled: true,
                        labelText: 'captcha',
                      ),
                    ),
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Get.back(),
                child: const Text('取消'),
              ),
              TextButton(
                onPressed: () {
                  Get.back();
                  _postRequestValidate();
                },
                child: const Text('验证并继续'),
              ),
            ],
          ),
        );
      }
    } catch (e) {
      SmartDialog.showToast('无法获取验证码: $e');
      debugPrint(e.toString());
    }
  }

  Future<void> _postRequestValidate() async {
    try {
      Response response = await NetworkRepo.postRequestValidate(
        FormData.fromMap({
          'type': 'err_request_captcha',
          'code': _captchaController.text,
        }),
      );
      LoginResponse data = LoginResponse.fromJson(response.data);
      if (data.data == '验证通过') {
        _onPublish();
      } else if (!data.message.isNullOrEmpty) {
        SmartDialog.showToast(data.message!);
        if (data.message == "请输入正确的图形验证码") {
          _onGetCaptcha();
        }
      }
    } catch (e) {
      debugPrint(e.toString());
    }
  }

  void _onChooseEmote(String emoji) {
    if (!_enablePublish) {
      _enablePublish = true;
      _publishStream.add(true);
    }
    final int cursorPosition = _replyContentController.selection.baseOffset;
    final String currentText = _replyContentController.text;
    final String newText = currentText.substring(0, cursorPosition) +
        emoji +
        currentText.substring(cursorPosition);
    _replyContentController.value = TextEditingValue(
      text: newText,
      selection: TextSelection.collapsed(offset: cursorPosition + emoji.length),
    );
  }
}

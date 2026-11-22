# 回复图片点击与引导取号开关（2026-11-20）

## 用户需求

- 评论区楼中楼回复带图时，点击图片查看链接不能误触文字复制/选中，应打开已有全屏图片查看器及原图。
- 将首次引导的「自动刷新数字联盟ID」开关从第二页机型参数移至第三页取号。
- 复测帖子：https://www.coolapk1s.com/feed/75161214 ，首条置顶评论的回复有图片。

## 根因与实现

`SelectableLinkMovementMethod`继承系统`ArrowKeyMovementMethod`，本已有无选区/无拖动/非长按的`ClickableSpan`短按处理，但`applyTo`却装入普通`ArrowKeyMovementMethod.getInstance()`，实际没有启用自定义链接处理。楼中楼XML开启`textIsSelectable`，Adapter正确生成图片链接span，却只能触发选择交互。

- 改为每个可选择TextView独立安装`SelectableLinkMovementMethod`，重绑定同一View复用其处理器。
- 保留原生长按选字、拖动手柄和复制菜单，不恢复一按直接复制，不铺楼中楼九宫格，不重复添加图片链接。
- 滑动超阈值、取消、多点触摸、既有选区或长按均不分发链接短按。
- 图片仍走`MyURLSpan`→`ImageUtil.startBigImgViewSimple`→Mojito已有缩略图/原图列表，保留原图画质设置和下载交互。

引导：

- 自动刷新开关只在第三页显示；第三页有已配置非空ID且用户点下一步后才保存开关。
- 第二页不显示、不保存开关，原设置中的开关入口保留。
- 保持已有明确选择、首次无选择默认开启、Bundle旋转恢复与取号返回重绘的草稿状态。
- 正文加入可滚动区域，底部下一步固定，避免第三页新增控件在小屏被裁切。
- 不提升引导revision、不改版本号，升级不强迫已完成引导者再走一遍。

## 验证状态

- 已在原正式10011安装上打开指定帖子，确认首条置顶评论楼中楼存在「查看图片(1)」。点击后旧版未进入图片浏览器，目标文字进入选中高亮。
- 新增Robolectric回归测试：处理器独立/重绑保留、链接短按只触发一次、正文/空白不误开、既有选区、长按、拖出再返回、取消事件。
- 构建仅用debug测试线CI，不本地编译、不推main、不改release.vision。
- 第一轮`1d06aa71`，CI [#672](https://github.com/kongwufang/c001apk_next/actions/runs/44724177526) 的短按正例断言失败，六个处理器/负例通过，构建因此停止。未产生可验收APK，不能据此宣称修复成功。
- 第二轮`4ec33685`，CI [#673](https://github.com/kongwufang/c001apk_next/actions/runs/44725324533) 仍失败，完整诊断定位为测试几何：期望图片span为7..14，而文字坐标x=14实际返回offset=20、hits=0，所有短按都没命中链接，不能据此判断业务处理失败。
- 第三轮`9ca11fd5`，CI [#674](https://github.com/kongwufang/c001apk_next/actions/runs/44725970632) **通过**。改用Robolectric NATIVE文字度量并加构造期坐标命中前置断言，防止负例假绿；9项新增测试（含未聚焦首次点击、普通OnClick不随链接触发）及既有单元测试通过，assembleRelease成功。
- CI已验证签名证书SHA-256：`9e7692d0d3f996476c81be80841fc638530eeb1285c8ba325b65ca47af190ec8`。下载产物`_rev/apk/reply-image-ci674.apk`，包SHA-256：`b033c7e55bfd8346d4cc1bdf89e7351ede189a67fb52ee5eb441a9712e121120`。
- 时长守卫测试只覆盖事件持续时间判断，并未模拟Editor真实长按runnable；原生长按复制/选区必须另做设备验收。
- 模拟器升级前已备份应用shared_prefs/databases到本机忽略目录。未清数据、未登录、未改号。

## 设备验收（MEmu Android 9，2026-11-20）

- 覆盖安装10011测试包成功；最终安装仍为`c001apk_next-V1.0.11-debug(10011)`，未清数据。为长按回归曾临时回装原10011正式包作对照，之后已恢复CI674测试包。
- 同帖首条置顶评论内「查看图片(1)」短按进入`net.mikaelzero.mojito.ui.ImageMojitoActivity`；图片可见，不出现复制/回复弹窗。退出再点，重复验证成功。
- 原图证据：源URL为`https://image.coolapk.com/feed/2026/1120/11/391423_572938ea_4517_56_940@1272x2772.jpg`，其SHA-256恰为Glide下载缓存key `47429513f9a090640303550aa30f6a65ce2b218290f3900a915bf97c684a62aa`；对应下载文件解码为JPEG **1272×2772**，已实际查看与浏览器同一图片。另有720×1569渲染缓存，不能把它误当唯一原图。缩略图失败时已有原图fallback正常工作。
- 原生长按：在同一楼中楼的非图片文字上精确长按，实际截图显示局部文字选区、两侧手柄和「复制／分享／全选」浮动菜单。按返回退出选区，再短按图片正常进入看图器，未误开回复。
- 部分早期长按尝试只看到整行高亮，不能记为通过；后来用未改动的CopyActivity和旧版同帖独立对照、重新按新dump定位字形坐标后，在修复包确认上述菜单。**未宣称拖拽手柄、剪贴板粘贴或多图翻页已实测。**
- 引导第2页：实际截图只有机型参数入口，无自动刷新开关；点下一步到第3页后显示开关checked=true和已有号状态，下一步可用。
- 第3页草稿：切关时屏幕checked=false，但偏好仍true；旋转到1280×720横屏（dump rotation=1）再回720×1280竖屏，仍第3页且草稿false，偏好仍true。横屏截图已审阅，取号状态、开关及底部按钮均可见。
- 将草稿切回原true再下一步，第4页仅SSL确认，autoRefresh仍true；未点「完成」，未改引导完成状态/revision、SSL或原ID。
- 最终偏好按键哈希对比：仅`reliableLinkConfig`/`userVerifyConfig`两项后台远端配置缓存更新，其余键（ID、设备串、获取时间、机型、自动刷新、引导状态）均与基线一致。系统方向设置已还原accelerometer_rotation=1/user_rotation=0。
- 证据在本机忽略目录`_rev/win_ref/reply_image_20261120/`和`_rev/win_ref/shots/`。主要截图：`reply_actual_nested_picture_after.png`、`fixed_nested_selection_copy.png`、`reply_onboarding_page2_after.png`、`reply_onboarding_page3_after.png`、`reply_onboarding_landscape_draft_off.png`。私有偏好备份不提交。

范围边界：现有true+已有ID的设备状态没有覆盖首次无选择默认开启、已有false、无号禁用或取号失败/取消的独立设备场景；这些逻辑保持原实现/源码检查，未为测试清除用户数据或重新取号。无320/360dp设备实测，仅真实横竖屏。正式main远端不变，版本与发版锁不变；本次仅debug测试线。

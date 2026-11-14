# 搜索排序控件统一

> 日期：2026-11-15｜设备：127.0.0.1:21503｜状态：实现完成，CI / 设备待验收

用户要求：综合页动态段复用动态标签页的描边分段按钮，两处完整显示「综合 / 实时 / 热度 / 评论 / 精准」。本次样式以现有 `ProductSortHeaderAdapter` 为准，按用户明确要求复用，不推断新的官方卡片样式。

## 原因与修改

- 综合页的合成 `sortSelectCard` 被分派到游戏频道的横滚胶囊按钮，样式不同；布局绑定的 `onOpenLink` 还会覆盖代码安装的排序回调，截图出现 `unsupported url: c001apk://search-sort/reply`。
- 动态标签页已经定义五项，但 `wrap_content` 加按钮固有宽度导致第五项被挤出屏幕。
- 两处复用 `ProductSortHeaderAdapter.bindButtons`，搜索专用五段等宽布局、单行显示；产品 / 话题讨论保留三段布局。
- 综合页使用有独立标识的排序列表项，直接调用 `onSearchSort`，不再生成内部跳转链接；选择状态随列表项保存。
- 综合页原先补充动态请求写死 `default / isStrict=0`，排序后还会混入旧动态。本次将卡片和动态分别请求，排序保留上方卡片、重载动态第一页，后续只对动态翻页；精准传 `sort=none / isStrict=1`，其余传 `isStrict=0`。
- 补齐排序菜单选中态的 `dateline / none` 映射，避免切换实时、精准时抛出异常。

## 验证

- UTF-8 文件读取和 XML 解析通过；差异空白检查通过（CRLF 按行尾处理）。
- 不在本机编译。CI 和安装后 UI / 请求参数证据待补。
- 原有 `item_feed_content.xml` 状态变化及未跟踪的交接文档、用户附件不纳入提交。

## 下一步

首版 `1d017064`：CI [#651](https://github.com/kongwufang/c001apk_next/actions/runs/43621808137) 成功，已安装到模拟器。720×1280、density=320（360dp 宽）下，两处五段的横坐标一致：`50..170 / 174..295 / 299..420 / 424..545 / 549..670`，高均为 96px。综合页「评论」「精准」点击后各自 checked=true，动态标签页五项均完整可见。

设备验收发现：切换排序时若先移除旧动态，列表会缩短并夹紧滚动位置，排序条由 y=673 跳到 y=956。补充修复为等待新结果后整体替换动态，加载期间只同步排序条选中态。最终 CI / 设备复验待补。

原始证据：`_rev/win_ref/search_segments_20261115/` 中的 XML / JSON；采集工具 `verify_search_segments.py` / `search_request_evidence.py`，只输出筛选后的非凭据参数。

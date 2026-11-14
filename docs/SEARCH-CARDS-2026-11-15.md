# 搜索综合页数码与话题卡片

> 日期：2026-11-15｜设备：127.0.0.1:21503｜状态：官方证据核对、实现完成；CI / 设备验收待补

## 任务与根因

用户要求先核对官方实现，再修复搜索「14」时数码卡「更多」报错、话题名称缺失，并对齐官方卡片。

- `listCard` 中只要首项是 product，就被 `AppAdapter` 分派到产品参数页的横向比较卡，误显示价格、缺少纵向评分行。
- 实测话题实体是 `entityType=topic`，现有专用行只识别 `feedTopic`。通用实体行的横排容器仍把子标题/标题设成 match_parent，导致名称挤出可见范围。
- 两张卡片 URL 是 `tab://product` / `tab://feedTopic`，通用 URL 解析器不支持，应在当前搜索页切到对应标签，保留关键词。
- 卡片 `description` 分别为「更多相关数码」「更多相关话题」，应作为底部操作，而非话题行上方的摘要。

## 官方证据（先核对，后实现）

| 项目 | 原始证据 | 确认内容 |
|---|---|---|
| listCard | `_rev/ref_res/res/layout/item_list_card.xml` | 内部纵向 RecyclerView，行布局由实体决定 |
| 产品行 | `item_product_view.xml`、`DataBinderMapperImpl.java` 的 `ls7`、重新反编译的 `k7d.java` | 60dp 图标、左右上下12dp、16sp 两行标题/22sp 行高、12sp 热度/讨论、右侧78dp/最小41dp |
| 话题行 | `item_topic_view.xml`、mapper 的 `dz7`、重新反编译的 `wlh.java` | 56dp 图标、左右16dp/上下8dp、16sp 两行标题/22sp 行高、12sp 热度/讨论；allow_rating=0 显示箭头 |
| 评分 | `_rev/search_card_official/tuc.java`（ProductComposeUI.kt） | 产品/话题都调用 `tuc.m17345`，分数23sp、星徽槽14dp、右侧78dp/41dp |
| 徽章 | `_rev/search_card_official/m31.java`（ComposeRatingBar.kt） | 方形底为槽的0.75倍、星形0.44倍、圆角2dp；使用官方 `ic_rating_star_16dp.xml` |
| 星级 | `_rev/search_card_official/lyc.java`（ProductGlobalData.kt）`m15220` | 分数按区间映射，不是除以二：9.2→5，8.4→4.5，7.6→4，6.8→3.5，6→3，5→2.5，4→2，3→1.5，1→1，否则0 |
| 图标圆角 | 官方 `ls7.java` / `dz7.java` 调用 `gji.m9227`，该函数调用 `kki.m10348` | 产品4dp、话题12dp；按绑定实现补齐，而非根据截图估计 |
| 热度字段 | `k7d.java:308` / `wlh.java:222` 及真实响应 | 产品 `hot_num_txt` + `feed_comment_num_txt`；话题 `hot_num_txt` + `commentnum_txt` |

反编译输出保存在 `_rev/search_card_official/`。原始 C7 只含绑定实现，ViewHolder / Compose 在 classes08.dex；徽章 Compose 在 classes06.dex。调用 JDK17 的 `java -cp jadx-1.5.6-all.jar jadx.cli.JadxCLI --single-class ...` 导出；直接 `-jar` 的入口为 GUI，不会按预期输出单类，已改用 CLI。未做本地 Android 编译。

## 可复现的接口样本

`_rev/win_ref/research_search_cards.py` 通过已连接模拟器执行只读搜索请求，复用应用请求头但不输出凭据，完整公开响应落在 `_rev/win_ref/search_cards_14_20261115.json`。

| 卡片 | 模板 | URL | 行实体 | 名称示例 |
|---|---|---|---|---|
| 数码 | listCard | tab://product | product，5项 | MacBook Pro(PROLED XDR) 14、小米平板6 Max 14、小米14、机械革命星耀14、小米14 Ultra |
| 话题 | listCard | tab://feedTopic | topic，5项 | MIUI14、ColorOS14、Android14、MyOS14、小米14 |

评分来自 `rating_average_score`，缺失时才退回 `star_average_score`。话题「小米14」实测 allow_rating=0、评分0，按官方只显示箭头。评分/热度为服务端实时数据，不硬编码截图里的数值。

主机直连请求曾超时，默认网络使用虚拟设备头返回403；改为从模拟器发出与应用一致的只读请求后成功。此失败不应当作接口或字段不存在的证据。

## 实现范围

搜索 URL 对应的两类 listCard 使用独立纵向卡片，不影响产品参数页的横向比较卡。内部复用产品行与专用话题行；评分以普通 View 重现上述 Compose 参数，复用官方矢量星形和火焰轮廓。底部链接经带日志的回调切换搜索标签，不交给通用 URL 解析。

## 验收待补

- CI 成功并覆盖安装；
- 搜索14：产品五行、无价格；话题五个名称、评分/箭头正确；
- 两张卡的底部「更多」切到当前关键词的数码/话题标签，无 unsupported url；
- 产品/话题行打开正确详情；已有五段动态排序不回归。

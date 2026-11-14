# 搜索页卡片与动态排序控件对齐说明（官方 vs c001apk_next）

> 记录日期：2026-11-14
> 依据：官方 App 综合搜索页截图（酷回收 / 晚上刷机 / 14 三个关键词）、`/v6/search` 接口返回结构、本项目现有渲染代码。
> 标注「待抓包确认」的字段表示当前会话未取得该字段的实测值，不要当成已验证结论。

---

## 一、官方综合页（type=all）的结构

官方综合搜索结果页是**分段结构**，从上到下：

```
1. 搜索横幅（sponsorForSearch / 广告卡）
2. 数码卡      listCard，entities 全是 product
3. 话题卡      listCard，entities 全是 feedTopic
4. 动态段      标题「动态」+ 5 个排序按钮 + 动态列表
```

关键点：**第 4 段自己带「动态」标题和排序按钮**，不是页面顶部的控件。
我们的实现把动态直接拼在卡片后面，没有补这一段的分段标题和按钮，所以出现「综合页下方的动态丢了 5 选一」。

---

## 二、各卡片官方样式

### 1. 数码卡（`listCard` + `entityType=product`）

官方是一行一个产品的**纵向列表**，每行从左到右：

```
[产品图标 56dp]  产品名（可两行）
                 🔥 热度数值 · 讨论数
                                        评分数字（大号）+ 五星图标
```

底部右侧或底部整行：`更多相关数码 ›`（该卡片 url 指向产品搜索页）。

- 图标：圆角方形，白底；
- 标题：常规字重，非加粗；
- 次行：火苗图标 + 热度 + 讨论数；
- 评分：右侧大号数字 + 五星，评分数字与星级分开排；
- 价格：商品卡才显示（搜索综合的数码卡不带价格）。

### 2. 话题卡（`listCard` + `entityType=feedTopic`）

与数码卡同构，也是纵向列表行：

```
[话题图标]  话题名（可两行）
            🔥 热度 · 讨论数
                                      评分数字 + 五星
```

底部整行：`更多相关话题`（蓝绿色文字，可点）。最后一个话题行右侧可能出现 `›`。

和数码卡的区别：
- 没有价格；
- 图标为话题头像；
- 底部是蓝绿色的「更多相关话题」文本链接。

### 3. 动态段（feed 列表）

```
动态                 [综合][实时][热度][评论][精确]
─────────────────────────────────────────────────
动态卡片（头像 + 昵称 + 设备/时间 + 正文 + 九宫格/视频 + 底部互动栏）
```

- 左侧标题「动态」为加粗小标题；
- 右侧 5 个胶囊按钮，等间距，选中项高亮描边；
- 按钮与标题同一行，垂直居中；
- 点击任一按钮会重新请求该排序，不改变上方卡片。

---

## 三、接口字段对照

`/v6/search`（`type=all`）返回 `data[]`，每项是 `HomeFeedResponse.Data`：

| 字段 | 含义 | 用途 |
| --- | --- | --- |
| `entityTemplate` | `listCard` / `iconScrollCard` / `imageScaleCard` / `sponsorForSearch` | 决定卡片渲染形态 |
| `entities[]` | 卡片内实体数组 | 决定列表行内容 |
| `entities[].entityType` | `product` / `feedTopic` / `user` / `feed` | 区分数码 / 话题 / 用户 / 动态 |
| `entities[].title` | 产品名或话题名 | 行标题 |
| `entities[].logo` | 图标地址 | 行图标 |
| `entities[].hotNum` | 热度（待抓包确认字段名） | 火苗后的数字 |
| `entities[].commentNum` / `discussNum` | 讨论数（待抓包确认） | 次行第二段 |
| `entities[].score` | 评分（待抓包确认） | 右侧评分与星级 |
| `url` | 卡片跳转地址 | `更多相关数码` / `更多相关话题` |

`type=feed`（动态 tab 与综合页动态段）返回的每项是动态本体，不含卡片包装。

---

## 四、当前实现与本说明的差距

### 已完成

- 综合页卡片能正常显示（数码卡、话题卡都出现）；
- 动态 tab 的 5 个排序按钮正常显示，点击可刷新；
- 排序值映射：`综合=default`、`实时=dateline`、`热度=hot`、`评论=reply`、`精确=none + isStrict=1`。

### 未完成

| 问题 | 现状 | 目标 |
| --- | --- | --- |
| 综合页动态段缺「动态」标题与 5 个排序按钮 | 动态直接追加在卡片后，无分段头 | 与官方一致：分段标题 + 5 按钮 + 列表 |
| 话题卡渲染形态 | 走通用卡片（标题行 + 摘要 + 图标排），与官方纵向列表行不一致 | 纵向列表：图标 + 名称 + 热度讨论 + 评分 + 「更多相关话题」 |
| 数码卡评分/星级排布 | 通用卡片的排布，未按官方左信息右评分 | 左信息右评分，评分大号数字 + 五星 |
| `sponsorForSearch` 横幅 | 未渲染专门样式 | 独立横幅卡 |

### 为什么综合页加按钮不是加一行布局就够

综合页的卡片和动态在**同一个 `AppAdapter` 的数据列表**里，`BaseAppFragment` 只允许一个 `topAdapter()` 挂在列表最顶部。

而官方要求的按钮位置在**卡片之后、动态之前**，属于列表中间的分段头。可选两种做法：

1. **分段适配器**（推荐）：把综合页拆成两个 `AppAdapter` —— 卡片段 + 动态段，用 `ConcatAdapter` 串起来，中间插入「标题 + 排序按钮」适配器。与官方结构一一对应，排序切换只重刷动态段。
2. **合成列表项**：在数据列表里插入一个虚拟项，渲染「动态 + 5 按钮」。改动小，但排序状态和点击事件要额外从 ViewModel 传到 Adapter，耦合更重。

两种做法都需要把 `SearchContentFragment` 的适配器组装从 `BaseAppFragment` 的默认链里拆出来，属于结构性改动，应单独提交并单独验证。



---

## 六、官方逆向工程核对（2026-11-14）

上一版第二、三节中有些内容是根据截图和接口字段推断的，不能直接作为实现依据。本次改为读取项目里的官方逆向产物：

```text
_rev/CARD_UI_MAP.md
_rev/card_scan_out/_CARD_UI_MAP.md
_rev/card_scan_out/_FEED_CHAIN.md
_rev/ref_res/res/layout/item_list_card.xml
_rev/ref_res/res/layout/item_sort_select.xml
```

入口规则见 `HANDOVER.md` §8「逆向研究线」：`_rev/00_RULES_AND_INDEX.md` 是协议研究入口，卡片 UI 底表由 `_rev/card_ui_map.py` 根据 `ref_res/res/layout/` 和反编译产物生成。

### 1. 官方 `listCard` 的真实骨架

官方布局 `ref_res/res/layout/item_list_card.xml` 只有一个纵向根容器和一个 RecyclerView：

```xml
<LinearLayout orientation="vertical" ...>
    <RecyclerView id="list_view" layout_width="match_parent" />
</LinearLayout>
```

因此 `listCard` 本身不决定「产品行」或「话题行」的具体样式，具体行布局由内部列表的 ViewHolder / entity 类型决定。之前把 `listCard` 概括成固定的「评分 + 五星」行，是未经官方布局核对的推断，现撤回。

### 2. 官方 `sortSelectCard` 的真实骨架

官方布局 `ref_res/res/layout/item_sort_select.xml` 给出了动态段排序头的直接证据：

```text
CoolapkCardView（cardStyle=1）
└── FrameLayout fix_top_view，高度 40dp
    └── LinearLayout action_container，水平布局
        ├── owner_filter_view（可选）
        ├── count_text_view
        ├── Space（weight=1）
        ├── capsule_select_view（指示器 + 横向按钮容器）
        └── menu_select_view（文字 + 下拉箭头）
```

官方排序控件不是简单的页面顶部 `topAdapter`。如果综合页的动态段来自服务端 `sortSelectCard`，它应作为**卡片流中的一个中间卡片项**插入，位置由服务端返回顺序决定。

### 3. 官方卡片底表的可信度

`_rev/card_scan_out/_CARD_UI_MAP.md` 明确标注：

| 模板 | 官方布局 | 证据等级 |
| --- | --- | --- |
| `listCard` | `item_list_card.xml` | 硬链名，官方布局已找到 |
| `sortSelectCard` | `item_sort_select.xml` | 硬链变体，官方布局已找到 |
| `iconScrollCard` | 待链；候选 `item_icon_mini_scroll_card.xml` / `item_scroll_card.xml` / `item_image_scroll_card.xml` | 目前不能当作已确认 |
| `feedListCard` | 待链；候选 `item_list_card.xml` / `search_list_card.xml` / `item_doc_list_card.xml` | 目前不能当作已确认 |
| `sponsorForSearch` | `item_sponsor_self_draw` | `_FEED_CHAIN.md` 有实体判定和布局证据 |

底表第 232～238 行还规定：先按模板查官方布局，骨架参数以布局为准；`推断` 和候选布局只能作线索，必须回 XML 核对。



### 5. 官方 `feedTopic` / product 行布局已找到

继续沿官方逆向资源核对后，搜索综合页两种 `listCard` 内部行的可靠布局证据如下：

#### `feedTopic`：`ref_res/res/layout/item_topic_view.xml`

```text
CoolapkCardView，cardStyle=3
└── root_view，横向，左右16dp，上下8dp
    ├── logo_view：56dp × 56dp
    ├── title_view：16sp，最多两行，行高22sp
    ├── description_view：12sp，默认示例「110关注 233讨论」
    ├── arrow_view：右箭头
    └── right_container：最小宽度78dp
        ├── no_one_rated_view：「暂无人评分」
        └── compose_view：评分区域
```

这与用户截图中的官方话题卡一致：左侧 56dp 话题图标，中间两行标题与「关注 / 讨论」，右侧箭头和评分区。当前应用的通用 `GenericCardViewHolder` 不具备这套固定骨架，因此话题卡不对齐。

#### `product`：`ref_res/res/layout/item_product_view.xml`

```text
CoolapkCardView，cardStyle=2
└── item_view，横向，左右12dp，上下12dp
    ├── icon_view：60dp × 60dp
    ├── title_view：16sp，最多两行
    ├── subtitle_container：火焰图标 + hot_num_view + discuss_num_view
    └── right_container：宽78dp，发布时间或评分 ComposeView
```

这两个布局是官方 XML 直接证据，不再使用之前「评分 + 五星」的泛化描述。评分的具体 Compose 内容还需读取官方绑定类，布局只确认了它位于右侧 `compose_view`。

### 6. `sortSelectCard` 的官方注册证据

官方反编译 `EntityListFragment.java` 注册了 `sortSelectCard`（约 828 行），`cm3.java` 的分割规则也明确把它视为独立卡片。`item_sort_select.xml` 的 `capsule_select_view/container_view` 是动态选项容器，说明五选一属于卡片流项，而非只给动态 tab 顶部使用的通用头部。

当前实现下一步应：

1. 为综合页动态段插入一个搜索专用 `sortSelectCard`；
2. 让该卡片的 5 个选项调用现有 `onSearch("sort", ...)`；
3. 为 `feedTopic` 和 `product` 分别增加官方骨架 ViewHolder；
4. 通过实体 `entityType` 区分两种行，不再走通用卡片。

```text
· 综合页动态下方缺少 5 选一：应先确认服务端是否返回 sortSelectCard，
  不能仅在 type=feed 的页面 topAdapter 上假设它属于页面顶部。
· 话题卡不对齐：先确认 listCard 内部 entityType=feedTopic 对应的官方 item 布局，
  不能把 product 行的猜测样式套给话题。
· iconScrollCard 用户卡：当前底表仍是待链，不能直接声称官方是某一个候选 XML。
· sponsorForSearch：官方逆向链已确认对应 item_sponsor_self_draw，
  应按该布局和 binding 继续核对。
```

下一次实现前，必须继续完成 `feedTopic` 内部行和 `sortSelectCard` 的官方 ViewHolder / binding 对照；在此之前不改搜索卡片布局。

- [ ] 综合页：横幅 → 数码卡 → 话题卡 → 动态段（标题 + 5 按钮 + 列表）顺序正确；
- [ ] 综合页点「实时」只刷新动态段，上方卡片不动；
- [ ] 话题卡为纵向列表行，含图标、名称、热度、讨论数、评分；
- [ ] 数码卡评分在右侧，数字与五星分开；
- [ ] 动态 tab 的 5 按钮保持现状，不回归；
- [ ] 普通页面无全局复制等既有行为不受影响。

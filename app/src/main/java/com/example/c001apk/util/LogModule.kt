package com.example.c001apk.util

/**
 * 日志模块（2026-11-08 新增「全局分模块日志」的 A 步）
 *
 * 之前 85 处 `android.util.Log` 的 tag 是随手的字符串（"MyApplication" / "OfflineCache" / 各文件自己的
 * TAG），logcat 里既没法按模块筛，也没法在设置页按模块开关 ✗
 *
 * 这里把 tag 拆成「模块 + 子标签」：
 *   logcat 上看到的 form 是 `M:card/Generic`、`M:net/ShareLinkRepo`
 *   ⇒ `adb logcat -s` / `| findstr M:card` 就能只看某一块
 *   ⇒ 设置页可以按模块勾选要不要落盘
 *
 * 注意：模块是**稳定标识**，会写进导出日志和诊断包，命名不要随手改。
 */
enum class LogModule(val key: String) {

    /** 应用启动 / 生命周期 */
    APP("app"),

    /** 网络层、仓库层（重试、HTTP 码、接口错误） */
    NET("net"),

    /** 动态流（首页 / 关注 / 详情） */
    FEED("feed"),

    /** 用户、关注关系 */
    USER("user"),

    /** 话题、酷图、轮播 */
    TOPIC("topic"),

    /** 搜索 */
    SEARCH("search"),

    /** 消息中心、通知卡、侧栏 */
    NOTICE("notice"),

    /** 卡片渲染（通用卡 / 专属卡 / 规则表） */
    CARD("card"),

    /** 本地缓存、离线、数据库 */
    CACHE("cache"),

    /** 通用 UI（Activity/Fragment/Dialog 生命周期、跳转） */
    UI("ui"),

    /** 更新检查、热更新配置 */
    UPDATE("update"),

    /** 云端开关 / 拦截名单 / 统计上报 */
    GATES("gates"),

    /** 诊断上报本身（注意别自我递归刷屏） */
    DIAG("diag"),

    /** 心跳与事件通道 */
    HB("hb"),
    ;
}

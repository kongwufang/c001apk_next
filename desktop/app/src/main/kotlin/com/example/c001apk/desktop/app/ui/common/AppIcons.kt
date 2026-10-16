package com.example.c001apk.desktop.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘矢量图标。
 *
 * ## 为什么不用 `compose.materialIconsExtended`
 * 那个依赖包是 **36 MB**（`material-icons-extended-desktop-*.jar`），
 * 而本项目只用到 7 个图标 —— 为它们付 36 MB 让每次 CI 产物变大，
 * 也直接拖慢了本地下载验证（实测下载只有 0.03~1.5 MB/s，36 MB 是主要开销）。
 *
 * 所以：**core 包里有的**（Filled.Home / Filled.Person / Filled.Search）继续用；
 * **只有 extended 才有的 outlined 版本**改成这里自绘 —— 零额外依赖。
 *
 * ## 与 Android 端的对齐说明
 * Android 端导航图标是自有矢量图 `ic_home_outline` / `ic_mine_outline`
 * （24dp viewport、`autoMirrored`）。这里按同样的几何意图手写等价路径：
 * 描边风格（`stroke` 而非填充），线宽约 1.8dp，与 outlined 图标观感一致。
 * 后续若要像素级一致，把 Android 端 path 数据搬过来即可。
 */
object AppIcons {

    /** 首页（描边）—— 对应 `ic_home_outline` */
    val HomeOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "HomeOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 屋顶：从左下檐口到顶点再到右下檐口
                moveTo(3.5f, 10.5f)
                lineTo(12f, 3.5f)
                lineTo(20.5f, 10.5f)
                // 右侧墙
                lineTo(20.5f, 20f)
                // 底边（留门洞）
                lineTo(14.5f, 20f)
                lineTo(14.5f, 14.5f)
                lineTo(9.5f, 14.5f)
                lineTo(9.5f, 20f)
                lineTo(3.5f, 20f)
                close()
            }
        }.build()
    }

    /** 我的（描边）—— 对应 `ic_mine_outline`：头 + 肩 */
    val PersonOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "PersonOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 头
                moveTo(12f, 4f)
                curveTo(14.2f, 4f, 16f, 5.8f, 16f, 8f)
                curveTo(16f, 10.2f, 14.2f, 12f, 12f, 12f)
                curveTo(9.8f, 12f, 8f, 10.2f, 8f, 8f)
                curveTo(8f, 5.8f, 9.8f, 4f, 12f, 4f)
                close()
                // 肩
                moveTo(5f, 20f)
                curveTo(5f, 16.5f, 8f, 14.5f, 12f, 14.5f)
                curveTo(16f, 14.5f, 19f, 16.5f, 19f, 20f)
            }
        }.build()
    }

    /** 消息（描边）—— 对应 `ic_message_outline`：圆角气泡 + 左下小尾巴 */
    val MailOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "MailOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 圆角气泡
                moveTo(6f, 4.5f)
                lineTo(18f, 4.5f)
                curveTo(20.2f, 4.5f, 21.5f, 5.8f, 21.5f, 8f)
                lineTo(21.5f, 14f)
                curveTo(21.5f, 16.2f, 20.2f, 17.5f, 18f, 17.5f)
                lineTo(9.5f, 17.5f)
                lineTo(4.5f, 20.5f)
                lineTo(5.5f, 16.8f)
                curveTo(3.9f, 16.3f, 2.5f, 15.3f, 2.5f, 14f)
                lineTo(2.5f, 8f)
                curveTo(2.5f, 5.8f, 3.8f, 4.5f, 6f, 4.5f)
                close()
            }
        }.build()
    }
}

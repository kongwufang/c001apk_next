// app：Compose Desktop 界面层（横屏版 UI）
//
// 打包目标：
//   * 便携版 zip  —— `packageDistributionForCurrentOS` + `packageZip`（CI 默认产出）
//   * MSI 安装包  —— `packageMsi`（GitHub windows-latest 预装 WiX，可选用）
//
// 两者都会内嵌精简 JRE（jpackage），终端用户无需自行安装 Java。
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

val coroutinesVersion: String by rootProject.extra

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$coroutinesVersion")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.example.c001apk.desktop.app.MainKt"

        // 窗口初始尺寸取「横屏」比例（对齐 Android 端 layout-land：左侧导航栏 + 宽内容区）
        // 具体窗口尺寸在 Main.kt 里设置（WindowState）。
        nativeDistributions {
            targetFormats(
                TargetFormat.Msi,
                TargetFormat.Exe,
            )

            packageName = "c001apk-desktop"
            packageVersion = "0.1.0"
            description = "c001apk 桌面端（实验性）"
            vendor = "c001apk_next"

            // jlink 裁剪后的模块集：Compose Desktop 需要的基础模块 + 加密相关（md5/bcrypt 走 JVM 内置）
            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.naming",
                "java.net.http",
                "java.prefs",
                "java.scripting",
                "jdk.crypto.ec",
                "jdk.unsupported",
            )

            windows {
                menu = true
                shortcut = true
                // 固定 UUID，保证后续版本升级安装时识别为同一应用
                upgradeUuid = "3F6C1E2A-9B4D-4A17-9E5C-7D2B8A1F4C60"
            }
        }
    }
}

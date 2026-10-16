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

        // ── 渲染后端 ──
        // Compose Desktop 的渲染层 Skiko 在 Windows 默认走 DirectX 12。
        // 实测在老显卡/驱动上会抛：
        //     org.jetbrains.skiko.RenderException: Failed to create DirectX12 device.
        // 此时窗口往往出不来（进程活着但界面不显示）。实测 SOFTWARE / OPENGL /
        // 关硬件加速都不再抛异常。
        //
        // 因此默认用 SOFTWARE 保证「一定能看到窗口」；需要 GPU 加速的机器可覆盖：
        //   * 环境变量：SKIKO_RENDER_API=OPENGL（或 DIRECT3D）
        //   * 或直接编辑安装目录下 app/c001apk-desktop.cfg 里的 java-options
        // 启动时会打印当前生效值，便于排查。
        jvmArgs += listOf(
            "-Dskiko.renderApi=SOFTWARE",
            "-Dskiko.awt.hardwareAcceleration=false",
        )

        nativeDistributions {
            // 目标格式：安装程序 exe（单文件安装器）+ msi（冗余备份，便于排查）
            // 注意：Compose 插件的 packageDistributionForCurrentOS 会构建【全部】配置的格式。
            targetFormats(
                TargetFormat.Exe,
                TargetFormat.Msi,
            )

            packageName = "c001apk-desktop"
            packageVersion = "0.1.0"
            // ⚠️ 下面这些元数据必须保持 ASCII：
            //    jpackage 在 Windows 上会用【平台默认字符集】(GBK) 读取参数文件，
            //    中文会被解码失败并报 "Input length = 1"（已实测踩坑）。
            description = "c001apk desktop client (experimental)"
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

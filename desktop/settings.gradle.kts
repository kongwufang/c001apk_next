// 桌面端独立构建（与根目录的 Android 工程完全隔离）
//
// 为什么独立：根 settings.gradle.kts 一旦 include 桌面模块，Android CI 在
// 配置阶段就会去解析 Compose 插件，Android 构建会被拖挂。这里用自己的
// settings + wrapper，Android 侧一行都不用改。
//
// 版本与 Android 工程对齐：Kotlin 1.9.24 / Gradle 8.7 / JDK 17
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "c001apk-desktop"

include(":core")
include(":app")
project(":app").projectDir = file("app")

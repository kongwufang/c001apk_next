// 桌面端顶层构建。版本与 Android 工程对齐：Kotlin 1.9.24 / Gradle 8.7 / JDK 17
//
// 注意：Kotlin 1.9.x 的 Compose 编译器是独立插件（org.jetbrains.compose 会自动按
// Kotlin 版本挑选 compose-compiler）；Kotlin 2.0+ 才改成 kotlin.plugin.compose。
// 所以这里不声明 kotlin.plugin.compose。
plugins {
    kotlin("jvm") version "1.9.24" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
}

// 统一依赖版本（桌面端专用，不动 Android 的 gradle/libs.versions.toml）
extra["retrofitVersion"] = "2.11.0"
extra["okhttpVersion"] = "4.12.0"
extra["gsonVersion"] = "2.11.0"
extra["coroutinesVersion"] = "1.8.1"
extra["jbcryptVersion"] = "0.4"
extra["logbackVersion"] = "1.5.6"

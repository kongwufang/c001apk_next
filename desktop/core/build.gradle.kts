// core：纯 Kotlin/JVM 协议层（零 Android 依赖）
//
// 这是整个 Windows 端可复用的核心：数据模型 + AutoMirrored 接口定义 + 鉴权 + 请求头。
// 与 Android 端 logic/ 的对应关系见各文件注释。
plugins {
    kotlin("jvm")
    `java-library`
}

val retrofitVersion: String by rootProject.extra
val okhttpVersion: String by rootProject.extra
val gsonVersion: String by rootProject.extra
val coroutinesVersion: String by rootProject.extra
val jbcryptVersion: String by rootProject.extra

kotlin {
    jvmToolchain(17)
}

dependencies {
    api("com.squareup.retrofit2:retrofit:$retrofitVersion")
    api("com.squareup.retrofit2:converter-gson:$retrofitVersion")
    api("com.squareup.okhttp3:okhttp:$okhttpVersion")
    api("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")
    api("com.google.code.gson:gson:$gsonVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.mindrot:jbcrypt:$jbcryptVersion")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // 是否跑「真实网络」联调测试：-DrunLiveTests=true 才跑
    systemProperty("runLiveTests", System.getProperty("runLiveTests", "false"))
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

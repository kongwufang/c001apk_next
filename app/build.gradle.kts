import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import org.jetbrains.kotlin.konan.properties.Properties
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.rikka.tools.materialthemebuilder)
    alias(libs.plugins.google.dagger.hilt.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
}

apply(plugin = "kotlin-kapt")

kapt {
    generateStubs = true
    correctErrorTypes = true
}

materialThemeBuilder {
    themes {
        for ((name, color) in listOf(
            "Default" to "6750A4",
            "Red" to "F44336",
            "Pink" to "E91E63",
            "Purple" to "9C27B0",
            "DeepPurple" to "673AB7",
            "Indigo" to "3F51B5",
            "Blue" to "2196F3",
            "LightBlue" to "03A9F4",
            "Cyan" to "00BCD4",
            "Teal" to "009688",
            "Green" to "4FAF50",
            "LightGreen" to "8BC3A4",
            "Lime" to "CDDC39",
            "Yellow" to "FFEB3B",
            "Amber" to "FFC107",
            "Orange" to "FF9800",
            "DeepOrange" to "FF5722",
            "Brown" to "795548",
            "BlueGrey" to "607D8F",
            "Sakura" to "FF9CA8"
        )) {
            create("Material$name") {
                lightThemeFormat = "ThemeOverlay.Light.%s"
                darkThemeFormat = "ThemeOverlay.Dark.%s"
                primaryColor = "#$color"
            }
        }
    }
    // Add Material Design 3 color tokens (such as palettePrimary100) in generated theme
    // rikka.material >= 2.0.0 provides such attributes
    generatePalette = true
}

fun String.execute(currentWorkingDir: File = file("./")): String {
    val byteOut = ByteArrayOutputStream()
    rootProject.exec {
        workingDir = currentWorkingDir
        commandLine = split("\\s".toRegex())
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

// ===== 发行版本（唯一真源：仓库根目录 version.properties，发布只改那个文件）=====
// 规则：只有 beta 阶段主动推进版本号，main 继承 beta 的版本号；debug 快速迭代不涨号。
val releaseProps = Properties().also { it.load(rootProject.file("version.properties").inputStream()) }
val verCode = releaseProps.getProperty("VERSION_CODE").trim().toInt()
val verTag = releaseProps.getProperty("VERSION_NAME").trim()
// 发行渠道：CI 按分支传 -Pchannel=release|beta|debug；本地不传默认 release
val channel = (findProperty("channel") as String?)?.takeIf { it.isNotBlank() } ?: "release"
// versionName 统一前缀（与仓库同名）：c001apk_next-V1.0.1-release
val apkPrefix = "c001apk_next"

// 本地机密配置（local.properties 已在 .gitignore 里，不会入库）
val localProperties = Properties().also {
    val properties = rootProject.file("local.properties")
    if (properties.exists())
        it.load(properties.inputStream())
}

/**
 * 数字联盟 ID（szlmId）：设备串 `X-App-Device` 的首字段，同时也是 WebView 的 `DID` cookie。
 *
 * 它是数字联盟（cn.shuzilm.core）服务端在真机上签发的设备标识，**刻意不写进源码**：
 * 一旦随 APK 分发，所有安装都会被服务端算作同一台设备，触发 `-415 账号过多`
 * （历史版本的写死事故见 `util/PrefManager.kt` 的 SZLMID 注释）。
 *
 * 取值优先级：`-PszlmId=xxx` > `local.properties` 里的 `SZLM_ID` > 空串（发布版默认）。
 * 本机自用：往 `local.properties` 写一行 `SZLM_ID=<你自己那份>` 即可，无需改源码。
 * CI 自用：在 workflow 里把同名值写进 local.properties（或传 `-PszlmId=`）。
 */
val szlmId = (findProperty("szlmId") as String?)?.trim()?.takeIf { it.isNotEmpty() }
    ?: localProperties.getProperty("SZLM_ID").orEmpty().trim()

/**
 * 数盟（cn.shuzilm.core）SDK 探针开关：验证「纯靠 SDK 从官方签发 DUID」能否闭环。
 *
 * 默认 **关闭**。不开时 `src/probe/` 下的源码 / assets / jniLibs 一律不参与构建，
 * 产物与改动前**零差异** —— 这是刻意设计：真要分发的包里不该出现数盟 SDK，
 * 更不该背上那张 18.7 MB 的 dex。
 *
 * 开启：`./gradlew :app:assembleRelease -PshuzilmProbe=true`
 *
 * 探针内容见 `src/probe/java/com/example/c001apk/probe/ShuzilmProbe.kt`，
 * 调用口在 `MyApplication`（反射调用，关掉时连编译期依赖都不存在）。
 */
val shuzilmProbe = (findProperty("shuzilmProbe") as String?)?.toBoolean() ?: false

/**
 * 探针实验：让整个应用跑在指定进程名下（`<application android:process>`）。
 *
 * 为什么需要它：数盟 SDK 判定调用方身份用的是 **native 采集的真实进程名**
 * （最可能直接读 `/proc/self/cmdline`）—— 实测把 Java 层 `Context.getPackageName()`
 * 伪装成 `com.coolapk.market` 后 `device_label` 一字未变、device_id 仍是全零，
 * 说明它压根不读 Java 那层。要验证这个判断，只能改**真实进程名**。
 *
 * 留空 = 显式写回本应用包名（等于不设该属性，行为与改动前一致）。
 * 实验时传 `-PprobeProcess=com.coolapk.market`。
 */
val probeProcess = (findProperty("probeProcess") as String?)?.trim().orEmpty()

android {
    // 注意：namespace 决定 R / ViewBinding / DataBinding 生成类的包名，
    // 源码里全是 import com.example.c001apk.R / com.example.c001apk.databinding.*，不能跟着改名
    namespace = "com.example.c001apk"
    compileSdk = 34

    defaultConfig {
        // 包名同样保持不变：改 applicationId 等于换一个 App，老用户无法覆盖安装
        applicationId = "com.example.c001apk"
        minSdk = 24
        targetSdk = 34
        versionCode = verCode
        // 完整 versionName = 前缀-版本号-渠道，渠道后缀由 buildTypes.versionNameSuffix 追加
        versionName = "$apkPrefix-$verTag"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 数字联盟 ID：空串 = 未内置（客户端等同于留空，见 util/PrefManager.kt 的 SZLMID）
        buildConfigField("String", "SZLM_ID", "\"$szlmId\"")

        // 数盟 SDK 探针是否编进本包（默认 false；见文件上方 shuzilmProbe 注释）
        buildConfigField("boolean", "SHUZILM_PROBE", shuzilmProbe.toString())

        // <application android:process> 的取值：留空即显式写回本应用包名，等同于不设
        manifestPlaceholders["appProcess"] = probeProcess.ifEmpty { "com.example.c001apk" }
    }

    // 探针资产只在开关打开时才挂进 main sourceSet，默认构建完全不感知它们的存在
    sourceSets.getByName("main") {
        if (shuzilmProbe) {
            java.srcDir("src/probe/java")
            assets.srcDir("src/probe/assets")
            jniLibs.srcDir("src/probe/jniLibs")
        }
    }

    val config = localProperties.getProperty("KEYSTORE_PATH")?.let {
        signingConfigs.create("release") {
            storeFile = file(it)
            storePassword = localProperties.getProperty("KEYSTORE_PASSWORD")
            keyAlias = localProperties.getProperty("KEY_ALIAS")
            keyPassword = localProperties.getProperty("KEY_PASSWORD")
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        all {
            signingConfig = config ?: signingConfigs["debug"]
            // debug 频道的包 CI 是用 release 变体打的（BuildConfig.DEBUG=false），
            // 但排障需要看云端报文，所以这里单独开一个开关
            buildConfigField("boolean", "HTTP_LOG", (channel == "debug" || name == "debug").toString())
        }
        release {
            // 拼出完整版本名：c001apk_next-V1.0.1-release（beta 分支为 -beta）
            versionNameSuffix = "-$channel"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // 调试包恒为 c001apk_next-V1.0.1-debug
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }
    defaultConfig {
        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
//            abiFilters.add("armeabi")
//            abiFilters.add("x86")
            abiFilters.add("x86_64")
        }
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    applicationVariants.configureEach {
        // APK 文件名与 versionName 严格一致：c001apk_next-V1.0.1-release(10000).apk
        val apkFileName = "$versionName($versionCode)"
        outputs.configureEach {
            (this as? ApkVariantOutputImpl)?.outputFileName = "$apkFileName.apk"
        }
    }
}

configurations.configureEach {
    exclude("androidx.appcompat", "appcompat")
}

dependencies {
    androidTestImplementation(libs.androidx.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.leakcanary.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.core.ktx)
    implementation(libs.google.android.flexbox)
    implementation(libs.google.android.material)
    implementation(libs.google.dagger.hilt.android)
    ksp(libs.google.dagger.hilt.android.compiler)
    implementation(libs.rikkax.borderview)
    implementation(libs.rikkax.material.preference)
    implementation(libs.rikkax.material)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp3.logging.interceptor)
    implementation(libs.glide)
    ksp(libs.glide.ksp)
    implementation(libs.glide.okhttp3.integration)
    implementation(libs.glide.transformations)
    implementation(project(":mojito"))
    implementation(project(":SketchImageViewLoader"))
    implementation(project(":GlideImageLoader"))
    implementation(libs.appcenter.analytics)
    implementation(libs.appcenter.crashes)
    implementation(libs.drakeet.about)
    implementation(libs.jbcrypt)
    implementation(libs.jsoup)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.image.glide)
    testImplementation(libs.junit)
    implementation(libs.oss.android.sdk)
    implementation(libs.utilcode)

}
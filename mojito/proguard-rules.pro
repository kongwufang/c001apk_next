# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in E:\AndroidDeveloper\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
# 实现了 Initializer 接口的类需要在 AndroidManifest 中配置，然后在运行时实例化，因此不能混淆
-keep public class * implements me.panpf.sketch.Initializer

-keep class net.mikaelzero.mojito.bean.* {*;}
-keep class net.mikaelzero.mojito.interfaces.* {*;}
# Nexa Browser ProGuard rules

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class androidx.room.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
-keep class com.nexabrowser.app.data.local.entity.** { *; }

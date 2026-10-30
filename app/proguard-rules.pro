-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.vinstall.alwiz.model.** { *; }
-keep class com.vinstall.alwiz.parser.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

-keepclassmembers enum com.vinstall.alwiz.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep enum com.vinstall.alwiz.settings.InstallMode { *; }
-keep enum com.vinstall.alwiz.settings.DialogStyle { *; }

-keep class com.vinstall.alwiz.ui.FloatingNav { <init>(...); }
-keep class com.vinstall.alwiz.ui.MaxHeightRecyclerView { <init>(...); }
-keep class com.vinstall.alwiz.ui.MaxHeightNestedScrollView { <init>(...); }
-keep class * extends androidx.fragment.app.Fragment { <init>(); }

-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.debug.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**

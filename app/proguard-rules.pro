# Moqi IME ProGuard Rules

-keepclassmembers class com.moqi.im.core.** {
    *;
}
-keepclassmembers class com.moqi.im.engine.** {
    *;
}
-keepclassmembers class com.moqi.im.dict.** {
    *;
}

-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
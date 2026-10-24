# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.tototools.catalogo.**$$serializer { *; }
-keepclassmembers class com.tototools.catalogo.** {
    *** Companion;
}
-keepclasseswithmembers class com.tototools.catalogo.** {
    kotlinx.serialization.KSerializer serializer(...);
}

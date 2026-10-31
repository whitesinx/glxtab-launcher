# kotlinx.serialization: keep generated serializers for config classes
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.afdalah.headunit.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.afdalah.headunit.data.**$$serializer { *; }

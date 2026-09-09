# kotlinx.serialization keeps generated serializers via @Serializable companions.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.personal.sportspk.data.remote.dto.** { *; }

# Room generated implementations.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Glance widget receivers are referenced from the manifest only.
-keep class com.personal.sportspk.widget.** { *; }

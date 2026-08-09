# MapLibre and kotlinx.serialization publish consumer rules. Keep serialized names and app models
# explicit as a defence against future dependency changes.
-keepattributes *Annotation*,InnerClasses,EnclosingMethod
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

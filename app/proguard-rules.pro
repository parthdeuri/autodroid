# Tink (used by androidx.security:security-crypto) keeps protobuf fields via reflection.
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-keep class com.autodroid.** { *; }

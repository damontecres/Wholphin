-keepattributes SourceFile,LineNumberTable

-keep class com.github.damontecres.wholphin.util.mpv.* { *; }

-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
  <methods>;
}
-dontwarn com.google.protobuf.**

# TODO investigate using smaller scope
-keep class com.google.common.cache.** { *; }

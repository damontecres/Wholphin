-keepattributes SourceFile,LineNumberTable

-keep class com.github.damontecres.wholphin.mpv.MPVLib { *; }

-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
  <methods>;
}
-dontwarn com.google.protobuf.**

# TODO investigate using smaller scope
-keep class com.google.common.cache.** { *; }

# Media3SubtitleOverride uses reflection to access/modify these fields
-keep class androidx.media3.ui.SubtitleView {
  private androidx.media3.ui.SubtitleView$Output output;
}
-keep class androidx.media3.ui.CanvasSubtitleOutput {
  private final java.util.List painters;
}
-keep class androidx.media3.ui.SubtitlePainter {
  private final float outlineWidth;
}

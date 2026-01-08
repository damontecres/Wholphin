package android.text;

// Mocks static class for non-instrumented unit tests

public class TextUtils {
  public static boolean isEmpty(CharSequence str) {
    return str == null || str.length() == 0;
  }
}

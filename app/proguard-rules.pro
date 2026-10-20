# Everything else (the service, Compose, Media3) is covered by the rules AGP and the
# libraries ship, so the app only adds one optimization.

# Drop verbose/debug/info logging from release builds. Warnings and errors stay so
# `adb logcat` can still explain playback problems on a user's device.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static boolean isLoggable(java.lang.String, int);
}

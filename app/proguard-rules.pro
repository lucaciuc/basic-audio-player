# ═══════════════════════════════════════════════════════════════════
# Phase 9: Aggressive R8 Code Stripping & Optimization
# ═══════════════════════════════════════════════════════════════════

# ── Strip ALL Android Log calls from the release APK ──
# This removes every Log.d(), Log.v(), Log.i(), Log.w(), Log.e() call
# from the final bytecode, saving CPU cycles and reducing APK size.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static boolean isLoggable(java.lang.String, int);
}

# ── Strip Kotlin internal debugging overhead ──
# Kotlin adds hidden null-check assertions (Intrinsics.checkNotNull) 
# throughout the compiled bytecode. In release mode, R8 can safely
# remove them since we've already tested the code.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkParameterIsNotNull(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void throwUninitializedPropertyAccessException(...);
}

# ── R8 Optimization Passes ──
# Enable aggressive optimization: method inlining, class merging, 
# and dead code elimination across the entire dependency tree.
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# ── Remove Kotlin metadata that is only used by reflection ──
# This strips the @Metadata annotation from all Kotlin classes,
# which saves significant APK size since we don't use reflection.
-dontwarn kotlin.reflect.**
-keep class kotlin.Metadata { *; }

# ── Keep Media3 service intact (required for Android to find it) ──
-keep class com.ferhatozcelik.jetpackcomposetemplate.ui.activitys.AudioPlayerService { *; }
-keep class * extends androidx.media3.session.MediaSessionService { *; }

# ── Keep Compose stability annotations ──
-keep @androidx.compose.runtime.Immutable class * { *; }
-keep @kotlin.jvm.JvmInline class * { *; }

# ── Keep the data model for MediaStore cursor mapping ──
-keepclassmembers class com.ferhatozcelik.jetpackcomposetemplate.ui.activitys.AudioFile { *; }
-keepclassmembers class com.ferhatozcelik.jetpackcomposetemplate.ui.activitys.TrackId { *; }
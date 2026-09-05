# The Android test runner calls this class from the target process. It is otherwise unused by
# Jarboa, so the optimized CI-only app would remove it before instrumentation starts.
-keep class androidx.tracing.Trace { *; }

# Kotlin is already supplied by the target app, so Gradle does not duplicate it into the test APK.
# Preserve the target copy for AndroidX Test, whose Kotlin entry points are not referenced by the
# production application and would otherwise be removed before the runner starts.
-keep class kotlin.** { *; }

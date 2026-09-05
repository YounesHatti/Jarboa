# The Android test runner calls this class from the target process. It is otherwise unused by
# Jarboa, so the optimized CI-only app would remove it before instrumentation starts.
-keep class androidx.tracing.Trace { *; }

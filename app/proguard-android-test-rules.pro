# AndroidX Test marks these build-time annotations as neverlink. They are intentionally absent
# from the instrumented APK and have no behavior to preserve at runtime.
-dontwarn com.google.errorprone.annotations.**
# OkHttp's TLS fixture uses this compile-only API compatibility annotation.
# It is not executable code and is not needed in the instrumentation APK.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

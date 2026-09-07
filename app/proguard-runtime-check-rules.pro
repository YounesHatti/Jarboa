# The Android test runner calls this class from the target process. It is otherwise unused by
# Jarboa, so the optimized CI-only app would remove it before instrumentation starts.
-keep class androidx.tracing.Trace { *; }

# Kotlin is already supplied by the target app, so Gradle does not duplicate it into the test APK.
# Preserve the target copy for AndroidX Test, whose Kotlin entry points are not referenced by the
# production application and would otherwise be removed before the runner starts.
-keep class kotlin.** { *; }

# Instrumentation tests share Jarboa's coroutine dependency with the target process. Keep the
# target copy available to test-only runBlocking/Flow code after the app itself is optimized.
-keep class kotlinx.coroutines.** { *; }

# The migration test opens and queries the production Room database from a separate APK. Preserve
# this complete test-facing boundary so R8 cannot inline Builder methods or remove Kotlin-generated
# database/DAO bridges that are invisible while the target APK is optimized. Release builds do not
# use this file, so production shrinking remains unchanged.
-keep class androidx.room.Room { *; }
-keep class androidx.room.RoomDatabase$Builder { public *; }
-keep class com.youneshatti.jarboa.data.local.** { *; }

# Instrumentation is compiled into a separate APK. Preserve the narrow Jarboa API that the test
# calls across that APK boundary; otherwise R8 may legally change constructor or method signatures
# in the optimized target APK (for example OmemoTrustStore(Context)), leaving the test APK with a
# stale call site. The XMPP/OMEMO implementation and all Smack classes remain fully optimized.
-keep class com.youneshatti.jarboa.data.security.OmemoTrustStore {
    public <init>(android.content.Context);
}

-keep class com.youneshatti.jarboa.data.xmpp.SmackXmppClient {
    public protected *;
}

-keep class com.youneshatti.jarboa.data.xmpp.XmppEvent** {
    public protected *;
}

-keep class com.youneshatti.jarboa.domain.model.** {
    public protected *;
}

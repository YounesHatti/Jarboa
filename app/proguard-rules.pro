# Smack loads providers and Android initialization metadata by class name.
-keep class org.jivesoftware.smack.** { *; }
-keep class org.jxmpp.** { *; }
-keepattributes Signature,*Annotation*

# libsignal's Curve25519 facade constructs its provider by its original class name.
# Without this rule R8 renames or removes the provider in release builds, so OMEMO
# fails while generating the account's first identity key.
-keep class org.whispersystems.curve25519.** { *; }

# Room's generated implementations are referenced by naming convention.
-keep class * extends androidx.room.RoomDatabase

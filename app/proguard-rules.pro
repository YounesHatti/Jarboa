# Smack loads providers and Android initialization metadata by class name.
-keep class org.jivesoftware.smack.** { *; }
-keep class org.jxmpp.** { *; }
-keepattributes Signature,*Annotation*

# Room's generated implementations are referenced by naming convention.
-keep class * extends androidx.room.RoomDatabase


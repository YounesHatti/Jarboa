# Jarboa

Jarboa is a small, privacy-focused XMPP messenger for Android and GrapheneOS. It is built in Kotlin with Jetpack Compose, Room, Android Keystore, and Smack, with no dependency on Google Play services.

> [!WARNING]
> Jarboa is in early development. Version 0.1.0 requires TLS but does **not** yet implement OMEMO end-to-end encryption. The app labels every direct chat as unencrypted. Do not rely on it for sensitive conversations until the encryption milestone is complete and independently reviewed.

## Current milestone: 0.1.0

- XMPP sign-in with an optional host and port override
- Mandatory TLS with platform certificate and hostname validation
- Direct one-to-one messages and delivery receipts
- Stream management and automatic reconnection
- Room-backed local conversations and messages
- Keystore-encrypted account credentials
- Foreground connection service and privacy-preserving notifications
- Monochrome Chats, Contacts, and Settings navigation
- Android 9 (API 28) and later; compile/target SDK 36

Roster contacts, OMEMO, attachments, groups, calls, and multi-account support are not part of this milestone.

## Install and updates

Signed releases are published only on this repository's [GitHub Releases](https://github.com/YounesHatti/Jarboa/releases) page. GrapheneOS users can add `https://github.com/YounesHatti/Jarboa` to [Obtainium](https://obtainium.imranr.dev/) for installation and updates.

Install the versioned `Jarboa-X.Y.Z.apk` release asset. The adjacent `.sha256` file can be used to verify the download. APKs from pull-request CI are debug artifacts and are not a trusted update channel.

## Build

Requirements:

- JDK 17
- Android SDK Platform 36

```shell
./gradlew lintDebug testDebugUnitTest assembleDebug
```

The debug APK is written below `app/build/outputs/apk/debug/`. Release signing is intentionally externalized to environment variables; see [docs/RELEASING.md](docs/RELEASING.md).

## Security and privacy

Read [SECURITY.md](SECURITY.md) before testing with a real account. The implementation avoids cleartext traffic, backups, analytics, advertising SDKs, and Play services. An XMPP server can still observe account metadata and, until OMEMO ships, message contents.

Architecture and trust boundaries are documented in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## License

Jarboa is licensed under `GPL-3.0-or-later`. See [LICENSE](LICENSE).

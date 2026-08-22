# Jarboa

Jarboa is a small, privacy-focused XMPP messenger for Android and GrapheneOS. It is built in Kotlin with Jetpack Compose, Room, Android Keystore, and Smack, with no dependency on Google Play services.

> [!WARNING]
> Jarboa is early test software. End-to-end encryption is temporarily disabled while its OMEMO implementation is redesigned and tested. Current messages are plaintext to the XMPP server and must not be used for sensitive conversations.

## Current milestone: restore reliable messaging

- XMPP sign-in with an optional host and port override
- Mandatory TLS with platform certificate and hostname validation
- Direct one-to-one messages and delivery receipts
- Clearly labeled unencrypted direct messages
- A persistent in-chat warning that the XMPP server can read message contents
- Stream management and automatic reconnection
- Room-backed local conversations and messages
- Keystore-encrypted account credentials
- Foreground connection service and privacy-preserving notifications
- Monochrome Chats, Contacts, and Settings navigation
- Server-synchronized XMPP contacts with safe mutual presence requests
- Android 9 (API 28) and later; compile/target SDK 36

OMEMO, encrypted attachments, groups, calls, multi-account support, and carbon-copy history synchronization are not active in this build.

## Install and updates

Signed releases are published only on this repository's [GitHub Releases](https://github.com/YounesHatti/Jarboa/releases) page. GrapheneOS users can add `https://github.com/YounesHatti/Jarboa` to [Obtainium](https://obtainium.imranr.dev/) for installation and updates.

Install the versioned `Jarboa-X.Y.Z.apk` release asset. The adjacent `.sha256` file can be used to verify the download. APKs from pull-request CI are debug artifacts and are not a trusted update channel.

Beta releases are marked as GitHub prereleases. Obtainium normally follows stable releases unless prereleases are enabled for this source; beta testers can also install the signed APK directly from the matching prerelease.

## Build

Requirements:

- JDK 17
- Android SDK Platform 36

```shell
./gradlew lintDebug testDebugUnitTest assembleDebug
```

The debug APK is written below `app/build/outputs/apk/debug/`. Release signing is intentionally externalized to environment variables; see [docs/RELEASING.md](docs/RELEASING.md).

## Security and privacy

Read [SECURITY.md](SECURITY.md) before testing with a real account. The implementation avoids cleartext network connections, backups, analytics, advertising SDKs, and Play services. TLS protects traffic in transit to the XMPP server, but the server can read current message bodies because end-to-end encryption is disabled.

Architecture and trust boundaries are documented in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## License

Jarboa is licensed under `GPL-3.0-or-later`. See [LICENSE](LICENSE).

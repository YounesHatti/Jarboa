# Jarboa

Jarboa is a small, privacy-focused XMPP messenger for Android and GrapheneOS. It is built in Kotlin with Jetpack Compose, Room, Android Keystore, and Smack, with no dependency on Google Play services.

> [!WARNING]
> Jarboa is in early beta. Version 0.2.0-beta.1 adds OMEMO, but it has not received an independent security audit or broad interoperability testing. Treat the beta as test software rather than a high-risk communications tool.

## Current milestone: 0.2.0 beta

- XMPP sign-in with an optional host and port override
- Mandatory TLS with platform certificate and hostname validation
- Direct one-to-one messages and delivery receipts
- OMEMO-encrypted outgoing direct messages with no plaintext fallback
- Persistent device keys, automatic signed-prekey rotation, and fingerprint verification
- Explicit first-seen, verified, distrusted, and changed-key states
- Visible warnings for incoming plaintext and legacy 0.1.x history
- Stream management and automatic reconnection
- Room-backed local conversations and messages
- Keystore-encrypted account credentials
- Foreground connection service and privacy-preserving notifications
- Monochrome Chats, Contacts, and Settings navigation
- Server-synchronized XMPP contacts with safe mutual presence requests
- Android 9 (API 28) and later; compile/target SDK 36

Encrypted attachments, groups, calls, multi-account support, and carbon-copy history synchronization are not part of this beta.

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

Read [SECURITY.md](SECURITY.md) before testing with a real account. The implementation avoids cleartext traffic, backups, analytics, advertising SDKs, and Play services. OMEMO protects message bodies in supported direct chats, while servers can still observe metadata and endpoints can still expose plaintext.

Architecture and trust boundaries are documented in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## License

Jarboa is licensed under `GPL-3.0-or-later`. See [LICENSE](LICENSE).

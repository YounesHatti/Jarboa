# Changelog

All notable Jarboa changes will be recorded here. The project follows semantic versioning after its first stable release.

## [Unreleased]

### Added

- Synchronize the account's XMPP roster in the Contacts tab.
- Add a contact and request a mutual presence subscription whenever a direct chat is opened.

### Security

- Approve reciprocal presence requests only for contacts the user already added; continue rejecting unknown requests.

### Fixed

- Preserve libsignal's dynamically loaded Curve25519 provider in optimized release builds so OMEMO can generate its local identity.
- Follow Smack's one-store OMEMO lifecycle instead of attempting to replace its cache after sign-out.
- Treat the XEP-required public PEP-node check as an interoperability warning rather than disabling an otherwise initialized OMEMO session.
- Initialize Smack's required Android networking support before the first XMPP connection.
- Keep an authenticated XMPP session connected when OMEMO setup has a recoverable failure, while continuing to block all plaintext sending.
- Retry OMEMO after reconnection or when an encrypted action is requested.
- Replace obfuscated internal exception text in sign-in errors with safe, useful messages.
- Publish OMEMO device-list and bundle nodes with open read access so clients can establish encrypted sessions before a roster subscription exists.
- Use the supplied monochrome jerboa artwork for foreground-service and message notification icons.

## [0.2.0-beta.1] - 2026-08-17

### Added

- OMEMO-encrypted direct-message send and receive using persistent per-device Signal sessions.
- First-seen, verified, distrusted, and changed-key trust states with visible device fingerprints.
- A contact security panel for refreshing devices and making explicit trust decisions.
- Per-message encryption labels and a migration that identifies existing 0.1.x history as legacy plaintext.

### Security

- Never fall back to plaintext for outgoing messages; sending is blocked when OMEMO is unavailable, a key changed, or no recipient device can be encrypted.
- Rotate signed prekeys automatically and exclude OMEMO key material from Android backup.
- Keep incoming plaintext visible for compatibility but mark it clearly as unencrypted.

### Changed

- Require OMEMO initialization before a signed-in session is reported as connected.
- Support signed GitHub prereleases with monotonic Android version codes.

## [0.1.4] - 2026-08-16

### Changed

- Use the exact supplied running-jerboa artwork on pure black for the in-app mark and Android launcher icons.

## [0.1.3] - 2026-08-16

### Changed

- Replace the original chat-bubble launcher mark with a monochrome jerboa silhouette based on the supplied artwork.

### Fixed

- Keep the conversation composer above the Android on-screen keyboard.

## [0.1.2] - 2026-08-16

### Fixed

- Use Android's platform XML Pull Parser instead of packaging Smack's desktop XPP3 jars.
- Exercise the optimized release build in pull-request and main-branch CI.

## [0.1.1] - 2026-08-16

### Fixed

- Resolve the permanent signing keystore from the repository root in release builds.
- Derive a monotonic Android version code from each semantic release tag for Obtainium updates.
- Document the signed GitHub Release APK as the supported installation channel.

## [0.1.0] - development

### Added

- Native Android/GrapheneOS project with no Play services.
- TLS-required XMPP sign-in, direct messages, receipts, stream management, and reconnection.
- Keystore-encrypted credentials and Room-backed local chat history.
- Foreground connection service and private-by-default notifications.
- Monochrome Compose interface with Chats, Contacts, and Settings navigation.
- Explicit unencrypted-chat labeling ahead of the OMEMO milestone.
- CI, signed-release workflow, security policy, architecture, and release documentation.
- Stable Android API 36 production baseline.
- AndroidX Core and Lifecycle pinned to the stable-SDK-compatible release lines.

[Unreleased]: https://github.com/YounesHatti/Jarboa/compare/v0.2.0-beta.1...HEAD
[0.2.0-beta.1]: https://github.com/YounesHatti/Jarboa/compare/v0.1.4...v0.2.0-beta.1
[0.1.4]: https://github.com/YounesHatti/Jarboa/compare/v0.1.3...v0.1.4
[0.1.3]: https://github.com/YounesHatti/Jarboa/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/YounesHatti/Jarboa/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/YounesHatti/Jarboa/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/YounesHatti/Jarboa/releases/tag/v0.1.0

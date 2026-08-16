# Changelog

All notable Jarboa changes will be recorded here. The project follows semantic versioning after its first stable release.

## [Unreleased]

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

[Unreleased]: https://github.com/YounesHatti/Jarboa/compare/v0.1.4...HEAD
[0.1.4]: https://github.com/YounesHatti/Jarboa/compare/v0.1.3...v0.1.4
[0.1.3]: https://github.com/YounesHatti/Jarboa/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/YounesHatti/Jarboa/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/YounesHatti/Jarboa/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/YounesHatti/Jarboa/releases/tag/v0.1.0

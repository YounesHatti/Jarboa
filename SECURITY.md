# Security policy

## Development status

Jarboa 0.2.0-beta.1 is a test release of direct-chat OMEMO. It encrypts outgoing direct messages only after at least one recipient device accepts the ciphertext and never falls back to plaintext. Incoming plaintext remains visible for compatibility and is labeled unencrypted.

The beta uses Smack's `eu.siacs.conversations.axolotl` OMEMO implementation for interoperability with established Android XMPP clients. It has not received an independent audit. An incorrect encrypted label, plaintext fallback, key-change bypass, TLS bypass, credential exposure, or local database exposure should be reported immediately.

## Reporting a vulnerability

Please do not open a public issue containing exploit details, credentials, private server names, or message data. Use GitHub's private vulnerability reporting for this repository:

`https://github.com/YounesHatti/Jarboa/security/advisories/new`

Include the affected version/commit, Android version, device type, reproduction steps, impact, and any proposed remediation. Remove personal data from logs. You should receive an acknowledgement within seven days.

## Security boundaries

Jarboa currently protects:

- the client-to-server connection with required TLS, platform trust anchors, and hostname verification;
- the saved password with a non-exportable Android Keystore AES-GCM key;
- direct-message bodies from the XMPP server when OMEMO is available and the recipient device list is accepted;
- key continuity by blocking changed device fingerprints until an explicit decision;
- notification content by hiding sender and body text by default;
- app data from Android backup and cleartext network traffic.

Jarboa does not protect:

- plaintext messages received from other clients or legacy 0.1.x history;
- a device that is unlocked or controlled by an attacker;
- message metadata such as account addresses, timing, and server IP addresses;
- screenshots, keyboard learning, accessibility services, or notification access granted to other apps.

The Room database and OMEMO key files are app-sandboxed but not separately encrypted. Android/GrapheneOS file-based encryption protects them while the device is locked. Signing out erases message tables, credentials, trust decisions, and local OMEMO keys.

Verify device fingerprints through a separate trusted channel before treating a contact as verified. “Encrypted, unverified” protects against passive server reading but does not rule out a malicious first-key substitution. OMEMO does not hide addresses, timing, server IPs, device compromise, screenshots, keyboards, accessibility services, or notification access.

## Release authenticity

Only APKs attached to this repository's GitHub Releases are official. Each release must include a SHA-256 checksum and use the same long-lived signing identity. Never install a release after an unexpected signing-certificate change.

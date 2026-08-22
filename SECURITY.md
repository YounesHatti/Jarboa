# Security policy

## Development status

Jarboa is early test software. End-to-end encryption is temporarily disabled while its OMEMO implementation is redesigned and tested. Current direct messages are plaintext to the XMPP server and are explicitly labeled unencrypted in the app.

Do not use the current build for sensitive conversations. It has not received an independent security audit. An incorrect security label, TLS bypass, credential exposure, or local database exposure should be reported immediately.

## Reporting a vulnerability

Please do not open a public issue containing exploit details, credentials, private server names, or message data. Use GitHub's private vulnerability reporting for this repository:

`https://github.com/YounesHatti/Jarboa/security/advisories/new`

Include the affected version/commit, Android version, device type, reproduction steps, impact, and any proposed remediation. Remove personal data from logs. You should receive an acknowledgement within seven days.

## Security boundaries

Jarboa currently protects:

- the client-to-server connection with required TLS, platform trust anchors, and hostname verification;
- the saved password with a non-exportable Android Keystore AES-GCM key;
- notification content by hiding sender and body text by default;
- app data from Android backup and cleartext network traffic.

Jarboa does not protect:

- message bodies from the XMPP server or its administrators;
- messages sent by another client using encryption that this build cannot decrypt;
- a device that is unlocked or controlled by an attacker;
- message metadata such as account addresses, timing, and server IP addresses;
- screenshots, keyboard learning, accessibility services, or notification access granted to other apps.

The Room database is app-sandboxed but not separately encrypted. Android/GrapheneOS file-based encryption protects it while the device is locked. Signing out erases message tables, credentials, and retired local OMEMO data.

TLS does not provide end-to-end encryption: the XMPP server terminates that protected connection and can access message contents. It also does not hide addresses, timing, server IPs, device compromise, screenshots, keyboards, accessibility services, or notification access.

## Release authenticity

Only APKs attached to this repository's GitHub Releases are official. Each release must include a SHA-256 checksum and use the same long-lived signing identity. Never install a release after an unexpected signing-certificate change.

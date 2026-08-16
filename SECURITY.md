# Security policy

## Development status

Jarboa 0.1.x is a pre-production transport milestone. It requires authenticated TLS for the client-to-server connection but does not provide end-to-end encryption. Your XMPP service operator can read message bodies in this phase.

Do not report the absence of OMEMO in 0.1.x as a vulnerability; it is a visible, documented limitation. A claim that a conversation is encrypted when it is not, a TLS bypass, credential exposure, or local database exposure should be reported immediately.

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

Jarboa does not currently protect:

- message content from the XMPP server operator;
- a device that is unlocked or controlled by an attacker;
- message metadata such as account addresses, timing, and server IP addresses;
- screenshots, keyboard learning, accessibility services, or notification access granted to other apps.

The Room database is app-sandboxed but not separately encrypted in 0.1.0. Signing out erases its tables and deletes the credential key.

## Release authenticity

Only APKs attached to this repository's GitHub Releases are official. Each release must include a SHA-256 checksum and use the same long-lived signing identity. Never install a release after an unexpected signing-certificate change.

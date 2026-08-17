# Architecture

Jarboa is a single-activity, single-account Android application. Dependencies are assembled in `AppContainer` so security-sensitive ownership remains explicit and testable without a runtime dependency-injection framework.

## Runtime flow

1. `MainActivity` renders the Compose UI and starts the foreground connection service when a saved account exists.
2. `XmppConnectionService` asks the application container to restore the account and maintain the XMPP session.
3. `SecureAccountStore` decrypts the password with a non-exportable Android Keystore AES-GCM key. The password is held in mutable arrays where the surrounding API permits and cleared after use.
4. `SmackXmppClient` requires TLS, validates the certificate hostname, authenticates, initializes a persistent OMEMO device, and only then reports the session as connected.
5. `AppContainer` is the sole event consumer. It writes events through `MessageRepository` to Room and then creates a privacy-filtered notification.
6. `MainViewModel` exposes connection and database flows to Compose. Outgoing messages are written as pending, sent over XMPP, and then advanced to sent, delivered, or failed.

## Contacts

Smack's server-backed roster is the source of truth for the Contacts tab. Opening a direct chat adds the bare JID to that roster and requests a presence subscription. Jarboa uses manual subscription handling: it approves and reciprocates requests only when the sender is already in the user's roster, and rejects unknown subscription requests. This compatibility path helps servers and clients that restrict OMEMO PEP discovery to roster contacts without making plaintext fallback possible.

## Storage

- Account address, host, and port: private SharedPreferences.
- Password: AES-GCM envelope in private SharedPreferences; key material stays in Android Keystore.
- Conversations and messages: Room in the app sandbox.
- Preferences: private SharedPreferences.
- OMEMO identity keys, prekeys, and sessions: Smack's file store under Android's no-backup app directory.
- OMEMO trust decisions: private SharedPreferences, with first-seen, verified, rejected, and changed-key states.

Android backup is disabled. Jarboa does not claim separate app-level encryption at rest for message history or OMEMO files; a user with filesystem access to an unlocked or compromised device may recover them.

## Network policy

The manifest disables cleartext traffic. Smack is configured with required security mode, the platform default hostname verifier, and recommended TLS protocols. A host override changes the socket destination but does not disable certificate validation for the XMPP service identity.

No analytics, crash-reporting, advertising, Google Play services, or proprietary push service is linked.

## OMEMO beta

Jarboa uses Smack's Signal-backed legacy OMEMO namespace for interoperability with Conversations-family clients. Its device-list and per-device bundle PEP nodes are configured with open read access, as required for encrypted sessions to begin before contacts have presence subscriptions. New direct-message bodies are encrypted to the contact's active devices and the sender's other known devices. Sending fails closed when there are no compatible recipient devices, a fingerprint changed, or an accepted device could not be encrypted. Incoming plaintext is stored only with an explicit unencrypted label.

First-seen keys are trusted-on-first-use but remain visibly unverified. A fingerprint change for the same device ID becomes undecided and blocks sending until the user verifies or rejects it. Signed prekeys rotate automatically. Private OMEMO material is excluded from backup and erased on sign-out.

The beta does not yet synchronize carbon-copy history, restore keys on a second installation, decrypt historical MAM results, or claim audit-level assurance. Those are release gates for a stable 0.2.0.

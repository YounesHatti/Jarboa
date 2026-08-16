# Architecture

Jarboa is a single-activity, single-account Android application. Dependencies are assembled in `AppContainer` so security-sensitive ownership remains explicit and testable without a runtime dependency-injection framework.

## Runtime flow

1. `MainActivity` renders the Compose UI and starts the foreground connection service when a saved account exists.
2. `XmppConnectionService` asks the application container to restore the account and maintain the XMPP session.
3. `SecureAccountStore` decrypts the password with a non-exportable Android Keystore AES-GCM key. The password is held in mutable arrays where the surrounding API permits and cleared after use.
4. `SmackXmppClient` requires TLS, validates the certificate hostname, authenticates, enables stream management/reconnection, and emits incoming-message and receipt events.
5. `AppContainer` is the sole event consumer. It writes events through `MessageRepository` to Room and then creates a privacy-filtered notification.
6. `MainViewModel` exposes connection and database flows to Compose. Outgoing messages are written as pending, sent over XMPP, and then advanced to sent, delivered, or failed.

## Storage

- Account address, host, and port: private SharedPreferences.
- Password: AES-GCM envelope in private SharedPreferences; key material stays in Android Keystore.
- Conversations and messages: Room in the app sandbox.
- Preferences: private SharedPreferences.

Android backup is disabled. Version 0.1.0 deliberately does not claim encrypted-at-rest message storage; a user with filesystem access to an unlocked/compromised device may recover the database.

## Network policy

The manifest disables cleartext traffic. Smack is configured with required security mode, the platform default hostname verifier, and recommended TLS protocols. A host override changes the socket destination but does not disable certificate validation for the XMPP service identity.

No analytics, crash-reporting, advertising, Google Play services, or proprietary push service is linked.

## Encryption milestone

OMEMO is separated from 0.1.0 because cryptographic interoperability and dependency licensing require their own gate. Phase 0.2.0 must include device-list management, trust-state UX, key rotation, multi-device tests, backup/restore decisions, and interoperability testing before any encrypted label appears.

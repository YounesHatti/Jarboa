# Architecture

Jarboa is a single-activity, single-account Android application. Dependencies are assembled in `AppContainer` so security-sensitive ownership remains explicit and testable without a runtime dependency-injection framework.

## Runtime flow

1. `MainActivity` renders the Compose UI and starts the foreground connection service when a saved account exists.
2. `XmppConnectionService` asks the application container to restore the account and maintain the XMPP session.
3. `SecureAccountStore` decrypts the password with a non-exportable Android Keystore AES-GCM key. The password is held in mutable arrays where the surrounding API permits and cleared after use.
4. `SmackXmppClient` requires TLS, validates the certificate hostname, authenticates, and then reports the session as connected. The current recovery build does not initialize OMEMO.
5. `AppContainer` is the sole event consumer. It writes events through `MessageRepository` to Room and then creates a privacy-filtered notification.
6. `MainViewModel` exposes connection and database flows to Compose. Outgoing messages are written as pending, sent over XMPP, and then advanced to sent, delivered, or failed.

## Contacts

Smack's server-backed roster is the source of truth for the Contacts tab. Opening a direct chat adds the bare JID to that roster and requests a presence subscription. Jarboa uses manual subscription handling: it approves and reciprocates requests only when the sender is already in the user's roster, and rejects unknown subscription requests.

## Storage

- Account address, host, and port: private SharedPreferences.
- Password: AES-GCM envelope in private SharedPreferences; key material stays in Android Keystore.
- Conversations and messages: Room in the app sandbox.
- Preferences: private SharedPreferences.
- Retired OMEMO key and trust data: retained only for cleanup and erased on sign-out; it is not used by the current runtime.

Android backup is disabled. Jarboa does not claim separate app-level encryption at rest for message history; a user with filesystem access to an unlocked or compromised device may recover it.

## Network policy

The manifest disables cleartext traffic. Smack is configured with required security mode, the platform default hostname verifier, and recommended TLS protocols. A host override changes the socket destination but does not disable certificate validation for the XMPP service identity.

No analytics, crash-reporting, advertising, Google Play services, or proprietary push service is linked.

## Message security

The current recovery build sends and receives plaintext message bodies over the mandatory TLS connection. Every new message is labeled unencrypted and every conversation shows a persistent warning that the XMPP server can read its contents. Incoming OMEMO stanzas are not treated as readable messages because this build cannot authenticate or decrypt them.

OMEMO remains a future milestone. It will return only after the implementation has interoperable device publication, persistent keys, explicit trust handling, reliable recovery, and tests against established clients without affecting basic XMPP login or plaintext operation.

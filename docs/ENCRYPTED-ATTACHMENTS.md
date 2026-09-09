# Encrypted attachments

Jarboa uses XEP-0363 HTTP upload and the Conversations-compatible AESGCM URI format described in
https://xmpp.org/extensions/inbox/omemo-media-sharing.html (a historical proposal, not a final XEP).
https://xmpp.org/extensions/xep-0363.html defines upload slot discovery and HTTP headers.

Each attempt generates a fresh AES-256 key and 96-bit IV using SecureRandom. AES-GCM appends a
128-bit authentication tag. Only ciphertext is uploaded. The HTTPS download URL and IV/key are
sent through the existing OMEMO message path, which rechecks recipient trust before sending.
No plaintext fallback or unencrypted out-of-band URL is added. Retries of an already queued
message reuse that message and uploaded object; retries of failed uploads use a new slot and key.

The upload service sees the account requesting the slot, a random filename with its extension,
the encrypted size and network metadata. Original filenames are not uploaded. Photos selected
through Photo are oriented, resized to at most 2048 pixels per side, and re-encoded as JPEG with
metadata removed. File sends preserve all bytes, including any metadata inside the file.

Downloads require an explicit tap and an OMEMO message without a changed-key warning. HTTPS
certificate/hostname checks stay enabled. A dedicated OkHttp client does not follow redirects or
use ambient cookies/credentials; DNS rejects private and local addresses to prevent attachment
links accessing services on the recipient's network. Only allowed upload-slot headers are used,
and never on downloads. The key fragment is never sent over HTTP or opened in another app.

Files are bounded to 25 MiB (or the server's smaller upload limit). Downloaded plaintext stays in a
private temporary file until AES-GCM authentication completes; failed verification removes it.
Verified files are stored in app-private, non-backed-up storage, with a 100 MiB cache budget.
Older cached files may need downloading again; server expiration can make them unavailable.
Save a copy uses Android's document picker. No automatic gallery writes or broad storage grants.
Sign-out cancels transfers and erases private attachment storage. Explicitly exported copies
remain in the user-selected destination. If Android terminates the app during a transfer, the
incomplete transfer is discarded on startup; an unsent attachment must be selected again.

Validation includes an AES-GCM known-answer vector, malformed-URI and address-policy tests,
HTTPS upload/download and tamper rejection in the Android runtime variant, real Smack upload
slot discovery, and an encrypted media reference passing through the isolated XMPP server.
Conversations interoperability follows its documented wire format; device-to-device testing
with the user's installed Conversations version remains the final compatibility check.

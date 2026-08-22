# Releasing Jarboa

Jarboa uses a permanent Android signing identity. Losing it prevents seamless updates; exposing it allows malicious releases. The keystore must never be committed.

## One-time setup

Create the signing key on a trusted offline machine and keep at least two encrypted backups in separate physical locations. Record the certificate SHA-256 fingerprint. Add these GitHub Actions secrets:

- `ANDROID_SIGNING_KEY_BASE64`: base64-encoded keystore file
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

Repository releases also require the protected `release` environment. Limit approvals and secret access to the repository owner.

## Release checklist

1. Review the milestone, dependency licenses, `SECURITY.md`, and user-visible encryption claims.
2. Update the changelog. The release workflow derives `VERSION_NAME` and a monotonic `VERSION_CODE` from the semantic version tag.
3. Run `./gradlew clean lintDebug testDebugUnitTest assembleDebug` on a clean checkout.
4. Test sign-in failure, invalid TLS, offline recovery, send/receive, receipt state, process recreation, notification privacy, and sign-out erasure on GrapheneOS.
5. Merge only a green reviewed pull request to `main`.
6. Create and push an annotated `vX.Y.Z` tag from that exact commit. The release workflow builds the signed APK and checksum from the tag.
7. Compare the release certificate fingerprint with the recorded permanent identity and independently verify the SHA-256 checksum.
8. Install as an update over the previous release on a test device before marking the GitHub release stable.

Local signed builds use the same environment variable names plus `SIGNING_STORE_FILE`, which points to the keystore path. Do not place credentials in Gradle properties or shell history.

## Obtainium

Obtainium tracks the repository's GitHub Releases. Keep version tags monotonic, attach exactly one universal APK, and never replace an APK under an existing tag.

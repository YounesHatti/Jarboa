# Contributing

Jarboa accepts focused changes that preserve its privacy and GrapheneOS goals. Open an issue before large protocol or architecture changes.

Before submitting a pull request:

- keep changes free of Play services, trackers, ads, and undisclosed network endpoints;
- do not weaken TLS, backup, notification privacy, or encryption-status labeling;
- add tests for protocol state, persistence, and security-sensitive changes;
- run `./gradlew lintDebug testDebugUnitTest assembleDebug`;
- explain user-visible behavior and security tradeoffs in the pull request.

Never include real JIDs, passwords, server logs, keystores, signing fingerprints tied to a private identity, or message content in issues and fixtures.

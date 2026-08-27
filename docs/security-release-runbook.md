# Security and Release Runbook

This runbook defines the controls that must be completed before Watermelon MediaPlayer is distributed. It supplements automated checks; it does not permit bypassing failed security or dependency checks.

## 1. Repository security baseline

Repository administrators must keep Dependabot alerts, Dependabot security updates, secret scanning, and secret-scanning push protection enabled. CodeQL findings must be triaged promptly. Critical and High findings block a release unless the release owner records a time-bound exception with a compensating control and a remediation date.

Dependabot pull requests and CodeQL alerts require human review. Automated security tooling is a detection control, not proof that a dependency is safe or that an alert is exploitable.

## 2. Gradle dependency integrity

All dependencies are version-pinned in `gradle/libs.versions.toml`, including the JitPack-provided `java-lame` encoder. The `Dependency Integrity` workflow resolves dependencies from a clean cache and compiles `media-tools`; it must be green for every dependency/build-configuration change and the release candidate.

Before the first distributable release, generate and commit Gradle verification metadata from a trusted clean runner:

```sh
./gradlew --no-daemon --refresh-dependencies \
  --write-verification-metadata sha256,pgp :app:assembleRelease
```

Review every added key, checksum, component, and repository origin before committing `gradle/verification-metadata.xml`. Do not accept a regenerated metadata file mechanically. Once the verified metadata is committed, all CI and release commands must use Gradle's strict dependency-verification mode; do not use `--dependency-verification=off` to make a build pass.

The JitPack encoder dependency requires a release-specific provenance record containing its exact coordinate, licence, upstream repository/release reference, verification metadata, and a named maintainer. An approved fallback artifact, if one is retained, must have the same record and must be built and tested in CI. It must not be copied into the repository without review and checksum verification.

## 3. CI workflow integrity

Workflow actions are pinned to reviewed commit SHAs. Update a pin only through a reviewed pull request that records the source release/tag and the replacement commit. Workflows use least-privilege token permissions and pull-request workflows must not receive release-signing credentials.

The `CodeQL` workflow performs Kotlin/Java analysis on pushes, pull requests, scheduled runs, and manual dispatch. Its findings must be triaged before release. The `Dependency Integrity` workflow deliberately removes Gradle dependency caches before compiling `media-tools` to surface fresh-resolution failures, including JitPack availability or repository changes.

## 4. Release signing

Never commit a keystore, signing password, alias, or `RELEASE_*` property file. The release signing configuration is intentionally property-gated: it produces an unsigned release artifact unless all four required properties are securely supplied.

A release owner must create a protected release environment with approval gates and provide only these values to the protected, tag/release-only signing workflow:

| Required value | Handling requirement |
|---|---|
| `RELEASE_STORE_FILE` | Secure, ephemeral path to the decoded keystore; remove it after the job. |
| `RELEASE_STORE_PASSWORD` | Protected environment secret; never print, log, or pass through an unmasked channel. |
| `RELEASE_KEY_ALIAS` | Protected environment variable/secret. |
| `RELEASE_KEY_PASSWORD` | Protected environment secret; never print, log, or pass through an unmasked channel. |

Before publishing, verify the release artifact's application ID, version, signing certificate fingerprint, and provenance against the approved release record. Record the artifact SHA-256 and the CI run URL in the release decision. A debug-signed or unsigned release APK is never distributable.

## 5. MP3 runtime and privacy gates

Compilation is necessary but does not prove audio extraction is correct. Exercise extraction on physical devices with representative input containers, long files, corrupt files, storage exhaustion, cancellation, process restart, and background execution. Validate successful outputs independently for decodability, duration, and playback. Every advertised feature needs a result record and an owner for any defect.

Before asserting "nothing else is sent" in product copy, maintain a network-flow inventory for subtitle lookup. It must identify every outbound host, request field, why it is required, the provider policy, and offline/failure behaviour. Privacy text must describe observed behaviour, not an unverified intention.

## 6. Release decision checklist

A named release approver may issue a Go decision only when all of the following are linked in the release record:

1. Required build, unit/instrumented, CodeQL, and Dependency Integrity workflows are green.
2. All Critical/High security findings are remediated or have an approved, time-bound exception.
3. Gradle verification metadata and the JitPack provenance record have been reviewed.
4. The signing workflow produced the intended signed distribution artifact and its certificate/artifact hashes were verified.
5. MP3 runtime evidence and the planned device/accessibility/findability validation are complete.
6. Known limitations, residual risks, and the release decision owner are recorded.

Any failed or missing item is a Hold decision.

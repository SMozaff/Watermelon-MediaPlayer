# Release Evidence Index

This directory contains the evidence needed for a controlled Watermelon MediaPlayer release. It does not authorize publication by itself.

## Required release order

| Order | Action | Evidence / owner |
|---:|---|---|
| 1 | Merge and review only the intended dependency/security updates | Reviewed pull requests and green checks |
| 2 | Merge the release-preparation controls | Pull request with green Android, dependency, and CodeQL checks |
| 3 | Complete physical-device product validation | [MP3 device record](mp3-device-validation.md), Android TV matrix, accessibility, and media-job evidence |
| 4 | Complete data-flow and supply-chain reviews | [Subtitle network inventory](subtitle-network-inventory.md) and [java-lame provenance](java-lame-provenance.md) |
| 5 | Generate and review strict Gradle verification metadata on a clean trusted runner | Reviewed `gradle/verification-metadata.xml` commit |
| 6 | Create an immutable `v*` candidate tag only after the preceding gates pass | Tag name and commit recorded in [candidate record](release-candidate-record.md) |
| 7 | Run the unsigned Release Candidate workflow | Optimized unsigned APK and SHA-256 evidence |
| 8 | Configure the protected `release` environment and manually run signed candidate creation from the tag | Signed APK, certificate output, SHA-256, and approval audit trail |
| 9 | Record Go/Hold, then publish deliberately | Completed [candidate record](release-candidate-record.md) and named release owner |

## Current automated scope

The `Release Candidate` workflow deliberately does **not** publish a GitHub release or upload an artifact to an app store. On a `v*` tag it produces a short-retention unsigned candidate for R8 and packaging verification. A release owner can manually request signed-candidate creation only from a tag and only after approval of the protected `release` environment.

## Current manual scope

The release owner must assign ownership and attach evidence for all Pending fields. In particular, this repository has not yet recorded physical-device MP3 evidence, an observed subtitle network capture, a completed java-lame provenance/compliance review, or reviewed Gradle verification metadata. These are release gates, not backlog suggestions.

# java-lame Provenance Record

**Status:** SHA-256 metadata generated on the release-preparation branch; independent release-owner approval, SBOM review, and licence review remain pending. This record is evidence for the release decision; it is not a legal opinion or a substitute for licence review.

| Field | Recorded value |
|---|---|
| Application dependency coordinate | `com.github.nwaldispuehl:java-lame:v3.98.4` |
| Resolution repository | JitPack, declared in `settings.gradle.kts` |
| Upstream project | [`nwaldispuehl/java-lame`](https://github.com/nwaldispuehl/java-lame) |
| Upstream release/tag | [`v3.98.4`](https://github.com/nwaldispuehl/java-lame/releases/tag/v3.98.4) |
| Release date stated upstream | 21 August 2016 |
| Upstream description | Native Java port of LAME 3.98.4 |
| Licence stated by upstream | GNU Lesser General Public License, version 3.0 |
| Application use | MP3 extraction encoder in the `media-tools` module |
| Required release owner | **Unassigned — assign before a Go decision** |

## Evidence and release-owner actions

The upstream repository states that its Java port is licensed under LGPL and the tagged release supplies a prebuilt JAR.[1] [2] The application’s catalog pins the exact `v3.98.4` coordinate; it must not be changed in a release branch without a new provenance review.

Before distribution, the assigned release owner must review the upstream licence text and the application’s intended distribution model with the organisation’s appropriate counsel or compliance process. Record the outcome, notices required, source/offering obligations if any, and reviewer below. Do **not** infer that the upstream label alone resolves the application's distribution obligations.

| Evidence item | Release owner record |
|---|---|
| Reviewed upstream tag/repository | URL and immutable commit: ____________________ |
| Reviewed licence text and distribution obligations | Reviewer/date/outcome: ____________________ |
| Generated Gradle `verification-metadata.xml` | `gradle/verification-metadata.xml` was generated with Gradle SHA-256 metadata. Review the 642 components/1,166 artifacts before approval: ____________________ |
| JitPack artifact SHA-256 matches verification metadata | `java-lame-v3.98.4.jar`: `20b28d982b57fcf44aed9c123c14de10df3860ba3dcb0fe3ada89a1b7cf438ef`; reviewer: ____________________ |
| Clean-cache Dependency Integrity workflow passed on release candidate | Workflow URL, including MP3 test and SBOM artifact: ____________________ |
| Approved fallback artifact, if retained | Coordinate/location/checksum: ____________________ |
| MP3 physical-device validation completed | Result record URL: ____________________ |

## Integrity procedure

Generate verification metadata only from a clean, trusted build runner, inspect every new key and checksum, and commit it through a reviewed pull request:

```sh
./gradlew --no-daemon --refresh-dependencies \
  --write-verification-metadata sha256,pgp :app:assembleRelease
```

After review, release builds must use Gradle's strict dependency-verification mode. The dependency, CodeQL, and release-candidate workflows invoke Gradle with `--dependency-verification=strict`. A clean-cache build that cannot resolve JitPack, or a checksum/key mismatch, is a **Hold** condition. Never suppress dependency verification to make a candidate build pass.

## References

[1] [nwaldispuehl/java-lame — README and licence statement](https://github.com/nwaldispuehl/java-lame)

[2] [Java Lame 3.98.4 release](https://github.com/nwaldispuehl/java-lame/releases/tag/v3.98.4)

# Watermelon MediaPlayer

<p align="center">
  <strong>A production-oriented Android media player built around a modern, extensible playback architecture.</strong><br/>
  <sub>Technical foundation for a commercial-grade media experience.</sub>
</p>

<p align="center">
  <a href="https://github.com/SMozaff/Watermelon-MediaPlayer/actions/workflows/CI.yaml">
    <img src="https://github.com/SMozaff/Watermelon-MediaPlayer/actions/workflows/CI.yaml/badge.svg" alt="CI">
  </a>
  <img src="https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Android-API%2037-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Media3-1.11.0-4285F4?logo=google&logoColor=white" alt="Media3">
  <img src="https://img.shields.io/badge/Gradle-9.7.1-02303A?logo=gradle&logoColor=white" alt="Gradle">
</p>

## Overview

**Watermelon MediaPlayer** is an Android media-player project focused on turning a capable playback engine into a product-ready media experience.

The project is being developed with two perspectives in parallel, under a single product constraint: **Watermelon is intended to be a commercial, proprietary application with paid premium features.**

- **Commercial:** a clear product proposition, differentiated user experience, extensible features, and a foundation that can evolve into a sustainable media application.
- **Technical:** modular Android architecture, Media3-based playback, automated validation, database migrations, runtime instrumentation, and CI gates intended to keep the codebase maintainable as the product grows.

The goal is not simply to ship another media player. The codebase is being structured so that product decisions, technical decisions, validation, and implementation can evolve together.

## Commercial Perspective

Watermelon is positioned as a **smart media-player platform**, rather than only a playback utility.

### Product proposition

- **Unified media experience** — one application for everyday local media playback and advanced playback workflows.
- **Intelligent playback features** — features such as subtitle synchronization and media-aware automation can reduce manual user intervention.
- **Phone and TV readiness** — interaction and timing controls are designed with both mobile and larger-screen experiences in mind.
- **Extensible product surface** — the architecture leaves room for premium capabilities, integrations, and future product modules without requiring a rewrite of the playback core.
- **Trust through engineering** — reproducible builds, automated tests, migrations, and runtime validation are treated as product-quality concerns, not only developer concerns.

### Potential commercial model

Watermelon is intended for **commercial distribution**, with premium capabilities forming part of the product strategy. The repository is therefore not positioned as a free/open-source media-player product.

The commercial product direction includes:

- a proprietary application and controlled distribution;
- a capable core media experience with differentiated premium capabilities;
- premium automation and intelligent media features;
- TV-oriented and specialized product experiences;
- future integrations and services where they support the product strategy.

The exact feature matrix, pricing, packaging, and entitlement model will evolve as the product moves toward release.

## Technical Perspective

Watermelon is built as a modern Android/Kotlin project with a strong emphasis on separation of concerns and verifiable changes.

### Current technical foundation

| Area | Foundation |
| --- | --- |
| Language | Kotlin |
| Android build | Android Gradle Plugin |
| Build system | Gradle |
| Playback | AndroidX Media3 |
| UI | Jetpack Compose |
| Persistence | SQLite-backed application data with versioned migrations |
| Runtime validation | Android instrumented tests / emulator |
| CI | GitHub Actions |
| Java | Java 17 |

The project currently targets **compileSdk 37**, uses **Kotlin 2.4.10**, **AGP 9.4.0**, **Gradle 9.7.1**, and **Media3 1.11.0**.

### Engineering priorities

- modular boundaries between playback, presentation, persistence, and feature logic;
- explicit contracts and data models for cross-feature behavior;
- database migrations that preserve existing application data;
- deterministic unit and algorithm-level validation where possible;
- Android runtime regression tests for behavior that cannot be validated on the JVM alone;
- CI gates covering compilation, tests, migration paths, and runtime behavior;
- incremental changes that keep product evolution traceable.

## BOUND Method

Watermelon development is also documented and organized with the **BOUND Method** as a reference point for keeping product intent, constraints, evidence, and implementation decisions connected.

**Reference:** [BOUND Method](https://bound-method.github.io/)

The relationship is practical:

1. **Define the product intent** — what user or business problem a change is meant to solve.
2. **Define the technical boundary** — architecture, compatibility, performance, platform, and data constraints.
3. **Record evidence and decisions** — make important assumptions and implementation choices reviewable.
4. **Implement against the defined boundary** — keep code changes tied to the intended outcome.
5. **Validate the result** — use tests, CI, migrations, and runtime checks to verify that the implementation satisfies the intended behavior.
6. **Feed the result back into the product** — use validation outcomes and newly discovered constraints to refine subsequent work.

This makes the README's commercial and technical views complementary: the **commercial view describes why the product exists and where it can go; the technical view describes how the repository is engineered to get there; BOUND provides the process reference connecting the two.**

## Key Capabilities

The repository currently contains or is being developed around capabilities including:

- Media3-based playback;
- subtitle activity fingerprinting;
- subtitle synchronization and offset correlation;
- synchronization consensus and conflict rejection;
- subtitle fingerprinting;
- persistent manual/automatic synchronization authority;
- SQLite database migration support;
- sparse MediaExtractor / MediaCodec audio probing;
- lightweight speech-likelihood estimation;
- phone and TV timing controls;
- Auto Sync configuration;
- Android unit and instrumentation validation.

## Quality & CI

Continuous integration is part of the product engineering workflow. The CI pipeline is intended to catch problems at several levels:

- source compilation;
- unit tests;
- migration validation;
- dependency/integrity checks;
- Android emulator runtime tests;
- player regression coverage;
- benchmark compilation.

The project deliberately keeps runtime regression testing in the validation path rather than treating a successful JVM build as sufficient evidence of Android correctness.

## Development

Clone the repository:

~~~bash
git clone https://github.com/SMozaff/Watermelon-MediaPlayer.git
cd Watermelon-MediaPlayer
~~~

Build the project:

~~~bash
./gradlew assembleDebug
~~~

Run unit tests:

~~~bash
./gradlew test
~~~

For Android runtime tests, use a compatible Android emulator/device and the Gradle instrumentation-test tasks defined by the project.

## Project Status

Watermelon MediaPlayer is an **actively developed project**. The repository is being hardened from feature implementation toward a more repeatable, product-oriented engineering workflow.

Features and APIs should therefore be considered subject to change until their corresponding product and technical contracts are stabilized.

## Author

**Soheil Mozaffari**

- Email: [soheil.mozaffari@gmail.com](mailto:soheil.mozaffari@gmail.com)
- GitHub: [SMozaff](https://github.com/SMozaff)
- ORCID: [0009-0001-2428-1295](https://orcid.org/0009-0001-2428-1295)
- LinkedIn: [sohmozaffari](https://www.linkedin.com/in/sohmozaffari/)
- BOUND Method: [bound-method.github.io](https://bound-method.github.io/)

## License

**Commercial / proprietary.** Watermelon is not intended to be released as a free open-source application. The final distribution license, end-user terms, premium entitlement model, and any source-availability policy will be established as part of the commercial release process.

## Links

- [Repository](https://github.com/SMozaff/Watermelon-MediaPlayer)
- [BOUND Method](https://bound-method.github.io/)
- [Project homepage](https://smozaff.github.io/)

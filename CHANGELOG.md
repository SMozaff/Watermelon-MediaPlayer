# Changelog

All notable user-facing changes are recorded in this file. Dates, distribution channel, and final release notes remain pending until the release owner completes the candidate record.

## [Unreleased]

### Release preparation

- Added a protected, tag-aware release-candidate workflow that separates unsigned R8 validation from signed artifact creation.
- Added reproducible release, MP3 device-validation, subtitle network-flow, and java-lame provenance records.
- Added source-control exclusion rules for Android build products and signing material.

## [1.0.0] — Pending release owner approval

### Highlights

- Local video library, folders, playlists, and continuation of playback.
- Subtitle discovery, local sidecar/cached subtitle support, and configurable subtitle presentation.
- Media utilities for MP3 extraction, compression, trimming, and screenshots.
- A remote-first Android TV experience with D-pad browsing, playback controls, playlist management, settings, and folder visibility.

### Security and quality controls

- Dependency integrity, CodeQL, Dependabot, secret-scanning, and pull-request build validation.
- Release signing is deliberately property-gated and must use the protected release environment.

[Unreleased]: https://github.com/SMozaff/Watermelon-MediaPlayer/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/SMozaff/Watermelon-MediaPlayer/releases/tag/v1.0.0

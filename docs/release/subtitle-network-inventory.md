# Subtitle Network-Flow Inventory

**Status:** Code-inspection baseline. Complete the runtime capture rows before using product copy that makes absolute network or privacy claims.

## Inspection scope

This inventory is based on `SubtitleRepositoryImpl.kt`, `SubtitleProviderRegistry.kt`, and `OpenSubtitlesProvider.kt` (modern OpenSubtitles.com provider/registry architecture — there are no legacy mirrors: the former `SubtitleApiClient`/`MirrorRotator` and the `rest.opensubtitles.org` / `www.opensubtitles.org` endpoints were removed). It covers the subtitle lookup and download paths; it does not replace an on-device traffic capture or a provider-policy review.

| Flow | Trigger in code | Observed destination | Observed data | Local/offline behaviour |
|---|---|---|---|---|
| Sidecar subtitle lookup | Player asks for a parsed subtitle | None | Video display name, parent folder, size, duration, and language preferences remain local for sidecar matching | Local sidecar is checked first |
| Cached subtitle lookup | Player asks for a parsed subtitle (`findSubtitles` is cache-only) | None | Cache key derived from local media URI; cached file contents remain in app cache | Cached subtitle is checked before any network path; opening a video never triggers network access |
| Subtitle search | User taps "Find online subtitles" → `searchOnlineSubtitles` → registry → `OpenSubtitlesProvider.search` | `https://api.opensubtitles.com` only (GET `/api/v1/subtitles`) | `moviehash`, `moviebytesize`, selected languages, `Api-Key`, and `User-Agent` headers | No configured providers → `ProviderNotConfigured`; offline → `Offline`; 401/403/429 typed; 5xx → provider failure; malformed JSON → typed failure. No mirrors, no failover — fails closed |
| Subtitle download | User selects a returned track → `downloadSubtitle` → `resolveDownload` (POST `/api/v1/download`) then GET | Temporary download URL, restricted to HTTPS `opensubtitles.com` hosts (resolved URL and final redirect URL both validated) | Selected provider-supplied download URL; response body is validated as non-empty SRT *before* being committed to app cache | Rejects non-HTTPS, missing-host, and unapproved-host URLs; rejects oversized (> 5 MiB) payloads; cue-less downloads are never cached |

## Runtime verification record

Perform the following on a physical device and attach a redacted capture or proxy report. Do not record media contents, authentication data, or user-identifying network data in the release record.

| Check | Required result | Evidence | Status |
|---|---|---|---|
| Local sidecar and cache paths | No outbound connection when a matching local/cached subtitle exists | `________________` | Pending |
| Search request | Only the documented host and documented query fields are observed | `________________` | Pending |
| No failover | A simulated timeout/5xx fails closed with no secondary host contacted | `________________` | Pending |
| Download URL allow-list | An unapproved or HTTP URL is rejected without an outbound request | `________________` | Pending |
| Offline mode | No network request; UI gives a clear recoverable outcome | `________________` | Pending |
| Provider policy | Current provider terms and privacy policy reviewed by release owner | `________________` | Pending |

## Product-copy boundary

Until all runtime checks above are complete, use limited, evidence-based wording such as: **“Subtitle lookup may contact the configured OpenSubtitles providers when you request online subtitles.”** Do not state or imply that the application sends no other data, that a provider retains no data, or that a request is anonymous unless those claims are independently substantiated.

## Release owner sign-off

| Reviewer | Date | Runtime evidence URL | Disclosure text reviewed | Result |
|---|---|---|---|---|
| `________________` | `________________` | `________________` | `________________` | ☐ Pass / ☐ Hold |

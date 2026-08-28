# Subtitle Network-Flow Inventory

**Status:** Code-inspection baseline. Complete the runtime capture rows before using product copy that makes absolute network or privacy claims.

## Inspection scope

This inventory is based on `SubtitleRepositoryImpl.kt`, `SubtitleApiClient.kt`, and `MirrorRotator.kt` in the release candidate. It covers the subtitle lookup and download paths; it does not replace an on-device traffic capture or a provider-policy review.

| Flow | Trigger in code | Observed destination | Observed data | Local/offline behaviour |
|---|---|---|---|---|
| Sidecar subtitle lookup | Player asks for a parsed subtitle | None | Video display name, parent folder, size, duration, and language preferences remain local for sidecar matching | Local sidecar is checked first |
| Cached subtitle lookup | Player asks for a parsed subtitle | None | Cache key derived from local media URI; cached file contents remain in app cache | Cached subtitle is checked before remote lookup |
| Subtitle search | `findSubtitles` reaches remote lookup | `https://api.opensubtitles.com`, then `https://rest.opensubtitles.org`, then `https://www.opensubtitles.org` on timeout/5xx | `moviehash`, `moviebytesize`, selected languages, and `User-Agent: Watermelon/1.0` | On hash/read failure, 4xx, timeout after all mirrors, or malformed response, return an empty result |
| Subtitle download | User selects a returned subtitle track | HTTPS URLs restricted to `opensubtitles.com`, `api.opensubtitles.com`, `opensubtitles.org`, `rest.opensubtitles.org`, `www.opensubtitles.org`, including their subdomains | Selected provider-supplied download URL; response body is written to app subtitle cache | Rejects malformed, non-HTTPS, and unapproved-host URLs before download |

## Runtime verification record

Perform the following on a physical device and attach a redacted capture or proxy report. Do not record media contents, authentication data, or user-identifying network data in the release record.

| Check | Required result | Evidence | Status |
|---|---|---|---|
| Local sidecar and cache paths | No outbound connection when a matching local/cached subtitle exists | `________________` | Pending |
| Search request | Only the documented host and documented query fields are observed | `________________` | Pending |
| Failover | A simulated timeout/5xx moves only to a listed HTTPS mirror and then fails closed | `________________` | Pending |
| Download URL allow-list | An unapproved or HTTP URL is rejected without an outbound request | `________________` | Pending |
| Offline mode | No network request; UI gives a clear recoverable outcome | `________________` | Pending |
| Provider policy | Current provider terms and privacy policy reviewed by release owner | `________________` | Pending |

## Product-copy boundary

Until all runtime checks above are complete, use limited, evidence-based wording such as: **“Subtitle lookup may contact the configured OpenSubtitles providers when you request online subtitles.”** Do not state or imply that the application sends no other data, that a provider retains no data, or that a request is anonymous unless those claims are independently substantiated.

## Release owner sign-off

| Reviewer | Date | Runtime evidence URL | Disclosure text reviewed | Result |
|---|---|---|---|---|
| `________________` | `________________` | `________________` | `________________` | ☐ Pass / ☐ Hold |

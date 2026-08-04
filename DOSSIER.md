# Premium Media Tools — Dossier of Changes

Everything below reflects what was actually built/edited in this session, checked against
`git diff` rather than assumed. One clarification up front: **only four pre-existing files
were substantively edited** (listed in §2). A much larger set of files show up in a raw
`git diff` with hundreds of changed lines each (`MainActivity.kt`, drawable XMLs,
`MiniPlayerBar.kt`, etc.) — those are line-ending/whitespace artifacts from the zip
extract/repack round-trip in this sandbox, not real edits. Don't mistake that diff noise for
intentional changes; nothing in this dossier includes those files.

---

## 1. New module: `media-tools`

A new Gradle module, `com.watermelon.mediatools`, added alongside `playback-engine`,
following its exact build pattern (namespace, minSdk 23, Java 17, `@UnstableApi` opt-in).
Registered in `settings.gradle.kts`.

### 1.1 Job system (`job/`)

**`MediaJob.kt`** — the data model. A `MediaJob` has an id, type (`EXTRACT_AUDIO`, `TRIM`,
`COMPRESS`), input/output paths, and a sealed `MediaJobState` (`Queued`, `Running`,
`Completed`, `Failed`, `Cancelled`). `Completed` carries an `awaitingOriginalFileDecision`
flag — true only for `TRIM`/`COMPRESS`, since only those replace an existing library video
and need the keep-or-delete-original prompt.

**`MediaJobManager.kt`** — the single owner of job state, exposed as a `StateFlow<List<MediaJob>>`.
Modeled on `PlaybackControllerImpl`'s `StateFlow` pattern (not `PlaybackConnection` — an
early blueprint claim that turned out to be wrong; see §5). Two ways to start a job:

- `register(...)` — for Transformer-backed engines (`VideoTrimmer`, `VideoCompressor`). The
  engine builds its own `Transformer` + listener, registers it here, and this class polls
  `Transformer.getProgress()` on a 250ms timer (Transformer has no push-based progress API).
- `registerCoroutineJob(...)` — for `AudioExtractor`, which doesn't use Transformer at all
  (see §1.2). Runs the caller's suspend block on `Dispatchers.IO`, reports progress via
  callback, and real coroutine cancellation actually interrupts the work.

Also handles: publishing finished output to `MediaStore` via `OutputFileStore` on success,
cleaning up staged files on failure/cancel, and logging `onFallbackApplied` (Transformer
falling back to re-encoding) without failing the job, per your instruction.

### 1.2 Engines (`engine/`)

**`AudioExtractor.kt` + `Mp3Encoder.kt` + native JNI (`cpp/`)** — real MP3 output, not
AAC/.m4a. This was the biggest structural decision of the session:

- The blueprint's original Phase 0 had two MP3 options: (A) ship AAC/.m4a and call it
  "Extract Audio," or (B) add a dedicated MP3 encoder. **You chose Option B — MP3 only.**
- Media3 Transformer's `Muxer` SPI looked like a possible way to route raw PCM into a custom
  MP3 encoder, but whether `setAudioMimeType(MimeTypes.AUDIO_RAW)` actually makes Transformer
  hand out raw PCM (vs. rejecting it) couldn't be confirmed via docs — rather than guess, we
  switched to a different, fully-verified approach: `AudioExtractor` bypasses Transformer
  entirely, using plain `MediaExtractor` + `MediaCodec` (stable, decades-old Android SDK) to
  decode to PCM, then hands that PCM to a JNI wrapper around `libmp3lame`.
- **What's real vs. scaffolded:** the JNI glue (`mp3_encoder_jni.cpp`) and Kotlin wrapper
  (`Mp3Encoder.kt`) use the long-stable classic liblame API — but `libmp3lame`'s actual source
  isn't vendored (no network access in this sandbox to fetch it), and nothing has been
  compiled (no NDK/CMake toolchain available here either). `cpp/lame/README.md` explains
  exactly what's needed to finish this.
- An earlier idea to bundle an ffmpeg binary was raised and explicitly rejected on your
  correction, since the project's own plan document had already ruled out FFmpeg for good
  reasons (retired wrappers, licensing, native-build ownership) — reversing that would have
  contradicted the plan's own stated logic.

**`VideoTrimmer.kt`** — the trim engine, and this changed meaningfully based on your
clarification mid-session. Originally scoped as "keyframe-snapped by default, may fall back
to re-encode." You clarified this needs to be a **hard guarantee**: lossless, stream-copy
only, never touching the codec. This is now enforced via
`MediaItem.ClippingConfiguration.Builder.setStartsAtKeyFrame(true)` (confirmed current API),
which forces the cut to snap to the nearest keyframe rather than ever re-encoding the
boundary. Per your decision, if Transformer still falls back anyway in some rare edge case
(corrupt GOP, unusual container), the job is logged but still allowed to complete — not
failed.

A related but separate idea — Media3 1.8.0's `experimentalSetMp4EditListTrimEnabled`, a
newer API for frame-accurate trims via MP4 edit lists — was investigated and explicitly
**not used**, since it depends on the playing app respecting edit lists (not universal
support), and you chose the simpler keyframe-only approach for v1.

**`VideoCompressor.kt`** — resolution downscale via `Presentation.createForShortSide` (confirmed
current API) and bitrate control via `DefaultEncoderFactory`/`VideoEncoderSettings`/
`AudioEncoderSettings`. One correction to the blueprint here too: it implied bitrate settings
attach through `EditedMediaItem`'s effects list — they don't; they go through
`Transformer.Builder.setEncoderFactory(...)`. **Flagged honestly:** the exact
`VideoEncoderSettings.Builder`/`DefaultEncoderFactory` method signatures could not be fully
confirmed against docs this session (only partially matching snippets came back), so this
file is written against the well-established Media3 pattern but is explicitly marked
unverified in its own doc comment — check against real sources before trusting it compiles
as-is.

### 1.3 Output handling (`output/`)

**`OutputFileStore.kt`** — publishes finished jobs into `MediaStore` so they show up in the
user's gallery. This is genuinely new territory for the app: an audit confirmed the existing
`MediaStoreIndexer` only *queries* MediaStore (for indexing pre-existing library files) and
has no insert precedent anywhere in the codebase, contrary to an early blueprint claim that
this would be "consistent with existing conventions."

Key mechanics:
- Transformer writes to an app-private staging file first (it takes a plain file path, not
  a `content://` Uri, so it can't write directly into a MediaStore pending entry) — 
  `publish()` then copies the finished file into the right collection and deletes the staging
  copy.
- **Per your requirement:** compressed output goes to `Movies/Watermelon/compressed`, trimmed
  output to `Movies/Watermelon/trimmed`, both configurable rather than hardcoded — the paths
  are passed in as lambdas so the app can back them with live settings.
- **Real limitation flagged, not hidden:** on API < 29, `MediaStore.insert()`'s `RELATIVE_PATH`
  field is silently ignored — custom subfolders only actually work on Android 10+. This is
  documented in the code and called out again in `UI_MANIFEST.md` so the settings screen
  can surface it rather than silently fail to honor the user's chosen path on older devices.

**`OutputNaming.kt`** — implements your filename requirement: outputs are named
`originalName_suffix.ext`, not a generic name:
- Trim: `video_trimmed_00-00-15_00-00-16.mp4` (HH-MM-SS timestamps, your chosen format)
- Compress: `video_compressed.mp4`
- Extract audio: `video_audio.mp3`

**`OriginalFileDeleter.kt`** — implements the "keep or delete original" prompt's actual
deletion mechanics. This surfaced a real Android platform constraint: deleting a pre-existing
library video (one the app didn't itself insert into MediaStore) throws
`RecoverableSecurityException` on API 29+ rather than just deleting. The real fix is
`MediaStore.createDeleteRequest()` → an `IntentSenderRequest` → an
`ActivityResultLauncher`-based system consent dialog — confirmed via web search (this is
platform `MediaStore` API, not Media3, so Context7 wasn't the right tool for it). This had to
be written as an Activity-scoped helper class (`registerForActivityResult` requires
construction in an Activity's `onCreate`), not something `MediaJobManager` itself can do —
flagged as an open wiring question for whoever builds the actual UI (see `UI_MANIFEST.md`).

`MediaJobManager.resolveOriginalFileDecision(...)` is the entry point tying this together:
called once the user answers the prompt, it either does nothing (Keep) or deletes (Delete) —
with the API<29/29+ split documented directly in its own doc comment rather than silently
assumed to work uniformly.

### 1.4 Foreground service (`service/`)

**`MediaJobService.kt`** — the Phase 1 foreground service, needed because trim/compress on
large files can take minutes and the process could be killed while backgrounded. Uses the
`dataSync` foreground service type (confirmed current requirement via web search — Android 14+
mandates declaring a service type, and `mediaPlayback` isn't the right fit for a non-playback
job like this). Observes `MediaJobManager.jobs`, shows a single progress notification with a
Cancel action, and stops itself once the job queue empties.

**Known gap, flagged rather than papered over:** this app has no dependency-injection
framework, so there's no clean existing way for the service to get a `MediaJobManager`
instance. A static `jobManagerProvider` var is used as a pragmatic stopgap — documented in the
class's own doc comment as exactly that, not presented as a real solution. Also noted: Android
15+ imposes a 6-hour time limit on `dataSync` foreground services, which could theoretically
matter for a very large compress job, though this wasn't built out.

---

## 2. Edits to existing files (the only real ones — confirmed via `git diff`)

| File | What changed |
|---|---|
| `settings.gradle.kts` | Registered the new `:media-tools` module. |
| `gradle/libs.versions.toml` | Bumped `media3` from 1.5.1 → 1.6.0 (needed for the `Transformer.Listener` API used); added four new catalog entries: `media3-transformer`, `media3-effect`, `media3-muxer`, `media3-common`. |
| `app/src/main/AndroidManifest.xml` | Added `WRITE_EXTERNAL_STORAGE` (maxSdkVersion=28, needed for `MediaStore.insert()` on API<29), `FOREGROUND_SERVICE_DATA_SYNC` permission, and registered `MediaJobService` as a `dataSync`-type service — all matching the manifest's existing formatting style. |
| `library-storage/.../FolderVisibilityStoreImpl.kt` | Added four small methods (`getCompressedOutputPath`/`setCompressedOutputPath`, `getTrimmedOutputPath`/`setTrimmedOutputPath`) reusing the class's existing generic `getString`/`putString` helpers — no new settings-storage class needed. |

Nothing else in the repo was touched. (As noted above, a raw diff will show many other files
as changed — that's line-ending noise from this session's zip round-trip, not intentional
edits, and none of it is described as "changed by me" anywhere in this dossier.)

---

## 3. Corrections made to the original blueprint

The blueprint (`PREMIUM_MEDIA_TOOLS_BLUEPRINT.md`) had a few claims that didn't hold up once
checked against the real codebase or real docs — corrected rather than propagated:

1. **State-owner analogy was wrong.** The blueprint said `MediaJobManager` should mirror
   `PlaybackConnection`. Reading the actual file showed `PlaybackConnection` just holds a
   `MediaController` reference — it doesn't own or publish state. The real `StateFlow` owner
   is `PlaybackControllerImpl`, which is what `MediaJobManager` was actually modeled on.
2. **No existing MediaStore-insert convention.** The blueprint claimed output-file handling
   would be "consistent with how the rest of this app already sources media through
   MediaStore." Checking `MediaRepositoryImpl`'s indexer showed it only ever *queries*
   MediaStore — there's no insert precedent anywhere in the repo. `OutputFileStore` is new
   territory, treated and documented as such.
3. **Bitrate wiring location.** The blueprint implied `AudioEncoderSettings`/bitrate config
   attaches via `EditedMediaItem`'s effects. It actually goes through
   `Transformer.Builder.setEncoderFactory(DefaultEncoderFactory...)`.
4. **Trim-accuracy framing.** The blueprint discussed keyframe-snap vs. frame-accurate as an
   implicit Transformer behavior. Docs research surfaced a real, explicit API for both sides
   of that tradeoff — `setStartsAtKeyFrame(boolean)` for the fast path (now used, per your
   requirement) and 1.8.0's `experimentalSetMp4EditListTrimEnabled` for a real (if
   player-dependent) frame-accurate option, which the blueprint didn't mention at all.

---

## 4. Product decisions made during this session (yours, not assumed)

- MP3 output: **Option B, real MP3** — not the blueprint's original default suggestion of AAC/.m4a.
- Rejected bundling an ffmpeg binary once it was pointed out that this contradicted the
  plan's own stated reasoning for avoiding FFmpeg; kept the libmp3lame JNI approach instead.
- Trim must be **lossless, stream-copy-only, always** — not "fast by default, re-encode if
  needed." If Transformer still has to fall back in some edge case, the job **completes
  anyway**, just logged — it does not fail.
- Frame-accurate trim (the 1.8.0 edit-list API) is **out of scope for v1** — keyframe-snap
  only.
- **Keep-or-delete-original prompt** required after Trim/Compress, **not** after Extract Audio
  (since audio extraction doesn't replace/touch the source video).
- Output filenames: `originalName_trimmed_HH-MM-SS_HH-MM-SS.ext`,
  `originalName_compressed.ext` — your exact chosen format.
- Output location: `Movies/Watermelon/compressed` and `Movies/Watermelon/trimmed`
  (`MediaStore.Video`, so results appear in gallery apps) — you explicitly chose this over a
  generic `Documents/` tree, and confirmed both must be user-changeable via settings.

---

## 5. What's built vs. what's still open

**Built and internally consistent:**
Full job/engine/output/service layer for all three features (extract audio, trim, compress),
wired end-to-end from "user calls an engine method" through to "file appears in MediaStore
and the user is asked what to do with the original." Settings keys for the two output paths.
A UI manifest (`UI_MANIFEST.md`) specifying what screens/dialogs/entry points need to exist,
grounded in this app's real naming conventions and existing components (confirmed via
`VideoListScreen.kt`'s existing `DropdownMenu`, etc.) — no UI code written yet, per your
instruction to hold off.

**Explicitly not done, flagged rather than silently skipped:**
- Nothing has been compiled or run — this sandbox has no NDK, no Android build tooling, and
  no network access to fetch `libmp3lame` sources.
- `libmp3lame` itself isn't vendored into the repo.
- The DI/ownership question for `MediaJobManager` (how `MediaJobService` and any future UI
  actually get an instance) is unresolved — flagged in both the service's doc comment and
  the UI manifest, not guessed at.
- `VideoCompressor`'s exact encoder-settings API surface is unverified against real sources.
- The Activity-level wiring for `OriginalFileDeleter` (how a Compose screen reaches an
  Activity-scoped helper) is a decision left to whoever builds the actual UI.
- No UI code — per your explicit instruction this session.

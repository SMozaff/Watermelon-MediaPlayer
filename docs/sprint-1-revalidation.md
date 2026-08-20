# Sprint 1 Revalidation — Watermelon MediaPlayer

**Audited revision:** `5a33808`  
**Review date:** 20 August 2026  
**Scope:** P0-01 through P0-04, revalidated against the checked-out source before implementation.

## Conclusion

All four P0 findings remain substantively valid. The codebase includes useful foundations—full settings persistence, a MediaStore indexer that tracks indexing phases, a batch-delete consent flow, idempotent playlist insertion, and an existing activity-result deletion helper—but the phone application flow still does not connect those foundations into truthful, user-visible behavior.

| P0 item | Revalidated evidence | Sprint 1 action |
|---|---|---|
| **P0-01 — Settings are not consistently observable** | `SettingsPersistence` saves all listed fields, but `MainActivity` does not pass `forcedRtl` to `WatermelonTheme`; folder/video screens own independent default layout state; video components do not consume thumbnail/duration/file-size preferences; memory-safety and full-folder-access switches have no observable production effect. | Pass forced RTL through the theme; pass library-display preferences to screens/components; hide the two unimplemented advanced switches rather than expose misleading controls. |
| **P0-02 — Add-to-playlist performs a Favourite action** | `MainActivity` maps the player’s `onAddToPlaylist` directly to `playlistRepository.addToFavourites(mediaUri)`. The repository already has user playlist create/add support, and `addToPlaylist` is idempotent. | Replace the callback with a player-scoped picker that selects or creates a user playlist and confirms the selected destination. |
| **P0-03 — Player delete flow is unsafe/incomplete** | `MainActivity` launches `MediaStore.createDeleteRequest` only on API 30+ and immediately pops the player route. There is no confirmation, result feedback, or pre-R delete path. The codebase already uses `ActivityResultContracts.StartIntentSenderForResult` for batch deletion and media-job original-file decisions. | Add an in-app `Delete from device` confirmation; await the activity result before mutating playback/navigation; implement direct deletion for pre-R paths and show success/cancel/failure feedback. |
| **P0-04 — Empty/loading/error library states are conflated** | `VideoListScreen` renders an animation for any empty list; `FolderBrowserScreen` renders scanning content based on an empty list. The indexer exposes `IDLE`, `SWEEPING`, `EXTRACTING`, and `COMPLETE`, but repository interfaces/screen view models do not expose an explicit screen state or errors. | Expose a library-state flow from the media repository; derive explicit loading, empty, content, and error UI state in screens; provide a `Refresh library` action and permission-aware recovery copy. |

## Design constraints preserved

The remediation will preserve the existing retro-industrial visual system, the mini-player, the separate TV composition, the batch selection flow, and the existing protected original-file deletion flow for media jobs. P0 work changes only the false or unsafe product contracts identified above.

## Validation targets after implementation

1. Changing each visible Sprint 1 settings preference changes the corresponding UI immediately and remains effective after recreation.
2. The player’s Add to playlist action lets the user choose/create a user playlist; Favourites changes only through the separate Favourite action.
3. A delete request names the media, can be cancelled without leaving the player, yields a clear final result, and works through the appropriate supported API path.
4. A library screen can distinguish scanning, no eligible media, no permission/failed indexing, and loaded content; empty/error states offer an appropriate recovery action.

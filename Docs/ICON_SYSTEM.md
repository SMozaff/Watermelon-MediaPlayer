# Watermelon Icon System

Watermelon’s vector library now uses a unified **rounded, two-colour** system for operational controls, media tools, library actions, and state pairs. The system is designed to remain recognizable at 24 dp and to survive glyph tinting on phone and TV surfaces.

> **Core rule:** Operational icons are rounded two-colour glyphs. The renderer may tint them for state. Only the launcher and TV banner retain their separately documented large-surface geometry.

## Rendering contract

| Renderer | Use for | Colour behaviour |
|---|---|---|
| `WatermelonGlyph` | Navigation, toolbars, player transport, overflow, settings, library actions, and stateful controls | Caller supplies semantic tint for default, selected, disabled, or focused state. |
| `WatermelonArtwork` | Brand/feature art when authored colours must be preserved | Keeps authored colours; do not rely on tint to communicate state. |

New operational vectors must be compatible with the glyph path. New feature art must explicitly opt into `WatermelonArtwork`.

## Geometry and palette

| Property | Standard |
|---|---|
| Operational viewport | 24×24 dp |
| Optical boundary | Keep the visible form inside a consistent 2 dp optical inset |
| Geometry | Rounded caps and joins; compact silhouettes; no gradients or shadows |
| Main colours | Watermelon Red `#E63946` and Rind Green `#1F8B68` |
| State communication | Shape/fill difference first; tint is supporting evidence, never the only distinction |
| Detail | No internal detail that disappears at 24 dp |
| Launcher | Dedicated 108×108 viewport using the same two-colour brand language |
| TV banner | Preserved 320×180 composition; it is banner artwork, not a 24 dp icon |

## Canonical asset roles

| Meaning | Canonical asset | Role |
|---|---|---|
| Navigate back | `ic_arrow_back` | Single back-navigation glyph; the old exact duplicate was removed. |
| Overflow menu | `ic_more_vertical` | Vertical overflow; use horizontal overflow only in a documented layout. |
| Primary play | `ic_play` | Watermelon-red play form with green support bar. |
| Pause and transport | `ic_pause`, `ic_skip_next`, `ic_skip_previous`, `ic_fast_forward`, `ic_rewind` | Consistent rounded transport family. |
| Settings and utility | `ic_settings`, `ic_share`, `ic_delete`, `ic_edit`, `ic_refresh` | Shared red/green operational treatment. |
| Media tools | `ic_trim`, `ic_audio_extract`, `ic_compress_video`, `ic_screenshot_single` | Concrete tool metaphors; Trim must never use Screenshot. |
| State pairs | `ic_favorite`/`ic_favorite_off`, `ic_star`/`ic_star_off`, repeat, shuffle, volume, and lock pairs | Shape or fill changes supplement accessible labels. |

## Brand hierarchy

The **watermelon play symbol** is the master product mark for launcher and primary playback. The **cassette/VHS treatment** belongs only to the TV banner and retro-media surfaces. The TV banner is intentionally preserved as a 320×180 composition instead of being forced into the operational 24 dp system.

## Review checklist

Every new or changed vector must use the 24×24 viewport unless it is a documented large-surface asset, use only the approved red/green family, remain legible without its filename, have one canonical registry role, and be included in a three-icon contact-sheet review at 128 px rendering. The component using it must also provide a visible or accessible text label whenever the silhouette is ambiguous.

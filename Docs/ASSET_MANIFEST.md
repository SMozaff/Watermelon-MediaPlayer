# Watermelon Asset Manifest

This manifest records intentional drawable decisions. A vector may be retained only when it has a current production use or a named, approved future role.

## Removed during asset-system cleanup

| Asset | Decision | Reason |
|---|---|---|
| `ic_back.xml` | Removed | Byte-for-byte duplicate of `ic_arrow_back.xml`. |
| `ic_more_vert.xml` | Removed | Unreferenced variant; `ic_more_vertical.xml` is the canonical vertical-overflow asset. |
| `ic_play_arrow.xml` | Removed | Unreferenced compact-play variant; avoid an undocumented second play meaning. |
| `ic_settings_outline.xml` | Removed | Unreferenced settings variant; `ic_settings.xml` is canonical. |

## Retained contextual variants

| Asset | Approved role | Current status |
|---|---|---|
| `ic_confirm.xml` | User-initiated confirmation/submit action | Retained for approved action contexts. |
| `ic_check_circle.xml` | Completed/success result state | Retained for status/result contexts. |
| `ic_more_horizontal.xml` | Horizontal overflow in a documented horizontal layout | Dormant; do not use as a random visual alternative. |
| `ic_volume_medium.xml` | Medium-level volume indicator | Dormant until level-indicator granularity is activated. |
| `ic_volume_low.xml` | Low-level volume indicator | Dormant until level-indicator granularity is activated. |
| `ic_volume_mute.xml` | Muted state | Active. |
| `ic_volume_mute_off.xml` | Explicit unmuted state if needed | Dormant; current unmuted state uses Volume High. |
| `ic_search.xml` | Library search affordance | Dormant until library search is implemented. |
| `ic_edit.xml` | File/playlist edit action | Dormant until an edit workflow is exposed. |
| `ic_refresh.xml` | Explicit re-index/refresh action | Dormant until a refresh workflow is exposed. |
| `ic_lock_open.xml` | Explicit unlock state | Dormant; current unlock journey is handled by the overlay. |

## New canonical asset

| Asset | Role | Notes |
|---|---|---|
| `ic_trim.xml` | Trim/cut media range | Filmstrip + scissors treatment. Replaces misuse of Screenshot in media tools. |

## Maintenance rule

When a dormant asset becomes active, update this manifest with its screen/component call site. When an action is retired, remove both its registry entry and its unused drawable unless the asset is intentionally retained for a documented future feature.

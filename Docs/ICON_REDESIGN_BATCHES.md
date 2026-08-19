# Icon Redesign Batches

The complete redesign covers **62 true icon vectors** in 20 full three-icon groups plus one final two-icon group, alongside the preserved 320×180 TV banner. Every operational vector uses a 24×24 viewport and the rounded Watermelon Red / Rind Green system.

| Batch | Icons | Role |
|---:|---|---|
| 01 | `app/ic_launcher`, `app/ic_play`, `ic_arrow_back` | Brand and navigation |
| 02 | `ic_audio_extract`, `ic_background_play`, `ic_badge_new` | Media tools and status |
| 03 | `ic_brightness_high`, `ic_check_circle`, `ic_close` | Player utility and status |
| 04 | `ic_compress_video`, `ic_confirm`, `ic_delete` | Media tools and actions |
| 05 | `ic_edit`, `ic_fast_forward`, `ic_favorite` | Library action and playback |
| 06 | `ic_favorite_off`, `ic_folder`, `ic_folder_open` | State and library browsing |
| 07 | `ic_lock`, `ic_lock_open`, `ic_more_horizontal` | Security and overflow |
| 08 | `ic_more_vertical`, `ic_orientation_auto`, `ic_orientation_landscape` | Overflow and orientation |
| 09 | `ic_orientation_portrait`, `ic_pause`, `ic_pip` | Orientation and player |
| 10 | `ic_play`, `ic_playlist`, `ic_playlist_add` | Playback and playlist |
| 11 | `ic_playlist_remove`, `ic_refresh`, `ic_repeat_all` | Playlist, refresh, repeat |
| 12 | `ic_repeat_off`, `ic_repeat_one`, `ic_rewind` | Playback state and transport |
| 13 | `ic_screenshot_single`, `ic_search`, `ic_settings` | Player utility and discovery |
| 14 | `ic_share`, `ic_shuffle_off`, `ic_shuffle_on` | File sharing and playback state |
| 15 | `ic_size_large`, `ic_size_medium`, `ic_size_small` | Display sizing |
| 16 | `ic_skip_next`, `ic_skip_previous`, `ic_sleep_timer` | Transport and timer |
| 17 | `ic_sort_ascending`, `ic_sort_descending`, `ic_star` | Library sort and favourite state |
| 18 | `ic_star_off`, `ic_trim`, `ic_video_file` | Favourite state and media tools |
| 19 | `ic_video_unavailable`, `ic_view_grid`, `ic_view_list` | Library states and layout |
| 20 | `ic_volume_high`, `ic_volume_low`, `ic_volume_medium` | Volume states |
| 21 | `ic_volume_mute`, `ic_watermelon_logo` | Final volume and brand review; two icons remain after the 20 full groups. |

> The inventory script groups files alphabetically for deterministic coverage. The launcher is checked separately at 108×108 geometry, and `tv_banner.xml` is intentionally excluded from the 24 dp icon system because it is a 320×180 TV composition.

## Review standard

Each batch is reviewed for immediate recognizability, consistent optical weight, red/green-only authored colour, rounded geometry, and state-pair distinction at 24 dp. Any icon that remains ambiguous at that size must gain an accessible label and, where appropriate, a visible text label in the owning UI component.

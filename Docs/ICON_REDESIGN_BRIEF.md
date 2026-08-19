# Watermelon Icon Redesign Brief

## Goal

Redesign the complete Android vector icon library as a single, modern operational system. Assets are redesigned in batches of three, but each batch must read as part of one family at 24 dp.

> **Design direction:** rounded, two-colour, compact, high-contrast vector glyphs with a clear media-player personality and no decorative text, gradients, shadows, or pseudo-3D depth.

## Core visual language

| Attribute | Standard |
|---|---|
| Grid | 24×24 dp viewport; 2 dp optical boundary; shapes optically centred, not merely mathematically centred. |
| Palette | **Watermelon Red** `#E63946` for action/focus/active detail and **Rind Green** `#1F8B68` for structure/default form. Transparent background. |
| Geometry | Rounded caps and joins; 2 dp primary line weight; 3 dp only for tiny high-emphasis glyphs. |
| Composition | One recognisable silhouette, at most one small semantic detail, and no internal micro-detail below 2 dp. |
| State pairs | Different silhouette or fill/outlining first; colour is supporting evidence, never the sole indicator. |
| Contrast | Must work on deep-carbon surfaces and when flattened through the glyph renderer. |
| Scale | First judged at 24 dp; feature-art uses may scale to 32–48 dp without becoming visually dense. |

## Roles

| Role | Treatment | Examples |
|---|---|---|
| Navigation and player controls | Sparse rounded glyph, one dominant action direction | Back, play, pause, skip, rewind, volume, menu. |
| Library and file actions | Simple container/object silhouette plus one recognisable action cue | Folder, playlist, video file, search, delete, edit. |
| Media tools | A single concrete tool metaphor, no generic screenshot substitution | Trim, compress, audio extract, screenshot. |
| State/mode controls | Base symbol plus a clear small mode marker | Shuffle on/off, repeat all/one/off, lock/open, favourite/off. |
| Brand surfaces | Preserve the watermelon idea in a minimal play-slice or cassette motif, without text or image-like detail | Launcher, primary play, TV banner. |

## Batch validation

For each three-icon batch, verify that each symbol is recognisable without its filename, uses only the two approved colours, shares the 24 dp grid and rounded geometry, remains distinct from its state/semantic pair, and does not introduce a third decorative motif.

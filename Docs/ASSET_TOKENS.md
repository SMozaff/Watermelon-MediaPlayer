# Watermelon Asset Tokens

This reference defines the semantic treatment of the redesigned vector library. Operational vectors use two approved authored colours and may be tinted by `WatermelonGlyph`; large-surface art follows its own documented geometry.

| Token | Approved value | Use | Do not use for |
|---|---|---|---|
| `iconRed` | `#E63946` | Active detail, primary action, state marker, destructive/cut cue | Unexplained decoration |
| `iconGreen` | `#1F8B68` | Structure, default form, supporting action | Warning semantics by itself |
| `glyphDefault` | Component semantic tint | Default operational icon state | Large authored artwork |
| `glyphSelected` | Component semantic tint | Selected/active operational state | Sole state evidence when shape can change |
| `glyphDisabled` | Component semantic tint | Disabled operational state | Interactive active state |
| `glyphFocus` | Component semantic tint | Keyboard/D-pad focus and high-attention state | Decoration |
| `surfaceCarbon` | `#101614` / `#1C2526` | Deep visual ground for previews and dark surfaces | Icon path colour |
| `warning` | Theme-owned warning colour | Warnings and irreversible-action context | Routine selection state |
| `destructive` | Theme-owned destructive colour | Delete/destructive action context | Normal navigation or playback success |

## Application rules

New operational vectors should use only `iconRed` and `iconGreen` in their authored paths and should render through `WatermelonGlyph`. A state pair must change silhouette, fill, marker, or composition in addition to changing tint where possible. Accessible labels remain mandatory because a colour or small shape is never sufficient on its own.

The launcher uses its dedicated 108×108 vector geometry but remains within the same red/green brand family. The TV banner remains a 320×180 art composition and is not subject to the 24 dp glyph constraint.

## Pull-request gate

A new or changed vector is ready for review only when its canonical action name, three-icon batch, renderer type, viewport, colour role, accessible label, and 128 px contact-sheet preview are included in the pull request description.

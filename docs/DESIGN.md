# Design tokens: CarPlay / Apple HIG → Android

Source of truth: `app/src/main/java/com/afdalah/headunit/ui/theme/`.

## Principles applied
| HIG / CarPlay guideline | Implementation |
|---|---|
| Glanceable in < 2 s | 3 cards + dock, one primary value per card, ≤ 2 lines per item (`HuText` caps maxLines at 2) |
| Large touch targets | `HuDimens.minTarget = 76dp`; transport buttons 88–104dp, call buttons 128dp; ≥ 16dp between targets |
| Persistent sidebar | `Sidebar`: clock, mode pill, signal/BT/heat, speed, 3 recent apps, Home, Voice |
| Dark first, high contrast | true-black background, dark by default; sidebar sun/moon toggle for light; high-contrast option |
| One accent | `accentColor` in config, per-profile override, swatches in settings; `onAccent` picked by WCAG contrast |
| Materials | translucent "glass" panels (fill + hairline border + top highlight) over a faint accent radial |
| Continuous corners | `ContinuousRoundedShape`: superellipse corners starting at 1.528·r (like `.continuous`) |
| SF Symbols-style icons | Lucide line icons (2px stroke, round caps) as vector drawables |
| Uniform app icons | icons rendered full-bleed from adaptive layers, clipped to one 22.37% squircle mask |
| Short springs | damping 0.85, stiffness 500 → settles ≈ 240 ms |
| No decorative motion while driving | `HuTheme.decorativeMotion = !moving`; screen fades snap while moving |
| Reduce Motion | animator/transition scale 0 (One UI "Remove animations") → every spec becomes `snap()` |
| Haptics | `Modifier.huClickable`: VIRTUAL_KEY tick on tap, LONG_PRESS on long-press |
| Accessibility | contentDescription on every icon-only control, roles/state on switches and tabs, live regions for banners and calls |

## Color
| HIG token | Dark | Light | Night Drive |
|---|---|---|---|
| systemBackground → `background` | #000000 | #F2F2F7 | #000000 |
| card material → `glassFill` | white 8% | white 78% | warm white 5% |
| separator/border → `glassBorder` | white 12% | black 8% | warm 8% |
| secondarySystemFill → `fill` | white 12% | #787880 16% | warm 8% |
| label → `labelPrimary` | #FFFFFF | #000000 | #EDE3D6 |
| secondaryLabel → `labelSecondary` | #EBEBF5 72% | #3C3C43 78% | #BFB2A3 |
| tint → `accent` | #0A84FF | #0A84FF | #FF9F0A |
| systemRed / Green / Orange | #FF453A / #30D158 / #FF9F0A | #D70015 / #248A3D / #C93400 | #E5483E / #2DBF52 / #FF9F0A |

Secondary labels are darker than HIG's 60% in light mode so they clear WCAG AA 4.5:1 (≈ 5:1 on white,
≈ 9:1 on black). High contrast makes panels opaque, uses 2dp borders, promotes secondary text to primary
and adds +100 font weight.

## Typography (Inter for SF Pro)
| HIG style | iOS pt | Head Unit sp | Weight | Used for |
|---|---|---|---|---|
| largeTitle | 34 | 48 | Bold | speed, caller name, temperature |
| title1 | 28 | 40 | Bold | screen titles |
| title2 | 22 | 34 | SemiBold | "Available when parked", OBD values |
| title3 | 20 | 28 | SemiBold | track title, clock |
| headline | 17 | 24 | SemiBold | buttons, sender, setting rows |
| body | 17 | 22 | Regular | artist, banner text |
| callout | 16 | 20 | Medium | metadata |
| caption1 | 12 | 18 | Medium | smallest text (status, labels) |

Tracking tightens with size (−0.02em at 48sp), approximating SF's optical sizes. The Standby clock (128sp
Light) is the one deliberate exception, because Standby is only shown when not driving.

## Shape
| Token | Radius |
|---|---|
| card | 32dp continuous |
| panel (dock, settings) | 28dp |
| button / tile | 22dp |
| artwork | 18dp |
| appIcon | 22.37% of size |
| pill | 50% (circular) |

## Motion
| Token | Value |
|---|---|
| spring | dampingRatio 0.85, stiffness 500 |
| press scale | 0.95 |
| fade | 180 ms tween |
| banners | slide + fade (functional, allowed while moving) |
| screen changes | fade, snapped while moving or with reduce motion |

# Tierra Colorada - Zen — Design Spec

**Date:** 2026-10-20
**Author:** Consuelo Merino
**Status:** Draft, awaiting review

## 1. Purpose and success criteria

A relaxing "rake and shape" zen garden set in a Latin American rainforest clearing on red earth. It is a **free portfolio piece** published on Google Play: no ads, no in-app purchases, no accounts, no data collection.

The game succeeds when:

- Raking feels smooth and calm: drawing stays at 60 fps on a mid-range phone, including fast strokes.
- It looks finished enough for a public Play Store listing.
- The code on GitHub is clean, modern Kotlin + Jetpack Compose that the author is proud to show.

## 2. Scope

### In version 1

- **Rake**: draw multi-tine grooves in the sand with a finger.
- **Place objects**: stones, fern, heliconia, pond. Move, resize, rotate and delete them.
- **Living details**: hummingbird visits, drifting leaves, pond ripples, drifting canopy shadows.
- **Smooth** brush and **Clear all**.
- **Save** automatically; **share** a screenshot.
- **Ambient rainforest audio** with interaction sounds and a mute toggle.
- UI in **Spanish and English** (follows the phone's language; English is the fallback).

### Not in version 1

- Time-of-day lighting (planned for a later version).
- Tablet-specific layouts, landscape orientation, multiple saved gardens, scrolling or zooming the garden.
- Online features, accounts, ads, analytics, in-app purchases.

## 3. Platform and technology

| Item | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, drawing with Compose `Canvas` and Android `Bitmap` |
| Package name | `com.consuelomerino.tierracolorada` |
| App name | Tierra Colorada - Zen |
| Min / target SDK | minSdk 26, targetSdk 36 (compileSdk 36) |
| Orientation | Portrait only |
| Permissions | None (haptics use `View.performHapticFeedback`, which needs no permission) |
| Build output | Android App Bundle (`.aab`) signed with an upload key; Play App Signing enabled |

## 4. Architecture

A single Activity hosting one Compose screen. Code is split into packages that each have one job and do not depend on each other's internals.

| Package | Responsibility | Depends on |
|---|---|---|
| `sand` | Height map data, rake carving, smoothing, clearing, shading into a bitmap | nothing (pure Kotlin + `Bitmap`) |
| `objects` | Object model (type, position, scale, rotation, seed), drawing each type, footprint (area it covers) | nothing |
| `life` | Ambient animations: hummingbird, leaves, ripples, canopy shadows | `objects` (reads where heliconia/pond are) |
| `audio` | Ambient loop + short effects, mute state, audio focus | Android media APIs |
| `storage` | Save/load garden state atomically | `sand`, `objects` (their serialized forms) |
| `share` | Render the garden to an image and open the share sheet | `sand`, `objects` drawers |
| `ui` | `GardenScreen`, toolbar, object tray, `GardenViewModel` connecting everything | all of the above |

The `GardenViewModel` owns the current tool (Rake / Place / Smooth), the object list, mute state and the sand surface, and triggers saves.

## 5. Sand surface and raking

### Height map

- The sand is a `FloatArray` grid. Grid width is **360 cells**; height is scaled to the screen's aspect ratio. Each cell maps to `screenWidth / 360` pixels.
- Flat sand has height `0`. Grooves are negative, ridges positive, clamped to `[-1, 1]`.
- Shading converts heights to colors using a light from the top-left: each cell's slope (difference to its neighbours) brightens or darkens the base red-ochre color. The result is drawn into a `Bitmap` the size of the grid and scaled up to the screen with filtering.
- **Only the dirty rectangle** (the area changed since the last frame) is re-shaded each frame.

### Rake

- The rake has **4 tines**, spaced 8 cells apart, each with a radius of 2 cells.
- Tines sit on a line perpendicular to the finger's direction of travel, centred on the finger.
- Touch samples are smoothed (moving average of the last 3 points) and the path between samples is filled at steps of 1 cell, so fast swipes leave no gaps.
- Each tine stamp sets the cells under it to a smooth trough (lowest `-0.8` at the centre) and raises a ridge of `+0.3` on each side. Stamping replaces existing heights in that area, so raking over old lines overwrites them.
- Cells under an object's footprint are never changed.

### Smooth and clear

- **Smooth**: a brush of radius 12 cells moves heights under the finger 20 % of the way toward `0` per frame.
- **Clear all**: asks for confirmation, then plays a 600 ms sweep from left to right that sets every cell to `0`.

### Feel

- Rake scrape volume scales with finger speed.
- A light haptic tick every 24 cells of travel, using the system's haptic feedback (so it respects the phone's vibration setting).

## 6. Garden objects

| Type | Look (all drawn in code) | Special behaviour |
|---|---|---|
| Stone | Irregular rounded outline generated from its seed, soft gradient, optional moss patch | Pressing it into the sand stamps 3 concentric ripple rings around it |
| Fern | Fronds of repeating leaflets, slight variation from its seed | — |
| Heliconia | Stem with zigzag red-and-yellow bracts | Attracts the hummingbird |
| Pond | Organic dark teal shape with a lily pad | Ripples on tap; nothing else can overlap it |

- **Seed**: each object stores a random seed so its generated shape is the same after saving and loading.
- **Tray**: the Place tool opens a bottom tray with the four types. Dragging one onto the garden creates it.
- **Editing**: drag to move; pinch to resize (0.5×–2× of default size) and rotate; dragging onto the trash zone (appears at the bottom while dragging) deletes it.
- **Overlap rule**: objects may overlap each other, except the pond. An object dropped on the pond moves to the nearest free spot outside it. Only one pond is allowed; the pond item in the tray is disabled while one exists.
- **Limit**: at most 30 objects, to keep drawing smooth. The tray shows a short message when the limit is reached.

## 7. Living details

- **Canopy shadows**: soft, semi-transparent leaf shadow shapes drift slowly across the whole garden in a continuous loop.
- **Hummingbird**: only when at least one heliconia exists. Every 30–90 s (random), it flies in from off-screen, hovers at a randomly chosen heliconia for 3–5 s with blurred wings, then darts away.
- **Drifting leaves**: every 20–60 s, one leaf floats across, settles on the sand, and fades out after 5 s. Leaves do not change the sand.
- **Pond ripples**: gentle continuous ripples; a tap (touch without dragging) on the pond in any tool mode adds a stronger ring and plays a water sound.
- **Reduced motion**: if the system animator duration scale is 0 ("Remove animations"), hummingbird, leaves and shadows are off and ripples are static.
- All animation stops while the app is in the background.

## 8. Audio

- **Ambient loop**: rainforest birds, distant water, light rain. Plays at low volume while the app is in the foreground.
- **Effects**: rake scrape (looped while raking, volume tied to speed), stone placed, water tap, clear sweep.
- **Mute** toggle in the toolbar, remembered between sessions.
- Requests audio focus; pauses when another app plays audio or the app goes to the background.
- All sound files are **CC0**. `CREDITS.md` lists each file and its source.

## 9. Saving

- State saved: height map (as raw floats, compressed), object list (type, position, scale, rotation, seed), mute setting.
- Stored in the app's private files directory. No storage permission is needed.
- Saves when the app goes to the background and every 30 s while there are unsaved changes.
- **Atomic write**: write to a temporary file, then rename over the real file.
- **Corrupted or unreadable save**: start with fresh flat sand and no objects; never crash. The app writes a log entry for debugging.
- If the screen aspect ratio changes (e.g. a different device restored from backup), the saved height map is resampled to the new grid size.

## 10. Sharing

- **Share** renders the garden (sand, objects, current living details) at screen resolution without the toolbar, adds a small "Tierra Colorada - Zen" watermark in the bottom-right corner, and opens the Android share sheet via a `FileProvider` URI.
- No permissions are required.

## 11. Screen layout

- The garden fills the whole screen (edge to edge, behind the system bars).
- A **floating toolbar** at the bottom, inside the safe area: Rake, Place, Smooth, Clear all, then Mute and Share. The active tool is highlighted.
- All buttons have content descriptions for screen readers and touch targets of at least 48 dp.

## 12. Privacy and Play Store

- The app collects no data, makes no network requests, and declares no `INTERNET` permission.
- **Data safety form**: "No data collected, no data shared."
- **Privacy policy**: a short public page stating the above (required by Play; hosted e.g. on GitHub Pages).
- Store listing in Spanish and English. Release goes through closed testing (12 testers, 14 days) before production, as required for new personal developer accounts.

## 13. Testing

- **Unit tests** (JVM): rake stamping produces grooves and ridges at expected cells; gap filling for fast strokes; cells under footprints unchanged; smoothing moves heights toward 0; clear resets all cells; save/load round-trip; corrupted file falls back to an empty garden; height map resampling; pond overlap rule; 30-object limit.
- **UI tests** (Compose): tool switching highlights the right button; Clear all asks for confirmation; tray disables pond when one exists; mute state persists.
- **Performance check**: on the Pixel API 36 emulator, a scripted fast rake stroke keeps frame time under 16 ms (checked with the Android Studio profiler / JankStats).
- **Manual testing**: by the 12 closed testers on real phones.

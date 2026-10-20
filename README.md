# Tierra Colorada - Zen

A relaxing "rake and shape" zen garden set in a Latin American rainforest clearing on red earth.
Draw flowing patterns in the sand with your finger, set stones, ferns, heliconias and a pond,
and watch hummingbirds visit while rain and birdsong play softly in the background.

Free, offline, no ads and no data collection. Built with Kotlin and Jetpack Compose; all art is drawn in code.

## Features

- **Rake** multi-tine grooves that follow your finger, with soft 3D shading
- **Place** stones (which press ripple rings into the sand), ferns, heliconias and a pond; drag to move, pinch to resize and rotate, drop on the trash to remove
- **Smooth** the sand with a brush, or **clear** the whole garden
- **Living details:** canopy shadows, drifting leaves, pond ripples, and a hummingbird that visits heliconias
- Ambient rainforest audio with a mute toggle
- The garden saves automatically; **share** a picture of it
- English and Spanish

## Build and run

Requirements: Android Studio (or JDK 17+ and the Android SDK, platform 36).

```bash
./gradlew assembleDebug          # build
./gradlew installDebug           # install on a running emulator or device
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew connectedDebugAndroidTest   # UI tests (needs an emulator or device)
./gradlew bundleRelease          # Play Store bundle (sign it in Android Studio)
```

## Project layout

| Package | What it does |
|---|---|
| `sand` | Height map, rake, smoothing, ripples, clear sweep, shading |
| `objects` | Object model, placement rules, seeded shapes, drawing |
| `life` | Hummingbird, leaf and ripple timing, plus their drawing |
| `audio` | Ambient loop and sound effects |
| `storage` | Atomic save and tolerant load |
| `share` | Renders and shares a picture of the garden |
| `ui` | ViewModel, garden screen, toolbar, object tray |

Design: [docs/superpowers/specs/2026-10-20-tierra-colorada-zen-design.md](docs/superpowers/specs/2026-10-20-tierra-colorada-zen-design.md) ·
Privacy: [docs/privacy-policy.md](docs/privacy-policy.md) ·
Sounds: [CREDITS.md](CREDITS.md) ·
Performance: [docs/performance.md](docs/performance.md)

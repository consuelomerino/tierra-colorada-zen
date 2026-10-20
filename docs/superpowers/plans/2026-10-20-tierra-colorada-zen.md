# Tierra Colorada - Zen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and package the version 1 "rake and shape" rainforest zen garden Android game described in the spec.

**Architecture:** Single-Activity Jetpack Compose app. The game logic (sand height map, rake, objects, life timing, save format) is pure Kotlin with JVM unit tests; thin Android/Compose layers draw it, play audio, and handle touch. A `GardenViewModel` connects the pieces and emits one-off events (sounds, haptics) that the screen turns into Android calls.

**Tech Stack:** Kotlin 2.2.10 (built into AGP 9.4.1), Jetpack Compose (BOM 2026.02.01, Material 3), AndroidX Lifecycle ViewModel 2.6.1, JUnit 4, Compose UI tests, Gradle 9.8.2.

**Spec:** `docs/superpowers/specs/2026-10-20-tierra-colorada-zen-design.md`

## Global Constraints

- Package / applicationId / namespace: `com.consuelomerino.tierracolorada`. Source root: `app/src/main/java/com/consuelomerino/tierracolorada/`.
- App name: `Tierra Colorada - Zen` (same in every language).
- minSdk 26, targetSdk 36, compileSdk 36. If the AAR metadata check demands a higher compileSdk, raise **only** compileSdk to 37 (already installed); targetSdk stays 36.
- Portrait only. Edge-to-edge.
- No `INTERNET` permission, no other permissions. No ads, analytics, accounts or network calls.
- UI strings exist in `values/strings.xml` (English) and `values-es/strings.xml` (Spanish). No hard-coded user-visible text in Kotlin.
- All touch targets ≥ 48 dp; every icon button has a content description.
- Height map grid width: **360 cells**; values clamped to `[-1, 1]`; flat = `0`.
- Pure-logic packages (`sand`, `objects` model/rules, `life` timing, `storage`) must not import `android.*` or Compose, so they run as JVM unit tests. (Exception: `storage` may use `android.util.Log`; unit tests run with `unitTests.isReturnDefaultValues = true`.)
- Commits: explicit paths only (`git add <paths>`). Never `git add -A` — the untracked `HelloPlay/` folder must not be committed.
- Version catalog: all dependency versions live in `gradle/libs.versions.toml`.

## Review Focus

1. **Second finger while raking:** in Rake/Smooth mode only the first pointer counts; lifting and re-touching elsewhere must not draw a line joining the two strokes. → Task 3 test `newStrokeDoesNotConnectToPreviousStroke`.
2. **Raking past the screen edge:** stamps partly or fully outside the grid are clipped, never crash. → Task 2 test `setOutOfBoundsIsIgnored`, Task 3 test `strokeOffGridDoesNotCrash`.
3. **Finger held still / tiny moves:** direction must never become NaN; the rake keeps its last direction. → Task 3 test `stationaryFingerProducesNoNaN`.
4. **Truncated or wrong-version save file** (phone died mid-write before the atomic rename existed, or a future format): load returns `null`, app starts fresh. → Task 7 tests `truncatedFileLoadsAsNull`, `unknownVersionLoadsAsNull`.
5. **Object dropped at the very edge or pushed off the pond toward an edge:** its centre stays inside the garden, and it still ends up outside the pond. → Task 5 test `pondPushNearEdgeStaysInBoundsAndOutsidePond`.

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew + gradle/wrapper/*
.gitignore
CREDITS.md                                      – sound sources (Task 10)
tools/gen_sounds.py                             – generates CC0 sounds (Task 10)
docs/privacy-policy.md                          – Play privacy policy (Task 13)
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/values/strings.xml, values-es/strings.xml, values/themes.xml
app/src/main/res/xml/file_paths.xml             – FileProvider paths (Task 11)
app/src/main/res/raw/*.ogg|*.wav                – sounds (Task 10)
app/src/main/java/com/consuelomerino/tierracolorada/
  MainActivity.kt
  sand/HeightMap.kt         – grid, clamp, dirty rect, resample
  sand/Rake.kt              – multi-tine stroke carving
  sand/SandTools.kt         – smooth brush, stone ripples
  sand/ClearSweep.kt        – timed clear
  sand/SandShader.kt        – heights → ARGB pixels
  objects/GardenObject.kt   – model + ObjectType
  objects/GardenObjects.kt  – placement rules, footprint mask
  objects/ObjectShapes.kt   – seeded geometry (pure)
  objects/ObjectDrawing.kt  – DrawScope drawing per type
  life/LifeDirector.kt      – hummingbird/leaf/shadow timing (pure)
  life/PondRipples.kt       – tap ripple rings (pure)
  life/LifeDrawing.kt       – DrawScope drawing of life
  audio/GardenAudio.kt      – ambient loop, effects, focus
  storage/GardenStore.kt    – snapshot format, atomic save, load
  share/GardenSharer.kt     – render image + share intent
  ui/GardenViewModel.kt     – state, events, autosave
  ui/GardenScreen.kt        – canvas, gestures, sand bitmap
  ui/Toolbar.kt             – tool buttons, mute, share
  ui/ObjectTray.kt          – place tray, trash zone
  ui/theme/Theme.kt         – colours
app/src/test/java/com/consuelomerino/tierracolorada/...      – JVM unit tests (mirrors packages)
app/src/androidTest/java/com/consuelomerino/tierracolorada/ui/GardenScreenTest.kt
```

---

### Task 1: Project scaffold

**Files:**
- Create: Gradle files listed above, `.gitignore`, `app/build.gradle.kts`, `AndroidManifest.xml`, `MainActivity.kt`, `values/strings.xml`, `values-es/strings.xml`, `values/themes.xml`, `ui/theme/Theme.kt`
- Test: `app/src/test/java/com/consuelomerino/tierracolorada/SmokeTest.kt`

**Interfaces:**
- Produces: buildable app; `TierraColoradaTheme { }` composable; string resources below (later tasks add strings to both files).

- [ ] **Step 1: Create the Gradle setup**

Copy the structure of `~/AndroidStudioProjects/ConsuHelloWorld` (version catalog, plugins `android-application` + `kotlin-compose`, `compose = true`, Java 11). Changes from that template:
- Gradle wrapper `9.8.2` (`gradle wrapper --gradle-version 9.8.2`).
- Add libraries `androidx.lifecycle:lifecycle-viewmodel-compose` and `androidx.lifecycle:lifecycle-runtime-compose`, both on the existing `lifecycleRuntimeKtx` version.
- `namespace`/`applicationId` per Global Constraints; `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36`; `versionCode = 1`, `versionName = "1.0"`.
- `testOptions { unitTests.isReturnDefaultValues = true }`.
- `.gitignore`: standard Android (`.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `.DS_Store`, `*.jks`, `*.keystore`).
- `local.properties` with `sdk.dir=/Users/consu/Library/Android/sdk` (ignored by git).

- [ ] **Step 2: Manifest, activity, theme, strings**

- Manifest: one `MainActivity`, `android:screenOrientation="portrait"`, `exported=true`, launcher intent filter, label `@string/app_name`, theme `@style/Theme.TierraColorada` (parent `android:Theme.Material.Light.NoActionBar`). No permissions.
- `MainActivity : ComponentActivity` calls `enableEdgeToEdge()` and `setContent { TierraColoradaTheme { Box(Modifier.fillMaxSize().background(SandBase)) } }`.
- `Theme.kt`: `val SandBase = Color(0xFFB5523B)`, `val LeafDark = Color(0xFF1F4D2E)`, `val LeafLight = Color(0xFF4F8A3C)`, `val HeliconiaRed = Color(0xFFD7263D)`, `val HeliconiaYellow = Color(0xFFF2B705)`, `val PondTeal = Color(0xFF1B5E63)`; `TierraColoradaTheme` wraps `MaterialTheme` with a light scheme using `LeafDark` as primary.
- Strings (English / Spanish):

| key | en | es |
|---|---|---|
| app_name | Tierra Colorada - Zen | Tierra Colorada - Zen |
| tool_rake | Rake | Rastrillo |
| tool_place | Place | Colocar |
| tool_smooth | Smooth | Alisar |
| tool_clear | Clear all | Borrar todo |
| sound_off | Mute | Silenciar |
| sound_on | Sound on | Activar sonido |
| share | Share | Compartir |
| clear_title | Clear the garden? | ¿Borrar el jardín? |
| clear_message | All the sand will be smoothed. Your objects stay in place. | Toda la arena quedará lisa. Tus objetos se quedan en su lugar. |
| clear_confirm | Clear | Borrar |
| cancel | Cancel | Cancelar |
| object_stone | Stone | Piedra |
| object_fern | Fern | Helecho |
| object_heliconia | Heliconia | Heliconia |
| object_pond | Pond | Estanque |
| garden_full | Your garden is full (30 objects) | Tu jardín está lleno (30 objetos) |
| remove | Remove | Quitar |
| share_title | Share your garden | Comparte tu jardín |

- [ ] **Step 3: Smoke test**

```kotlin
class SmokeTest { @Test fun unitTestsRun() = assertEquals(4, 2 + 2) }
```
(Replaced by real tests from Task 2; it only proves the unit-test task runs.)

- [ ] **Step 4: Verify**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Install on the `Pixel_API_36` emulator (`./gradlew installDebug`), launch: a full red-ochre screen, portrait.

- [ ] **Step 5: Commit**

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat app
git commit -m "chore: scaffold Tierra Colorada - Zen Compose app"
```

---

### Task 2: Height map

**Files:**
- Create: `sand/HeightMap.kt`
- Test: `test/.../sand/HeightMapTest.kt` (delete `SmokeTest.kt`)

**Interfaces:**
- Produces:
  - `data class CellRect(val left: Int, val top: Int, val right: Int, val bottom: Int)` — right/bottom exclusive; `fun union(other: CellRect): CellRect`.
  - `class HeightMap(val width: Int, val height: Int, val values: FloatArray = FloatArray(width * height))`
    - `operator fun get(x: Int, y: Int): Float` — returns `0f` out of bounds
    - `operator fun set(x: Int, y: Int, value: Float)` — clamps to `[-1,1]`, ignores out of bounds, grows the dirty rect
    - `fun inBounds(x: Int, y: Int): Boolean`
    - `fun takeDirty(): CellRect?` — returns the dirty rect since last call and resets it
    - `fun markAllDirty()`
    - `fun resampledTo(newWidth: Int, newHeight: Int): HeightMap` — bilinear
    - `companion object { const val GRID_WIDTH = 360; fun forScreen(widthPx: Int, heightPx: Int): HeightMap }` — height = `round(360 * heightPx / widthPx)`, min 1

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun forScreenUsesGridWidthAndAspect() { val m = HeightMap.forScreen(1080, 2400); assertEquals(360, m.width); assertEquals(800, m.height) }
@Test fun setClampsToUnitRange() { val m = HeightMap(10, 10); m[1, 1] = 5f; m[2, 2] = -5f; assertEquals(1f, m[1, 1]); assertEquals(-1f, m[2, 2]) }
@Test fun setOutOfBoundsIsIgnored() { val m = HeightMap(10, 10); m[-1, 0] = 1f; m[10, 5] = 1f; m[3, 99] = 1f; assertNull(m.takeDirty()); assertEquals(0f, m[-1, 0]) }
@Test fun dirtyRectCoversWritesAndResets() { val m = HeightMap(10, 10); m[2, 3] = .5f; m[6, 1] = .5f; assertEquals(CellRect(2, 1, 7, 4), m.takeDirty()); assertNull(m.takeDirty()) }
@Test fun markAllDirtyCoversGrid() { val m = HeightMap(10, 8); m.markAllDirty(); assertEquals(CellRect(0, 0, 10, 8), m.takeDirty()) }
@Test fun resamplePreservesValuesAtCorners() { val m = HeightMap(4, 4); m[0, 0] = 1f; m[3, 3] = -1f; val r = m.resampledTo(8, 8); assertEquals(1f, r[0, 0], 1e-4f); assertEquals(-1f, r[7, 7], 1e-4f); assertEquals(8, r.width) }
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testDebugUnitTest --tests '*HeightMapTest'` → compilation errors (`HeightMap` undefined).
- [ ] **Step 3: Implement `HeightMap` and `CellRect`** per Interfaces. Resample maps corner cell centres to corner cell centres (`srcX = x * (width-1) / (newWidth-1)`).
- [ ] **Step 4: Run, expect PASS** — same command.
- [ ] **Step 5: Commit** — `git add app/src && git commit -m "feat(sand): height map with dirty tracking and resampling"`

---

### Task 3: Rake

**Files:**
- Create: `sand/Rake.kt`
- Test: `test/.../sand/RakeTest.kt`

**Interfaces:**
- Consumes: `HeightMap` (Task 2).
- Produces:
  - `class Rake(private val map: HeightMap, private val isBlocked: (Int, Int) -> Boolean = { _, _ -> false })`
    - `fun beginStroke(x: Float, y: Float)` — coordinates in cells; stamps once at the start point
    - `fun moveTo(x: Float, y: Float): Float` — returns cells travelled (after smoothing) for haptics/sound
    - `fun endStroke()`
    - `companion object { const val TINES = 4; const val TINE_SPACING = 8f; const val TINE_RADIUS = 2f; const val GROOVE_DEPTH = -0.8f; const val RIDGE_HEIGHT = 0.3f }`

Algorithm (not determined by the tests alone):
- **Smoothing:** keep the last 3 raw points of the current stroke; the working position is their average.
- **Direction:** unit vector from previous working position to the new one; if its length < 0.01 cells keep the previous direction. Initial direction `(1, 0)`.
- **Gap fill:** walk from the previous to the new working position in steps of 1 cell; stamp at each step.
- **Stamp:** tine offsets along the perpendicular `(-dy, dx)` at `(i - 1.5) * TINE_SPACING`, `i = 0..3`. For every cell within `2 * TINE_RADIUS` of a tine centre (distance `d`, R = `TINE_RADIUS`):
  - `d ≤ R`: height = `GROOVE_DEPTH * cos(π d / (2R))`, and remember the cell in this stroke's trough set (`BooleanArray` sized to the grid, cleared on `beginStroke`).
  - `R < d ≤ 2R`: height = `RIDGE_HEIGHT * sin(π (d − R) / R)` **only if** the cell is not in this stroke's trough set.
  - Skip cells where `isBlocked(x, y)`.
- `beginStroke` clears the smoothing buffer, so a new stroke never connects to the previous one.

- [ ] **Step 1: Write the failing tests**

```kotlin
private fun map() = HeightMap(100, 100)
@Test fun singleStampCarvesFourTroughsAcrossVerticalLine() {
    val m = map(); Rake(m).apply { beginStroke(50f, 50f); endStroke() }
    // initial direction (1,0) → tines spread along y at 50 ± 4, 50 ± 12
    listOf(38, 46, 54, 62).forEach { y -> assertEquals(Rake.GROOVE_DEPTH, m[50, y], 1e-3f) }
}
@Test fun ridgeSitsBesideTrough() { val m = map(); Rake(m).apply { beginStroke(50f, 50f); endStroke() }; assertTrue(m[50, 65] > 0.2f) }  // d = 3 from tine at 62
@Test fun fastStrokeLeavesNoGaps() {
    val m = map(); Rake(m).apply { beginStroke(10f, 50f); moveTo(90f, 50f); moveTo(90f, 50f); moveTo(90f, 50f); endStroke() }
    for (x in 12..88) assertTrue("gap at $x", m[x, 46] < -0.5f)
}
@Test fun blockedCellsAreUntouched() { val m = map(); Rake(m) { x, _ -> x in 40..60 }.apply { beginStroke(10f, 50f); repeat(3) { moveTo(90f, 50f) }; endStroke() }; for (x in 40..60) assertEquals(0f, m[x, 46]) }
@Test fun newStrokeDoesNotConnectToPreviousStroke() {
    val m = map(); val r = Rake(m)
    r.beginStroke(10f, 20f); r.endStroke(); r.beginStroke(90f, 80f); r.endStroke()
    assertEquals(0f, m[50, 50])
}
@Test fun strokeOffGridDoesNotCrash() { val m = map(); Rake(m).apply { beginStroke(-30f, -30f); moveTo(130f, 130f); endStroke() } }
@Test fun stationaryFingerProducesNoNaN() { val m = map(); Rake(m).apply { beginStroke(50f, 50f); repeat(5) { moveTo(50f, 50f) }; endStroke() }; assertFalse(m.values.any { it.isNaN() }) }
@Test fun moveToReturnsDistanceTravelled() { val r = Rake(map()); r.beginStroke(10f, 10f); val d = (1..3).sumOf { r.moveTo(40f, 10f).toDouble() }; assertTrue(d > 15 && d <= 30.01) }
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testDebugUnitTest --tests '*RakeTest'`.
- [ ] **Step 3: Implement `Rake`** per the algorithm.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `git add app/src && git commit -m "feat(sand): multi-tine rake with smoothing and gap fill"`

---

### Task 4: Smooth, ripples, clear sweep, shading

**Files:**
- Create: `sand/SandTools.kt`, `sand/ClearSweep.kt`, `sand/SandShader.kt`
- Test: `test/.../sand/SandToolsTest.kt`, `ClearSweepTest.kt`, `SandShaderTest.kt`

**Interfaces:**
- Consumes: `HeightMap`, `CellRect`.
- Produces:
  - `object SandTools { const val SMOOTH_RADIUS = 12f; const val SMOOTH_FACTOR = 0.2f; const val RIPPLE_COUNT = 3; const val RIPPLE_GAP = 3f; const val RIPPLE_DEPTH = -0.6f`
    - `fun smooth(map: HeightMap, cx: Float, cy: Float, isBlocked: (Int, Int) -> Boolean = { _, _ -> false })` — every cell within `SMOOTH_RADIUS` moves `SMOOTH_FACTOR` of the way to 0
    - `fun stampRipples(map: HeightMap, cx: Float, cy: Float, objectRadius: Float, isBlocked: (Int, Int) -> Boolean = { _, _ -> false })` — rings at radii `objectRadius + RIPPLE_GAP * k`, `k = 1..3`; cells within 1 cell of a ring get `RIPPLE_DEPTH * cos(π·dist/2)`; others untouched `}`
  - `class ClearSweep(private val map: HeightMap) { fun advance(elapsedMs: Long): Boolean; companion object { const val DURATION_MS = 600L } }` — sets all columns `x < width * elapsed / DURATION_MS` to 0; returns `true` once elapsed ≥ duration (all cells 0)
  - `object SandShader { const val BASE_COLOR: Int = 0xFFB5523B.toInt(); fun shade(map: HeightMap, rect: CellRect, out: IntArray) }` — writes opaque ARGB into `out[y * map.width + x]` for cells in `rect`. Per cell: `lum = 1 + 0.15 * h + 0.8 * (h − h(x+1, y+1))`, clamped to `[0.55, 1.35]`; each RGB channel = `clamp(channel(BASE_COLOR) * lum, 0, 255)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// SandToolsTest
@Test fun smoothMovesTowardZero() { val m = HeightMap(50, 50); m[25, 25] = -1f; SandTools.smooth(m, 25f, 25f); assertEquals(-0.8f, m[25, 25], 1e-4f) }
@Test fun smoothLeavesFarCellsAlone() { val m = HeightMap(50, 50); m[0, 0] = -1f; SandTools.smooth(m, 40f, 40f); assertEquals(-1f, m[0, 0]) }
@Test fun ripplesFormThreeRings() { val m = HeightMap(100, 100); SandTools.stampRipples(m, 50f, 50f, 10f); listOf(63, 66, 69).forEach { assertEquals(SandTools.RIPPLE_DEPTH, m[it, 50], 1e-3f) }; assertEquals(0f, m[50, 50]) }
// ClearSweepTest
@Test fun halfwayClearsLeftHalf() { val m = HeightMap(10, 4).apply { values.fill(-0.5f) }; assertFalse(ClearSweep(m).advance(300)); assertEquals(0f, m[4, 2]); assertEquals(-0.5f, m[5, 2]) }
@Test fun finishedClearsEverything() { val m = HeightMap(10, 4).apply { values.fill(-0.5f) }; assertTrue(ClearSweep(m).advance(600)); assertTrue(m.values.all { it == 0f }) }
// SandShaderTest
@Test fun flatSandIsBaseColor() { val m = HeightMap(3, 3); val out = IntArray(9); SandShader.shade(m, CellRect(0, 0, 3, 3), out); assertEquals(SandShader.BASE_COLOR, out[4]) }
@Test fun ridgeFacingLightIsBrighter() { val m = HeightMap(3, 3); m[1, 1] = 0.5f; val out = IntArray(9); SandShader.shade(m, CellRect(0, 0, 3, 3), out); assertTrue(red(out[4]) > red(SandShader.BASE_COLOR)) }
@Test fun grooveIsDarker() { val m = HeightMap(3, 3); m[1, 1] = -0.5f; val out = IntArray(9); SandShader.shade(m, CellRect(0, 0, 3, 3), out); assertTrue(red(out[4]) < red(SandShader.BASE_COLOR)) }
@Test fun onlyRectIsWritten() { val m = HeightMap(3, 3); val out = IntArray(9); SandShader.shade(m, CellRect(0, 0, 1, 1), out); assertEquals(0, out[8]) }
private fun red(c: Int) = (c shr 16) and 0xFF
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testDebugUnitTest --tests '*.sand.*'`.
- [ ] **Step 3: Implement** the three files per Interfaces.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `git add app/src && git commit -m "feat(sand): smoothing, stone ripples, clear sweep and shading"`

---

### Task 5: Garden objects model and placement rules

**Files:**
- Create: `objects/GardenObject.kt`, `objects/GardenObjects.kt`, `objects/ObjectShapes.kt`
- Test: `test/.../objects/GardenObjectsTest.kt`, `ObjectShapesTest.kt`

**Interfaces:**
- Produces:
  - `enum class ObjectType(val baseRadius: Float) { STONE(14f), FERN(22f), HELICONIA(16f), POND(40f) }` (cells)
  - `data class GardenObject(val id: Long, val type: ObjectType, val x: Float, val y: Float, val scale: Float = 1f, val rotation: Float = 0f, val seed: Int) { val radius: Float; fun covers(cx: Float, cy: Float): Boolean }` — circle footprint `radius = baseRadius * scale`
  - `class GardenObjects(val gridWidth: Int, val gridHeight: Int, initial: List<GardenObject> = emptyList())`
    - `val items: List<GardenObject>`
    - `fun canAdd(type: ObjectType): Boolean` — false at 30 items, or for POND when a pond exists
    - `fun add(type: ObjectType, x: Float, y: Float, seed: Int): GardenObject?` — null when `!canAdd`; result already resolved (see below)
    - `fun move(id: Long, x: Float, y: Float): GardenObject?`
    - `fun transform(id: Long, scale: Float, rotation: Float): GardenObject?` — scale clamped to `[0.5, 2.0]`
    - `fun remove(id: Long)`
    - `fun isBlocked(cellX: Int, cellY: Int): Boolean` — any footprint covers the cell centre
    - `fun pond(): GardenObject?`
    - `companion object { const val MAX_OBJECTS = 30; const val MIN_SCALE = 0.5f; const val MAX_SCALE = 2.0f }`
  - `ObjectShapes.stoneOutline(seed: Int, radius: Float, points: Int = 24): List<Pair<Float, Float>>` — polar points with radius varied ±15 % by a `Random(seed)`; `ObjectShapes.fernFronds(seed: Int): List<Float>` — 7 frond angles (radians) with ±0.15 jitter.

Placement resolution (applied by `add`, `move`, `transform`):
1. Clamp the centre into `[0, gridWidth] × [0, gridHeight]`.
2. If a non-pond object overlaps the pond (centre distance < pondRadius + objRadius): push it along the pond-centre→object vector (use `(1, 0)` if centres coincide) to distance `pondRadius + objRadius + 1`, then clamp. If still overlapping after clamping, push in the opposite direction and clamp.
3. When the pond itself is added/moved/resized, apply step 2 to every other object.

- [ ] **Step 1: Write the failing tests**

```kotlin
private fun garden() = GardenObjects(360, 800)
@Test fun limitIsThirty() { val g = garden(); repeat(30) { assertNotNull(g.add(ObjectType.STONE, 10f + it * 10, 10f, it)) }; assertNull(g.add(ObjectType.FERN, 100f, 400f, 0)); assertFalse(g.canAdd(ObjectType.STONE)) }
@Test fun onlyOnePond() { val g = garden(); g.add(ObjectType.POND, 180f, 400f, 1); assertFalse(g.canAdd(ObjectType.POND)); assertTrue(g.canAdd(ObjectType.STONE)) }
@Test fun stoneDroppedOnPondMovesOutside() { val g = garden(); val p = g.add(ObjectType.POND, 180f, 400f, 1)!!; val s = g.add(ObjectType.STONE, 185f, 400f, 2)!!; assertTrue(hypot(s.x - p.x, s.y - p.y) >= p.radius + s.radius) }
@Test fun pondMovedOntoStonePushesStone() { val g = garden(); val s = g.add(ObjectType.STONE, 100f, 100f, 2)!!; g.add(ObjectType.POND, 300f, 600f, 1)!!.let { g.move(it.id, 100f, 100f) }; val s2 = g.items.first { it.id == s.id }; val p = g.pond()!!; assertTrue(hypot(s2.x - p.x, s2.y - p.y) >= p.radius + s2.radius) }
@Test fun objectsMayOverlapEachOther() { val g = garden(); g.add(ObjectType.STONE, 50f, 50f, 1); val f = g.add(ObjectType.FERN, 52f, 50f, 2)!!; assertEquals(52f, f.x) }
@Test fun dropOutsideGardenIsClamped() { val g = garden(); val s = g.add(ObjectType.STONE, -20f, 900f, 1)!!; assertEquals(0f, s.x); assertEquals(800f, s.y) }
@Test fun pondPushNearEdgeStaysInBoundsAndOutsidePond() { val g = garden(); val p = g.add(ObjectType.POND, 20f, 400f, 1)!!; val s = g.add(ObjectType.STONE, 5f, 400f, 2)!!; assertTrue(s.x in 0f..360f); assertTrue(hypot(s.x - p.x, s.y - p.y) >= p.radius + s.radius) }
@Test fun scaleIsClamped() { val g = garden(); val s = g.add(ObjectType.STONE, 50f, 50f, 1)!!; assertEquals(2f, g.transform(s.id, 9f, 0f)!!.scale) }
@Test fun isBlockedUnderFootprint() { val g = garden(); g.add(ObjectType.STONE, 50f, 50f, 1); assertTrue(g.isBlocked(50, 50)); assertFalse(g.isBlocked(80, 50)) }
// ObjectShapesTest
@Test fun stoneOutlineIsDeterministic() = assertEquals(ObjectShapes.stoneOutline(7, 10f), ObjectShapes.stoneOutline(7, 10f))
@Test fun stoneOutlineVariesBySeed() = assertNotEquals(ObjectShapes.stoneOutline(7, 10f), ObjectShapes.stoneOutline(8, 10f))
@Test fun stoneRadiusWithinFifteenPercent() { ObjectShapes.stoneOutline(3, 10f).forEach { (x, y) -> assertTrue(hypot(x, y) in 8.5f..11.5f) } }
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testDebugUnitTest --tests '*.objects.*'`.
- [ ] **Step 3: Implement** per Interfaces and the resolution rules. Ids: `System.nanoTime()`-independent counter starting at `max(existing ids) + 1`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `git add app/src && git commit -m "feat(objects): garden object model, placement rules and seeded shapes"`

---

### Task 6: Life timing (pure)

**Files:**
- Create: `life/LifeDirector.kt`, `life/PondRipples.kt`
- Test: `test/.../life/LifeDirectorTest.kt`, `PondRipplesTest.kt`

**Interfaces:**
- Consumes: `GardenObject`, `ObjectType`.
- Produces:
  - `data class HummingbirdVisit(val target: GardenObject, val startMs: Long, val hoverMs: Long) { fun phase(nowMs: Long): VisitPhase }` with `enum class VisitPhase { FLYING_IN, HOVERING, LEAVING, DONE }` — fly-in 1500 ms, hover `hoverMs` (3000–5000), leave 1000 ms.
  - `data class DriftingLeaf(val startMs: Long, val fromX: Float, val fromY: Float, val toX: Float, val toY: Float, val seed: Int)` — drifts 4000 ms, rests and fades 5000 ms.
  - `data class LifeState(val hummingbird: HummingbirdVisit?, val leaves: List<DriftingLeaf>, val shadowPhase: Float, val showShadows: Boolean)`
  - `class LifeDirector(private val random: Random, private val gridWidth: Int, private val gridHeight: Int) { fun update(nowMs: Long, objects: List<GardenObject>, reducedMotion: Boolean): LifeState }`
    - Next hummingbird visit scheduled 30 000–90 000 ms after the previous visit ended (or after the first `update` call); only starts if a HELICONIA exists then (target chosen at random); if none, reschedules.
    - Next leaf 20 000–60 000 ms after the previous leaf started; leaves older than 9000 ms are dropped.
    - `shadowPhase = (nowMs % 60_000) / 60_000f`.
    - `showShadows = !reducedMotion`.
    - `reducedMotion = true` → no hummingbird, no leaves, `shadowPhase = 0f`, `showShadows = false`.
  - `class PondRipples { fun tap(x: Float, y: Float, nowMs: Long); fun active(nowMs: Long): List<Ripple> }`, `data class Ripple(val x: Float, val y: Float, val ageMs: Long)` — ripples live 2000 ms.

- [ ] **Step 1: Write the failing tests**

```kotlin
private val heliconia = GardenObject(1, ObjectType.HELICONIA, 100f, 100f, seed = 1)
private fun runFor(d: LifeDirector, ms: Long, objs: List<GardenObject>, reduced: Boolean = false) = (0..ms step 100).map { d.update(it, objs, reduced) }
@Test fun noHeliconiaNoHummingbird() { val states = runFor(LifeDirector(Random(1), 360, 800), 600_000, emptyList()); assertTrue(states.all { it.hummingbird == null }) }
@Test fun firstVisitStartsWithin30To90Seconds() { val states = runFor(LifeDirector(Random(2), 360, 800), 120_000, listOf(heliconia)); val first = states.first { it.hummingbird != null }.hummingbird!!; assertTrue(first.startMs in 30_000..90_000); assertEquals(heliconia, first.target) }
@Test fun hoverLastsThreeToFiveSeconds() { val v = runFor(LifeDirector(Random(3), 360, 800), 120_000, listOf(heliconia)).first { it.hummingbird != null }.hummingbird!!; assertTrue(v.hoverMs in 3000..5000) }
@Test fun leavesAppearAndExpire() { val states = runFor(LifeDirector(Random(4), 360, 800), 200_000, emptyList()); assertTrue(states.any { it.leaves.isNotEmpty() }); states.forEachIndexed { i, s -> s.leaves.forEach { assertTrue(i * 100L - it.startMs <= 9000) } } }
@Test fun reducedMotionStopsEverything() { val states = runFor(LifeDirector(Random(5), 360, 800), 300_000, listOf(heliconia), reduced = true); assertTrue(states.all { it.hummingbird == null && it.leaves.isEmpty() && it.shadowPhase == 0f && !it.showShadows }) }
@Test fun rippleExpiresAfterTwoSeconds() { val p = PondRipples(); p.tap(1f, 1f, 0); assertEquals(1, p.active(1999).size); assertEquals(0, p.active(2000).size) }
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testDebugUnitTest --tests '*.life.*'`.
- [ ] **Step 3: Implement** per Interfaces.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `git add app/src && git commit -m "feat(life): hummingbird, leaf and ripple timing"`

---

### Task 7: Save and load

**Files:**
- Create: `storage/GardenStore.kt`
- Test: `test/.../storage/GardenStoreTest.kt`

**Interfaces:**
- Consumes: `HeightMap`, `GardenObject`, `ObjectType`.
- Produces:
  - `data class GardenSnapshot(val heights: HeightMap, val objects: List<GardenObject>, val muted: Boolean)`
  - `class GardenStore(private val dir: File) { fun save(snapshot: GardenSnapshot); fun load(gridWidth: Int, gridHeight: Int): GardenSnapshot? }`

Format (`garden.bin`, all via `DataOutputStream`): int magic `0x54435A31` ("TCZ1"), int version `1`, int width, int height, int byteCount + `Deflater`-compressed float bytes of `values`, int objectCount, per object `long id, int typeOrdinal, float x, y, scale, rotation, int seed`, boolean muted.
- `save` writes `garden.bin.tmp`, flushes + `fd.sync()`, then `Files.move(tmp, real, ATOMIC_MOVE, REPLACE_EXISTING)`.
- `load` returns `null` for missing file, wrong magic, unknown version, any `IOException`/`EOFException`/`DataFormatException`/invalid ordinal; logs with `Log.w("GardenStore", …)`.
- If saved size ≠ requested size: heights `resampledTo(gridWidth, gridHeight)`; each object's `y *= gridHeight / savedHeight` and `x *= gridWidth / savedWidth`.

- [ ] **Step 1: Write the failing tests** (use `TemporaryFolder` rule)

```kotlin
@Test fun roundTrip() { val m = HeightMap(360, 800).apply { this[5, 5] = -0.7f }; val o = GardenObject(3, ObjectType.FERN, 10f, 20f, 1.5f, 0.3f, 99); store.save(GardenSnapshot(m, listOf(o), muted = true)); val s = store.load(360, 800)!!; assertEquals(-0.7f, s.heights[5, 5]); assertEquals(listOf(o), s.objects); assertTrue(s.muted) }
@Test fun missingFileLoadsAsNull() = assertNull(store.load(360, 800))
@Test fun truncatedFileLoadsAsNull() { store.save(GardenSnapshot(HeightMap(360, 800), emptyList(), false)); val f = File(dir, "garden.bin"); f.writeBytes(f.readBytes().copyOf(20)); assertNull(store.load(360, 800)) }
@Test fun garbageFileLoadsAsNull() { File(dir, "garden.bin").writeText("hello"); assertNull(store.load(360, 800)) }
@Test fun unknownVersionLoadsAsNull() { DataOutputStream(File(dir, "garden.bin").outputStream()).use { it.writeInt(0x54435A31); it.writeInt(99) }; assertNull(store.load(360, 800)) }
@Test fun differentAspectIsResampled() { store.save(GardenSnapshot(HeightMap(360, 800), listOf(GardenObject(1, ObjectType.STONE, 100f, 400f, seed = 1)), false)); val s = store.load(360, 720)!!; assertEquals(720, s.heights.height); assertEquals(360f, s.objects[0].y, 1e-3f) }
@Test fun noTempFileLeftBehind() { store.save(GardenSnapshot(HeightMap(10, 10), emptyList(), false)); assertFalse(File(dir, "garden.bin.tmp").exists()) }
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testDebugUnitTest --tests '*.storage.*'`.
- [ ] **Step 3: Implement `GardenStore`.**
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `git add app/src && git commit -m "feat(storage): atomic garden save and tolerant load"`

---

### Task 8: ViewModel, garden screen, rake/smooth/clear, toolbar

**Files:**
- Create: `ui/GardenViewModel.kt`, `ui/GardenScreen.kt`, `ui/Toolbar.kt`
- Modify: `MainActivity.kt` (show `GardenScreen`)
- Test: `test/.../ui/GardenViewModelTest.kt`, `androidTest/.../ui/GardenScreenTest.kt`

**Interfaces:**
- Consumes: Tasks 2–7.
- Produces:
  - `enum class Tool { RAKE, PLACE, SMOOTH }`
  - `sealed interface GardenEvent { data object StonePlaced; data object WaterTap; data object Sweep; data object HapticTick; data class RakeSpeed(val cellsPerSecond: Float) }`
  - `class GardenViewModel(private val store: GardenStore) : ViewModel()`
    - state (Compose `mutableStateOf`, read-only getters): `tool: Tool`, `objects: List<GardenObject>`, `muted: Boolean`, `confirmingClear: Boolean`, `sandFrame: Int` (incremented whenever sand changes, to trigger redraw), `heights: HeightMap?` (null until `initialize`)
    - `val events: SharedFlow<GardenEvent>` (replay 0, extraBufferCapacity 16)
    - `fun initialize(gridWidth: Int, gridHeight: Int)` — loads snapshot or creates empty; idempotent
    - `fun selectTool(tool: Tool)`
    - `fun strokeStart(x: Float, y: Float)`, `fun strokeMove(x: Float, y: Float, dtMs: Long)`, `fun strokeEnd()` — routes to `Rake` (RAKE) or `SandTools.smooth` (SMOOTH); emits `HapticTick` every 24 cells of rake travel, `RakeSpeed` on each move and `RakeSpeed(0f)` on end
    - `fun tap(x: Float, y: Float)` — if on the pond: `PondRipples.tap` via a `pondRipples` property and emit `WaterTap`
    - `fun requestClear()`, `fun cancelClear()`, `fun confirmClear()` — confirm starts a `ClearSweep` driven by `fun advanceClear(elapsedMs: Long)`; emits `Sweep` at start
    - `fun toggleMute()`
    - `fun saveNow()` — saves if there are unsaved changes; autosave every 30 000 ms in `viewModelScope` while dirty
    - `companion object { fun factory(filesDir: File): ViewModelProvider.Factory }`
  - Test tags: `"garden"`, `"tool_rake"`, `"tool_place"`, `"tool_smooth"`, `"tool_clear"`, `"mute"`, `"share"`, `"clear_dialog"`; active tool button has `Modifier.semantics { selected = true }`.

Screen notes:
- `GardenScreen` measures the garden with `BoxWithConstraints`; calls `initialize` with `HeightMap.forScreen(widthPx, heightPx)` size; `cellSize = widthPx / 360f`.
- Sand bitmap: `ImageBitmap(gridWidth, gridHeight)` + `IntArray` pixel buffer kept in `remember`; on each `sandFrame` change call `heights.takeDirty()` → `SandShader.shade` → `asAndroidBitmap().setPixels(...)` for that rect; draw with `drawImage(…, dstSize = size, filterQuality = FilterQuality.Low)`.
- Gestures (`pointerInput`): in RAKE/SMOOTH track only the first pointer id (ignore others until it lifts); a press-and-release under 10 dp of movement is a `tap`.
- Toolbar: floating pill at bottom, `windowInsetsPadding(WindowInsets.safeDrawing)`; buttons Rake, Place, Smooth, Clear all, Mute/Sound on, Share (Share wired in Task 11; until then disabled). Icons: Material symbols drawn as simple vector paths or text labels — keep to `Icon` + `contentDescription` from strings.
- Clear dialog: `AlertDialog` with `clear_title`, `clear_message`, `clear_confirm`, `cancel`; sweep animation driven by `withFrameMillis`.
- Collect `events` in the screen; Task 10 connects audio/haptics (until then only `HapticTick` → `view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)`).
- `MainActivity`: `onStop` → `viewModel.saveNow()`.

- [ ] **Step 1: Write failing ViewModel unit tests** (use `kotlinx-coroutines-test` `runTest` + `Dispatchers.setMain`; add `org.jetbrains.kotlinx:kotlinx-coroutines-test` to the catalog matching the coroutines version Compose BOM pulls in)

```kotlin
@Test fun rakeStrokeChangesSandAndBumpsFrame() { vm.initialize(360, 800); val f = vm.sandFrame; vm.strokeStart(50f, 50f); vm.strokeMove(90f, 50f, 16); vm.strokeEnd(); assertTrue(vm.sandFrame > f); assertTrue(vm.heights!!.values.any { it < -0.5f }) }
@Test fun smoothToolDoesNotCarve() { vm.initialize(360, 800); vm.selectTool(Tool.SMOOTH); vm.strokeStart(50f, 50f); vm.strokeMove(90f, 50f, 16); vm.strokeEnd(); assertTrue(vm.heights!!.values.all { it == 0f }) }
@Test fun clearNeedsConfirmation() { vm.initialize(360, 800); vm.strokeStart(50f, 50f); vm.strokeEnd(); vm.requestClear(); assertTrue(vm.confirmingClear); vm.cancelClear(); assertFalse(vm.heights!!.values.all { it == 0f }) }
@Test fun confirmedClearFlattensAfterSweep() { vm.initialize(360, 800); vm.strokeStart(50f, 50f); vm.strokeEnd(); vm.requestClear(); vm.confirmClear(); vm.advanceClear(600); assertTrue(vm.heights!!.values.all { it == 0f }) }
@Test fun muteIsSavedAndRestored() { vm.initialize(360, 800); vm.toggleMute(); vm.saveNow(); val vm2 = GardenViewModel(GardenStore(dir)); vm2.initialize(360, 800); assertTrue(vm2.muted) }
@Test fun hapticTickEvery24Cells() = runTest { vm.initialize(360, 800); val ticks = mutableListOf<GardenEvent>(); val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { if (it == GardenEvent.HapticTick) ticks += it } }; vm.strokeStart(10f, 100f); repeat(10) { vm.strokeMove(10f + (it + 1) * 10f, 100f, 16) }; vm.strokeEnd(); job.cancel(); assertTrue(ticks.size in 2..4) }
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testDebugUnitTest --tests '*.ui.*'`.
- [ ] **Step 3: Implement `GardenViewModel`.**
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Write failing Compose UI tests** (`createAndroidComposeRule<MainActivity>()`)

```kotlin
@Test fun rakeIsSelectedByDefault() { rule.onNodeWithTag("tool_rake").assertIsSelected() }
@Test fun selectingSmoothHighlightsIt() { rule.onNodeWithTag("tool_smooth").performClick().assertIsSelected(); rule.onNodeWithTag("tool_rake").assertIsNotSelected() }
@Test fun clearAsksForConfirmation() { rule.onNodeWithTag("tool_clear").performClick(); rule.onNodeWithTag("clear_dialog").assertIsDisplayed() }
```

- [ ] **Step 6: Implement `GardenScreen` and `Toolbar`; wire into `MainActivity`.**
- [ ] **Step 7: Run UI tests on the emulator, expect PASS** — `./gradlew connectedDebugAndroidTest` (emulator `Pixel_API_36` running).
- [ ] **Step 8: Manual check** — `./gradlew installDebug`, rake curves: grooves follow the finger with 4 parallel lines; smooth softens them; clear sweeps left→right after confirming; leave and reopen app → garden restored.
- [ ] **Step 9: Commit** — `git add app && git commit -m "feat(ui): garden screen with rake, smooth, clear and autosave"`

---

### Task 9: Objects on screen — drawing, tray, move, pinch, trash

**Files:**
- Create: `objects/ObjectDrawing.kt`, `ui/ObjectTray.kt`
- Modify: `ui/GardenViewModel.kt`, `ui/GardenScreen.kt`, `ui/Toolbar.kt`
- Test: `test/.../ui/GardenViewModelObjectsTest.kt`, extend `androidTest/.../ui/GardenScreenTest.kt`

**Interfaces:**
- Consumes: `GardenObjects`, `ObjectShapes`, `SandTools.stampRipples`.
- Produces:
  - `fun DrawScope.drawGardenObject(obj: GardenObject, cellSize: Float)` — drawn at `(x, y) * cellSize`, rotated by `rotation` (radians), using colours from `Theme.kt`: STONE = `stoneOutline` path with radial gradient grey `0xFF8A8F87`→`0xFF4E524C` and moss patch `LeafLight` at 40 % alpha when `seed % 3 == 0`; FERN = 7 fronds (`fernFronds`) each a line of 10 leaflet ovals tapering outward, `LeafDark`/`LeafLight`; HELICONIA = `LeafDark` stem + 6 alternating triangular bracts `HeliconiaRed` with `HeliconiaYellow` tips; POND = blob outline from `stoneOutline(seed, radius)` filled `PondTeal`, lighter rim, one lily pad (`LeafLight` circle with a wedge cut).
  - ViewModel additions: `fun canAdd(type: ObjectType): Boolean`, `fun addObject(type: ObjectType, x: Float, y: Float)` (random seed; STONE → `stampRipples` + `StonePlaced`), `fun moveObject(id: Long, x: Float, y: Float)`, `fun endMoveObject(id: Long)` (STONE → ripples + `StonePlaced`), `fun transformObject(id: Long, scale: Float, rotation: Float)`, `fun deleteObject(id: Long)`, `val gardenFull: Boolean`. Rake/smooth use `GardenObjects.isBlocked`.
  - Tags: `"tray"`, `"tray_stone"`, `"tray_fern"`, `"tray_heliconia"`, `"tray_pond"`, `"trash"`.

UI behaviour: PLACE tool shows the tray above the toolbar; drag from a tray item creates the object where the finger is released (inside the garden). In PLACE mode: drag a placed object to move it (topmost under the finger wins); two-finger pinch/rotate on it calls `transformObject`; while dragging, a trash zone (`remove` string) shows at the bottom and releasing over it deletes. Tray items disabled when `!canAdd(type)`; when full, show `garden_full` text in the tray.

- [ ] **Step 1: Failing ViewModel tests**

```kotlin
@Test fun placingStoneStampsRipples() { vm.initialize(360, 800); vm.addObject(ObjectType.STONE, 180f, 400f); assertTrue(vm.heights!![180 + 14 + 3, 400] < -0.5f) }
@Test fun rakeSkipsObjects() { vm.initialize(360, 800); vm.addObject(ObjectType.FERN, 100f, 100f); vm.strokeStart(60f, 100f); vm.strokeMove(140f, 100f, 16); repeat(3) { vm.strokeMove(140f, 100f, 16) }; vm.strokeEnd(); assertEquals(0f, vm.heights!![100, 96]) }
@Test fun objectsSurviveSaveAndLoad() { vm.initialize(360, 800); vm.addObject(ObjectType.HELICONIA, 50f, 60f); vm.saveNow(); val vm2 = GardenViewModel(GardenStore(dir)); vm2.initialize(360, 800); assertEquals(ObjectType.HELICONIA, vm2.objects.single().type) }
```

- [ ] **Step 2: Run, expect FAIL.** — `./gradlew testDebugUnitTest --tests '*GardenViewModelObjectsTest'`
- [ ] **Step 3: Implement ViewModel additions.**
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Failing UI test**

```kotlin
@Test fun pondTrayItemDisabledWhenPondExists() {
    rule.onNodeWithTag("tool_place").performClick()
    rule.runOnUiThread { rule.activity.viewModel.addObject(ObjectType.POND, 180f, 400f) }
    rule.onNodeWithTag("tray_pond").assertIsNotEnabled()
}
```
(Expose `val viewModel` on `MainActivity` as `internal` for this test.)

- [ ] **Step 6: Implement `ObjectDrawing.kt`, `ObjectTray.kt`, gestures and trash zone.**
- [ ] **Step 7: Run UI tests, expect PASS** — `./gradlew connectedDebugAndroidTest`.
- [ ] **Step 8: Manual check** — place each type; stones leave rings; rake lines stop at objects; pinch resizes/rotates; drag to trash removes; a stone dropped on the pond slides off it.
- [ ] **Step 9: Commit** — `git add app && git commit -m "feat(objects): draw, place, move, transform and remove garden objects"`

---

### Task 10: Living details, audio and sounds

**Files:**
- Create: `life/LifeDrawing.kt`, `audio/GardenAudio.kt`, `tools/gen_sounds.py`, `CREDITS.md`, `app/src/main/res/raw/{ambient_rainforest,rake_scrape,stone_place,water_tap,clear_sweep}.wav`
- Modify: `ui/GardenScreen.kt`, `MainActivity.kt`
- Test: `test/.../audio/ScrapeVolumeTest.kt`

**Interfaces:**
- Consumes: `LifeDirector`, `PondRipples`, `GardenEvent`.
- Produces:
  - `fun DrawScope.drawLife(state: LifeState, ripples: List<Ripple>, nowMs: Long, cellSize: Float)` — canopy shadows (only when `showShadows`): 5 soft ellipses of `Color.Black` at 12 % alpha, offset by `shadowPhase` across the screen (wrapping); hummingbird: small body (`0xFF2E8B57` iridescent green) + two wing ellipses at 30 % alpha flickering each frame, position interpolated off-screen → `target` → off-screen per `phase`; leaves: `LeafLight` leaf shape moving `from → to` over 4 s with gentle sine sway, then fading over 5 s; ripples: rings radius `4 + ageMs/2000 * 20` cells, alpha falling to 0. Pond background ripples: two slow concentric rings always on (static when reduced motion).
  - `fun scrapeVolume(cellsPerSecond: Float): Float = (cellsPerSecond / 120f).coerceIn(0f, 1f) * 0.6f` (top level in `GardenAudio.kt`)
  - `class GardenAudio(context: Context) { fun resume(); fun pause(); fun setMuted(muted: Boolean); fun setRakeSpeed(cellsPerSecond: Float); fun play(effect: SoundEffect); fun release() }`, `enum class SoundEffect { STONE, WATER, SWEEP }`
    - `MediaPlayer` looping `ambient_rainforest` at volume 0.35; `SoundPool` (max 4 streams) for effects and a looping `rake_scrape` stream whose volume = `scrapeVolume` (0 → paused).
    - Audio focus via `AudioFocusRequest` (`AUDIOFOCUS_GAIN`, usage `USAGE_GAME`); on loss → pause; on gain → resume if not muted.

Sound generation (`tools/gen_sounds.py`, Python 3 stdlib only: `wave`, `struct`, `math`, `random`; 22 050 Hz mono 16-bit; deterministic `random.seed(7)`):
- `ambient_rainforest.wav` (30 s, seamless loop via 1 s crossfade): low-pass filtered noise (rain), plus 12 random bird chirps (short sine sweeps 2–5 kHz with vibrato), plus a low rumble (distant water).
- `rake_scrape.wav` (2 s loop): band-passed noise with slow amplitude wobble.
- `stone_place.wav` (0.4 s): 120 Hz decaying sine + short noise click.
- `water_tap.wav` (0.6 s): "plip" sine sweep 1200→400 Hz with exponential decay.
- `clear_sweep.wav` (0.7 s): noise with rising-then-falling envelope.
`CREDITS.md`: states every sound is generated by `tools/gen_sounds.py` in this repo and released CC0; lists the five files. (Real field recordings may replace them later; each replacement gets a CREDITS line with source URL and licence.)

- [ ] **Step 1: Failing test**

```kotlin
@Test fun silentWhenStill() = assertEquals(0f, scrapeVolume(0f))
@Test fun cappedAtSixtyPercent() = assertEquals(0.6f, scrapeVolume(500f), 1e-6f)
@Test fun halfSpeedHalfVolume() = assertEquals(0.3f, scrapeVolume(60f), 1e-6f)
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testDebugUnitTest --tests '*ScrapeVolumeTest'`.
- [ ] **Step 3: Implement `GardenAudio.kt`.**
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Generate sounds** — `python3 tools/gen_sounds.py app/src/main/res/raw`. Expected: five `.wav` files, total < 3 MB.
- [ ] **Step 6: Wire it up** — `MainActivity` owns `GardenAudio`: `onStart` → `resume()`, `onStop` → `pause()`, `onDestroy` → `release()`; `GardenScreen` maps events: `StonePlaced`→`STONE`, `WaterTap`→`WATER`, `Sweep`→`SWEEP`, `RakeSpeed`→`setRakeSpeed`; mute state → `setMuted`. Screen runs a `withFrameMillis` loop calling `LifeDirector.update` (reduced motion = `Settings.Global.getFloat(contentResolver, ANIMATOR_DURATION_SCALE, 1f) == 0f`) only while the lifecycle is at least STARTED (`LocalLifecycleOwner` + `repeatOnLifecycle`), and draws life above objects.
- [ ] **Step 7: Manual check** — ambient plays; rake scrape follows speed; mute silences and survives restart; hummingbird appears within 90 s when a heliconia exists; with Developer options › Animator duration scale = off, no hummingbird/leaves; home button pauses sound.
- [ ] **Step 8: Commit** — `git add app tools CREDITS.md && git commit -m "feat: living garden details and ambient audio"`

---

### Task 11: Share

**Files:**
- Create: `share/GardenSharer.kt`, `res/xml/file_paths.xml`
- Modify: `AndroidManifest.xml` (FileProvider), `ui/Toolbar.kt`, `ui/GardenScreen.kt`
- Test: `androidTest/.../share/GardenSharerTest.kt`

**Interfaces:**
- Consumes: `SandShader`, `drawGardenObject`, `drawLife`.
- Produces:
  - `object GardenSharer { fun render(widthPx: Int, heightPx: Int, sand: ImageBitmap, objects: List<GardenObject>, life: LifeState, ripples: List<Ripple>, nowMs: Long, watermark: String): ImageBitmap; fun share(context: Context, image: ImageBitmap) }`
    - `render` uses `CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(w, h)) { … }` with the same drawing functions as the screen, then draws `watermark` (= `app_name`) bottom-right with 16 px margin, white at 70 % alpha, text size `heightPx / 60`.
    - `share` writes PNG to `cacheDir/shared/garden.png`, gets a URI from `FileProvider` (authority `"${context.packageName}.fileprovider"`, path `<cache-path name="shared" path="shared/"/>`), starts `Intent.createChooser(ACTION_SEND type "image/png" + EXTRA_STREAM + FLAG_GRANT_READ_URI_PERMISSION, share_title)`.

- [ ] **Step 1: Failing instrumented test**

```kotlin
@Test fun renderHasRequestedSizeAndSandColor() {
    val sand = ImageBitmap(36, 80).also { b -> b.asAndroidBitmap().eraseColor(SandShader.BASE_COLOR) }
    val img = GardenSharer.render(360, 800, sand, emptyList(), LifeState(null, emptyList(), 0f, showShadows = false), emptyList(), 0, "Tierra Colorada - Zen")
    assertEquals(360, img.width); assertEquals(SandShader.BASE_COLOR, img.asAndroidBitmap().getPixel(10, 10))
}
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew connectedDebugAndroidTest`.
- [ ] **Step 3: Implement `GardenSharer`, manifest provider, enable the Share button.**
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Manual check** — Share opens the share sheet; image has no toolbar and shows the watermark.
- [ ] **Step 6: Commit** — `git add app && git commit -m "feat(share): share a picture of the garden"`

---

### Task 12: Performance check

**Files:**
- Create: `docs/performance.md` (results)

- [ ] **Step 1: Install the app** — `./gradlew installDebug` (debug builds are slower than release, so passing here means release passes too).
- [ ] **Step 2: Scripted fast rake** on the emulator:
```bash
adb shell dumpsys gfxinfo com.consuelomerino.tierracolorada reset
for i in $(seq 1 20); do adb shell input swipe 100 600 980 1800 150; adb shell input swipe 980 600 100 1800 150; done
adb shell dumpsys gfxinfo com.consuelomerino.tierracolorada | grep -E "Total frames|Janky frames|50th|90th"
```
Expected: 90th percentile ≤ 16 ms and janky frames < 5 %. If not met: profile with Android Studio's CPU profiler; the usual fixes are shading only the dirty rect (verify `takeDirty` usage) and avoiding allocations per frame.
- [ ] **Step 3: Record the numbers in `docs/performance.md` and commit** — `git add docs/performance.md && git commit -m "docs: record rake performance check"`

---

### Task 13: Release preparation

**Files:**
- Create: `docs/privacy-policy.md`, `README.md`
- Modify: `app/build.gradle.kts` (release `optimization { enable = true }` as in the template)

- [ ] **Step 1: Privacy policy** — `docs/privacy-policy.md` in English and Spanish: the app collects no personal data, makes no network connections, stores the garden only on the device, shares images only when the user chooses to; contact: the developer email shown on the Play listing. To be published via GitHub Pages later (the author's choice of repo visibility).
- [ ] **Step 2: README** — what the game is, screenshots placeholder section removed until real screenshots exist, how to build (`./gradlew assembleDebug`), how to run tests, link to spec and CREDITS.
- [ ] **Step 3: Verify release bundle builds** — `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` exists. Signing with the upload key is done by the author in Android Studio (**Build → Generate Signed App Bundle**); keystore files are git-ignored.
- [ ] **Step 4: Full test run** — `./gradlew testDebugUnitTest connectedDebugAndroidTest` → all PASS.
- [ ] **Step 5: Commit** — `git add docs/privacy-policy.md README.md app/build.gradle.kts && git commit -m "docs: privacy policy, README and release config"`

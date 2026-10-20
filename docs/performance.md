# Rake performance check

**Date:** 2026-10-20 · **Build:** debug · **Device:** `Pixel_API_36` emulator (Android 16, arm64), headless, host GPU (Apple M4 Pro via Metal), 60 Hz

The spec's target is smooth raking at 60 fps on a mid-range phone.

## Method

1. Fast swipes: 40 scripted 150 ms diagonal swipes (`adb shell input swipe`), then `dumpsys gfxinfo`.
2. Continuous stroke: one 120-step `input motionevent` stroke, then `dumpsys gfxinfo framestats`. The spacing between consecutive frames' `IntendedVsync` shows whether any frame missed its vsync.
3. Idle baseline: 10 s with no touch (only the ambient animation running).

## Results

| Scenario | Frames | Janky frames | Notes |
|---|---|---|---|
| Fast swipes | 442 | **0 (0.00 %)** | gfxinfo p50 24 ms / p90 26 ms (input-to-present latency, see below) |
| Continuous stroke | 120 | **0 missed vsyncs** | every frame-to-frame gap exactly 16.7 ms (max 16.7 ms) |
| Idle | 603 in 10 s (60 fps) | 0 (0.00 %) | gfxinfo p50 19 ms |

**Conclusion: passes.** While raking, every frame lands on its vsync, a steady 60 fps.

The gfxinfo percentiles (~20–26 ms) are above 16 ms because they measure the pipelined time from vsync to frame completion (UI thread plus RenderThread overlap), not the frame rate. Android's own "Janky frames" counter, which counts missed deadlines, reports 0.

With the emulator's default software renderer (SwiftShader), frames already took ~23 ms with no raking at all; raking added only ~2 ms. That renderer is not representative of a phone's GPU. Real-device numbers come from the closed testers.

## Re-check with 30 objects (after the final review)

The final code review pointed out that the always-animating life layer shared a graphics layer with the sand, so the sand and every object were redrawn every frame. A temporary counter confirmed it: the sand canvas drew **308 times in 5 s while idle**. Each canvas now has its own `graphicsLayer()`, and the count dropped to **0**.

Measured with 30 objects (10 stones, 10 ferns, 10 heliconias), host GPU, continuous 120-step stroke:

| Scenario | Before | After |
|---|---|---|
| Idle: missed vsyncs / work p50 | 0 / 23.2 ms | 0 / 20.2 ms |
| Raking: missed vsyncs / work p50 / p90 | 0 / 26.1 / 29.5 ms | 0 / 20.8 / 21.4 ms |

Raking over a full garden now costs about the same as an empty one.

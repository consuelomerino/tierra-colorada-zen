#!/usr/bin/env python3
"""Generates the game's sound effects and ambient loop.

Everything is synthesised from noise and sine waves, so the output is ours to
release as CC0. Usage: python3 tools/gen_sounds.py app/src/main/res/raw
"""
import math
import os
import random
import struct
import sys
import wave

RATE = 22050


def white(n):
    return [random.uniform(-1.0, 1.0) for _ in range(n)]


def lowpass(signal, a):
    """One-pole low-pass filter; smaller `a` means darker sound."""
    out, y = [], 0.0
    for x in signal:
        y += a * (x - y)
        out.append(y)
    return out


def bandpass(signal, low_a, high_a):
    hi = lowpass(signal, high_a)
    lo = lowpass(signal, low_a)
    return [h - l for h, l in zip(hi, lo)]


def normalize(signal, peak=0.8):
    m = max(abs(s) for s in signal) or 1.0
    return [s * peak / m for s in signal]


def write(path, signal):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32767)) for s in signal))


def crossfade_loop(signal, fade_seconds):
    """Blends the tail into the head so the sound loops without a click."""
    f = int(fade_seconds * RATE)
    body = signal[:-f]
    tail = signal[-f:]
    for i in range(f):
        t = i / f
        body[i] = body[i] * t + tail[i] * (1 - t)
    return body


def ambient_rainforest(seconds=30):
    n = int((seconds + 1) * RATE)
    rain = lowpass(white(n), 0.15)
    rain = [s * 0.25 * (0.8 + 0.2 * math.sin(2 * math.pi * i / (RATE * 7.3))) for i, s in enumerate(rain)]
    rumble = [s * 3.0 for s in lowpass(white(n), 0.01)]
    mix = [a + b for a, b in zip(rain, rumble)]
    for _ in range(12):
        start = random.randint(0, n - RATE)
        length = int(random.uniform(0.15, 0.4) * RATE)
        f0, f1 = random.uniform(2000, 5000), random.uniform(2000, 5000)
        phase = 0.0
        for i in range(length):
            t = i / length
            freq = f0 + (f1 - f0) * t + 150 * math.sin(2 * math.pi * 30 * i / RATE)
            phase += 2 * math.pi * freq / RATE
            mix[start + i] += 0.12 * math.sin(math.pi * t) * math.sin(phase)
    return normalize(crossfade_loop(mix, 1.0), 0.7)


def rake_scrape(seconds=2):
    n = int((seconds + 0.25) * RATE)
    s = bandpass(white(n), 0.05, 0.5)
    s = [x * (0.7 + 0.3 * math.sin(2 * math.pi * 3 * i / RATE)) for i, x in enumerate(s)]
    return normalize(crossfade_loop(s, 0.25), 0.7)


def stone_place():
    n = int(0.4 * RATE)
    out = []
    noise = white(n)
    for i in range(n):
        t = i / RATE
        thud = math.sin(2 * math.pi * 120 * t) * math.exp(-t * 18)
        click = noise[i] * math.exp(-t * 90) * 0.5
        out.append(thud + click)
    return normalize(out)


def water_tap():
    n = int(0.6 * RATE)
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE
        freq = 400 + 800 * math.exp(-t * 12)
        phase += 2 * math.pi * freq / RATE
        out.append(math.sin(phase) * math.exp(-t * 7))
    return normalize(out, 0.6)


def clear_sweep():
    n = int(0.7 * RATE)
    s = bandpass(white(n), 0.03, 0.4)
    return normalize([x * math.sin(math.pi * i / n) for i, x in enumerate(s)], 0.6)


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw"
    os.makedirs(out_dir, exist_ok=True)
    random.seed(7)
    sounds = {
        "ambient_rainforest": ambient_rainforest,
        "rake_scrape": rake_scrape,
        "stone_place": stone_place,
        "water_tap": water_tap,
        "clear_sweep": clear_sweep,
    }
    for name, make in sounds.items():
        path = os.path.join(out_dir, name + ".wav")
        write(path, make())
        print(path, os.path.getsize(path))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Synthesises the original ambient sound set (mono OGG Vorbis) and writes sounds.json.
Needs numpy, scipy and ffmpeg with libvorbis. Re-run to regenerate; swap in recorded audio any time
by replacing the .ogg files (keep names)."""
import json, os, subprocess, tempfile
import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 32000
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "livingworld")
SOUNDS = os.path.join(OUT, "sounds")

def noise(n, rng): return rng.standard_normal(n)
def _f(x, kind, f, order=2):
    sos = signal.butter(order, f, btype=kind, fs=SR, output="sos"); return signal.sosfilt(sos, x)
def lp(x, f, o=2): return _f(x, "low", f, o)
def hp(x, f, o=2): return _f(x, "high", f, o)
def bp(x, lo, hi, o=2): return _f(x, "band", [lo, hi], o)
def swell(n, rng, rate):
    m = lp(noise(n, rng), rate, 2); m = m - m.min(); return m / (m.max() + 1e-9)
def fade(x, a=0.5, b=0.8):
    n = len(x); env = np.ones(n); fi, fo = int(a * SR), int(b * SR)
    env[:fi] = np.linspace(0, 1, fi); env[-fo:] = np.linspace(1, 0, fo); return x * env
def finish(x, peak=0.8):
    x = x - np.mean(x); return (x / (np.max(np.abs(x)) + 1e-9) * peak).astype(np.float32)
def t_axis(dur): return np.arange(int(dur * SR)) / SR
def bump(t, c, w): return np.exp(-0.5 * ((t - c) / w) ** 2)
def echo(x, delays, decay):
    y = x.copy()
    for i, d in enumerate(delays):
        k = int(d * SR); y[k:] += x[:len(x) - k] * (decay ** (i + 1))
    return y

def wind_gentle(rng):
    n = int(7 * SR); x = bp(noise(n, rng), 150, 700) * (0.35 + 0.65 * swell(n, rng, 0.35))
    x += 0.4 * bp(noise(n, rng), 700, 1500) * swell(n, rng, 0.5); return fade(finish(x))
def wind_gust(rng):
    dur = 8; t = t_axis(dur); n = len(t); env = 0.15 + bump(t, rng.uniform(3, 5), rng.uniform(0.9, 1.5))
    x = bp(noise(n, rng), 100, 1100, 3) * env + 0.3 * bp(noise(n, rng), 900, 1800) * env ** 2; return fade(finish(x))
def wind_storm(rng):
    n = int(9 * SR); x = bp(noise(n, rng), 80, 1800) * (0.6 + 0.4 * swell(n, rng, 0.9))
    x += 0.2 * hp(noise(n, rng), 1500) * swell(n, rng, 1.2); return fade(finish(x))
def wind_sand(rng):
    n = int(7 * SR); x = bp(noise(n, rng), 1500, 6000) * (0.3 + swell(n, rng, 0.5)); return fade(finish(x, 0.6))
def leaves(rng):
    n = int(6 * SR); x = 0.08 * bp(noise(n, rng), 2500, 9000) * swell(n, rng, 0.5)
    imp = (rng.random(n) < 0.0009).astype(float) * rng.uniform(0.3, 1, n)
    burst = hp(noise(int(0.012 * SR), rng), 2500) * np.hanning(int(0.012 * SR))
    x += np.convolve(imp, burst, mode="same") * 0.8; return fade(finish(x, 0.6))
def bird(rng):
    n = int(6 * SR); x = np.zeros(n)
    for _ in range(rng.integers(2, 4)):
        start = rng.uniform(0.3, 4.5); f0 = rng.uniform(2500, 4200); pos = start
        for _ in range(rng.integers(3, 8)):
            d = rng.uniform(0.04, 0.09); tt = np.arange(int(d * SR)) / SR
            f = f0 + rng.uniform(-900, 1400) * tt / d + 150 * np.sin(2 * np.pi * 30 * tt)
            ph = 2 * np.pi * np.cumsum(f) / SR; c = np.sin(ph) * np.sin(np.pi * tt / d) ** 2 * 0.5
            i = int(pos * SR)
            if i + len(c) < n: x[i:i + len(c)] += c
            pos += d + rng.uniform(0.03, 0.08)
    return fade(finish(echo(x, [0.11], 0.3)), 0.1, 0.4)
def crickets(rng):
    dur = 8; t = t_axis(dur); n = len(t); x = np.zeros(n)
    for _ in range(3):
        f = rng.uniform(4200, 4800); off = rng.uniform(0, 0.3); period = rng.uniform(0.26, 0.32)
        ph = (t + off) % period; gate = ((ph % 0.027) < 0.012) & (ph < 0.09)
        x += np.sin(2 * np.pi * f * t) * gate.astype(float) * rng.uniform(0.6, 1)
    return fade(finish(lp(x, 7000), 0.5))
def owl(rng):
    t = t_axis(5); n = len(t); x = np.zeros(n)
    for start in (0.6, 1.35, 2.9, 3.6):
        d = 0.38; tt = np.arange(int(d * SR)) / SR; f = rng.uniform(380, 440) * (1 - 0.06 * tt / d)
        ph = 2 * np.pi * np.cumsum(f) / SR; c = (np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.15 * np.sin(3 * ph)) * np.sin(np.pi * tt / d) ** 1.5
        i = int(start * SR); x[i:i + len(c)] += c * (1.0 if start < 2 else 0.8)
    return fade(finish(echo(lp(x, 2500), [0.2, 0.45], 0.35)), 0.1, 0.5)
def frog(rng):
    n = int(5 * SR); x = np.zeros(n); pos = 0.4
    for _ in range(rng.integers(3, 6)):
        d = rng.uniform(0.35, 0.6); tt = np.arange(int(d * SR)) / SR; f = rng.uniform(200, 300)
        c = np.sin(2 * np.pi * f * tt) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 38 * tt))) * np.sin(np.pi * tt / d)
        i = int(pos * SR)
        if i + len(c) < n: x[i:i + len(c)] += c
        pos += d + rng.uniform(0.2, 0.5)
    return fade(finish(lp(x, 1800)), 0.1, 0.4)
def wave(rng):
    t = t_axis(10); n = len(t); c = rng.uniform(3.5, 4.5)
    env = np.where(t < c, np.sin(0.5 * np.pi * np.clip(t / c, 0, 1)) ** 2, np.exp(-(t - c) / 2.5))
    x = lp(noise(n, rng), 900) * env + 0.4 * hp(noise(n, rng), 2500) * env ** 3; return fade(finish(x))
def river(rng):
    n = int(9 * SR); x = bp(noise(n, rng), 400, 3500) * (0.6 + 0.4 * swell(n, rng, 2.5))
    x += 0.3 * bp(noise(n, rng), 1500, 6000) * swell(n, rng, 6); return fade(finish(x, 0.7))
def drip(rng):
    t = t_axis(3); n = len(t); x = np.zeros(n); f = rng.uniform(1100, 2200); d = 0.09
    tt = np.arange(int(d * SR)) / SR; c = np.sin(2 * np.pi * (f * (1 + 0.25 * tt / d)) * tt) * np.exp(-tt / 0.02)
    i = int(0.2 * SR); x[i:i + len(c)] += c
    return fade(finish(lp(echo(x, [0.22, 0.47, 0.8], 0.5), 5000)), 0.05, 0.5)
def rumble(rng):
    n = int(9 * SR); x = lp(noise(n, rng), 90, 3) * (0.4 + swell(n, rng, 0.25)) + 0.3 * lp(noise(n, rng), 220) * swell(n, rng, 0.4)
    return fade(finish(x, 0.7), 1.5, 2)
def thunder_far(rng):
    t = t_axis(11); n = len(t); env = np.exp(-t / 3.5) * (0.6 + 0.4 * swell(n, rng, 1.2))
    x = lp(noise(n, rng), 180) * env
    for c in (1.2, 2.8): x += 0.5 * lp(noise(n, rng), 400) * bump(t, c, 0.5) * 0.6
    x[:int(0.05 * SR)] *= np.linspace(0, 1, int(0.05 * SR)); return fade(finish(x), 0.02, 2)
def thunder_roll(rng):
    t = t_axis(13); n = len(t); env = np.zeros(n)
    for _ in range(rng.integers(4, 7)): env += rng.uniform(0.4, 1) * bump(t, rng.uniform(0.8, 9), rng.uniform(0.7, 1.8))
    x = lp(noise(n, rng), 250, 3) * env; return fade(finish(x), 0.5, 3)
def underwater(rng):
    t = t_axis(8); n = len(t); x = lp(noise(n, rng), 300, 4) * (0.5 + swell(n, rng, 0.3)) + 0.25 * np.sin(2 * np.pi * 55 * t)
    return fade(finish(x, 0.7), 1, 1.5)
def bubbles(rng):
    n = int(4 * SR); x = np.zeros(n)
    for _ in range(rng.integers(6, 12)):
        d = rng.uniform(0.03, 0.06); tt = np.arange(int(d * SR)) / SR; f = rng.uniform(350, 700)
        c = np.sin(2 * np.pi * (f + 900 * tt / d) * tt) * np.exp(-tt / 0.012); i = int(rng.uniform(0.1, 3.7) * SR); x[i:i + len(c)] += c
    return fade(finish(lp(x, 3500), 0.6), 0.05, 0.3)

EVENTS = {  # event name -> (folder/name, generator)
    "wind_gentle": ("wind/gentle", wind_gentle), "wind_gust": ("wind/gust", wind_gust),
    "wind_storm": ("wind/storm", wind_storm), "wind_sand": ("wind/sand", wind_sand),
    "leaves": ("forest/leaves", leaves), "bird": ("forest/bird", bird),
    "crickets": ("night/crickets", crickets), "owl": ("night/owl", owl), "frog": ("night/frog", frog),
    "wave": ("water/wave", wave), "river": ("water/river", river),
    "drip": ("cave/drip", drip), "rumble": ("cave/rumble", rumble),
    "thunder_far": ("thunder/far", thunder_far), "thunder_roll": ("thunder/roll", thunder_roll),
    "underwater": ("underwater/hum", underwater), "bubbles": ("underwater/bubbles", bubbles),
}

def main():
    manifest = {}
    for ev, (path, gen) in EVENTS.items():
        entries = []
        for v in range(1, 4):
            rng = np.random.default_rng(hash((ev, v)) % (2 ** 32) if False else sum(map(ord, ev)) * 100 + v)
            data = gen(rng); name = f"{path}_{v}"
            target = os.path.join(SOUNDS, name + ".ogg"); os.makedirs(os.path.dirname(target), exist_ok=True)
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp: wav = tmp.name
            wavfile.write(wav, SR, (np.clip(data, -1, 1) * 32767).astype(np.int16))
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "2", target], check=True)
            os.remove(wav); entries.append("livingworld:" + name)
        manifest[ev] = {"sounds": entries}
    with open(os.path.join(OUT, "sounds.json"), "w") as f: json.dump(manifest, f, indent=2)
    print("wrote", sum(len(m["sounds"]) for m in manifest.values()), "sounds")

if __name__ == "__main__": main()

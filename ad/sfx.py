"""Makes the ad's sound effects in public/sfx/, from scratch, so there are no rights to clear.

    python3 sfx.py

whoosh.wav  low, soft air sweep on each cut
tap.wav     a light tap on the Favorite button
pop.wav     the heart filling
chime.wav   two soft notes on the end card
"""
import pathlib
import wave

import numpy as np

RATE = 48000
OUT = pathlib.Path(__file__).parent / "public" / "sfx"
rng = np.random.default_rng(7)


def save(name, x):
    x = x / max(1e-9, np.abs(x).max()) * 0.8
    stereo = np.repeat((x * 32767).astype(np.int16)[:, None], 2, axis=1)
    with wave.open(str(OUT / name), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(stereo.tobytes())


def t(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def lowpass(x, cutoff):
    """One-pole low-pass whose cutoff (Hz) can change over time."""
    a = np.exp(-2 * np.pi * np.broadcast_to(cutoff, x.shape) / RATE)
    y = np.zeros_like(x)
    prev = 0.0
    for i in range(len(x)):
        prev = (1 - a[i]) * x[i] + a[i] * prev
        y[i] = prev
    return y


def whoosh():
    s = t(0.7)
    sweep = 200 + 900 * np.sin(np.pi * s / s[-1]) ** 2
    noise = lowpass(lowpass(lowpass(rng.standard_normal(len(s)), sweep), sweep), sweep)
    return noise * np.sin(np.pi * s / s[-1]) ** 2.5


def tap():
    s = t(0.12)
    return np.sin(2 * np.pi * 900 * s) * np.minimum(1, s / 0.004) * np.exp(-s * 45)


def pop():
    s = t(0.35)
    freq = 520 + 260 * np.exp(-s * 25)
    return np.sin(2 * np.pi * np.cumsum(freq) / RATE) * np.minimum(1, s / 0.01) * np.exp(-s * 12)


def chime():
    s = t(1.6)
    out = np.zeros(len(s))
    for start, f in [(0.0, 784.0), (0.18, 1174.7)]:
        k = s >= start
        u = s[k] - start
        out[k] += (np.sin(2 * np.pi * f * u) + 0.1 * np.sin(4 * np.pi * f * u)) * np.minimum(1, u / 0.02) * np.exp(-u * 2.5)
    return out


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in [("whoosh.wav", whoosh), ("tap.wav", tap), ("pop.wav", pop), ("chime.wav", chime)]:
        save(name, fn())
        print("made", name)

"""Makes the ad's sound effects in public/sfx/, from scratch, so there are no rights to clear.

    python3 sfx.py

whoosh.wav  soft air sweep on each cut
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
    s = t(0.45)
    sweep = 300 + 2600 * np.sin(np.pi * s / s[-1]) ** 2
    noise = lowpass(lowpass(rng.standard_normal(len(s)), sweep), sweep)
    return noise * np.sin(np.pi * s / s[-1]) ** 1.5


def tap():
    s = t(0.06)
    return (np.sin(2 * np.pi * 1800 * s) + 0.4 * rng.standard_normal(len(s))) * np.exp(-s * 90)


def pop():
    s = t(0.18)
    freq = 380 + 520 * np.exp(-s * 30)
    return np.sin(2 * np.pi * np.cumsum(freq) / RATE) * np.exp(-s * 22)


def chime():
    s = t(1.6)
    out = np.zeros(len(s))
    for start, f in [(0.0, 784.0), (0.18, 1174.7)]:
        k = s >= start
        u = s[k] - start
        out[k] += (np.sin(2 * np.pi * f * u) + 0.3 * np.sin(4 * np.pi * f * u)) * np.exp(-u * 3.2)
    return out


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in [("whoosh.wav", whoosh), ("tap.wav", tap), ("pop.wav", pop), ("chime.wav", chime)]:
        save(name, fn())
        print("made", name)

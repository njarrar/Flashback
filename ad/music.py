"""Makes the ad's music, public/music.mp3, from scratch, so there are no rights to clear.

    python3 music.py

A calm, steady 31-second loop at 92 beats a minute: soft pads on four chords
(Fmaj9, Am7, Dm9, Csus), a low bass note per bar and slow plucked notes, with a little
room sound. It stays at one level all the way through, so it never swells under the
captions. Needs numpy and ffmpeg.
"""
import pathlib
import subprocess
import tempfile
import wave

import numpy as np

RATE = 48000
BEAT = 60 / 92
BAR = 4 * BEAT
SECONDS = 31.0
OUT = pathlib.Path(__file__).parent / "public" / "music.mp3"

N = int(RATE * SECONDS)
left, right = np.zeros(N), np.zeros(N)
rng = np.random.default_rng(3)

CHORDS = [[53, 57, 60, 64, 67], [57, 60, 64, 67, 72], [50, 57, 60, 64, 65], [55, 60, 62, 67, 72]]
BASS = [41, 45, 38, 43]


def hz(note):
    return 440 * 2 ** ((note - 69) / 12)


def t(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def add(buf, start, x, gain):
    i = int(start * RATE)
    j = min(N, i + len(x))
    if i < N:
        buf[i:j] += gain * x[:j - i]


def pad(note, seconds):
    s = t(seconds)
    out = np.zeros(len(s))
    for detune in (-0.08, 0.0, 0.08):
        f = hz(note + detune)
        for k, a in [(1, 1), (2, .35), (3, .15), (4, .07)]:
            out += a * np.sin(2 * np.pi * f * k * s + rng.uniform(0, 6))
    return out * np.minimum(1, s / 0.9) * np.minimum(1, (seconds - s) / 0.9) / 3


def pluck(note):
    s = t(1.6)
    f = hz(note)
    x = sum(a * np.sin(2 * np.pi * f * k * s) * np.exp(-s * (2.2 + k * 1.8)) for k, a in [(1, 1), (2, .2), (3, .05)])
    return x * np.minimum(1, s / 0.02)


def bass(note, seconds):
    s = t(seconds)
    return np.sin(2 * np.pi * hz(note) * s) * np.minimum(1, s / 0.03) * np.exp(-s * 1.2)


for b in range(int(SECONDS / BAR) + 1):
    chord, start = CHORDS[b % 4], b * BAR
    for note in chord[:4]:
        x = pad(note, BAR + 0.4)
        add(left, start, x, .05)
        add(right, start, x, .05)
    x = bass(BASS[b % 4], BAR)
    add(left, start, x, .08)
    add(right, start, x, .08)
    for i, note in enumerate([chord[0], chord[3], chord[3], chord[1]]):
        x = pluck(note + 12)
        add(left, start + i * BEAT, x, .135)
        add(right, start + i * BEAT, x, .073)

# A little room sound.
tail = t(1.8)
for buf, seed in ((left, 1), (right, 2)):
    ir = np.random.default_rng(seed).standard_normal(len(tail)) * np.exp(-tail * 3.2)
    ir[0] = 0
    n = 1 << int(np.ceil(np.log2(N + len(ir))))
    buf += np.fft.irfft(np.fft.rfft(buf, n) * np.fft.rfft(ir, n), n)[:N] * 0.018

mix = np.stack([left, right], 1)
mix = mix / np.abs(mix).max() * 0.85
with tempfile.TemporaryDirectory() as tmp:
    wav = pathlib.Path(tmp) / "music.wav"
    with wave.open(str(wav), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((mix * 32767).astype(np.int16).tobytes())
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-b:a", "192k", str(OUT)], check=True)
print("made", OUT.name)

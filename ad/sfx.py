"""Makes the ad's sound effects in public/sfx/, from scratch, so there are no rights to clear.

    python3 sfx.py

whoosh.wav  DC-free, stereo-swept soft air transition
tap.wav     organic filtered wood/glass tactile tap
pop.wav     warm upward harmonic favorite pop with smooth zero-crossing tail
chime.wav   warm two-note marimba/celesta resolution in the key of music.mp3
"""
import array
import math
import pathlib
import random
import wave

RATE = 48000
OUT = pathlib.Path(__file__).parent / "public" / "sfx"


def dc_block(x, cutoff=60.0):
    """First-order high-pass filter to remove DC offset."""
    r = math.exp(-2.0 * math.pi * cutoff / RATE)
    y = [0.0] * len(x)
    prev_x = 0.0
    prev_y = 0.0
    for i, xi in enumerate(x):
        yi = xi - prev_x + r * prev_y
        y[i] = yi
        prev_x = xi
        prev_y = yi
    return y


def lowpass(x, cutoffs):
    """One-pole low-pass whose cutoff (Hz) can vary per sample."""
    y = [0.0] * len(x)
    prev = 0.0
    const = -2.0 * math.pi / RATE
    if isinstance(cutoffs, (int, float)):
        a = math.exp(const * cutoffs)
        b = 1.0 - a
        for i, xi in enumerate(x):
            prev = b * xi + a * prev
            y[i] = prev
    else:
        for i, (xi, fc) in enumerate(zip(x, cutoffs)):
            a = math.exp(const * fc)
            prev = (1.0 - a) * xi + a * prev
            y[i] = prev
    return y


def fade_ends(x, fade_in_s=0.004, fade_out_s=0.03):
    """Apply smooth raised-cosine fades so buffers start and end at exact zero."""
    n = len(x)
    fi = min(n // 2, int(fade_in_s * RATE))
    fo = min(n // 2, int(fade_out_s * RATE))
    out = list(x)
    for i in range(fi):
        out[i] *= 0.5 * (1.0 - math.cos(math.pi * i / fi))
    for i in range(fo):
        idx = n - 1 - i
        out[idx] *= 0.5 * (1.0 - math.cos(math.pi * i / fo))
    if n > 0:
        out[0] = 0.0
        out[-1] = 0.0
    return out


def save_stereo(name, left, right, peak_target=0.81):
    """Remove any residual DC, normalize symmetrically, and write 16-bit stereo WAV."""
    n = len(left)
    mean_l = sum(left) / max(1, n)
    mean_r = sum(right) / max(1, n)
    # Only subtract DC if envelope stays zero at endpoints; re-pin endpoints to 0.
    left = [v - mean_l for v in left]
    right = [v - mean_r for v in right]
    left = fade_ends(left, 0.002, 0.015)
    right = fade_ends(right, 0.002, 0.015)

    peak = max(max(abs(v) for v in left), max(abs(v) for v in right), 1e-9)
    scale = peak_target / peak
    pcm = array.array("h")
    for l, r in zip(left, right):
        pcm.append(max(-32767, min(32767, int(round(l * scale * 32767)))))
        pcm.append(max(-32767, min(32767, int(round(r * scale * 32767)))))
    with wave.open(str(OUT / name), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())


def whoosh():
    """DC-free soft air sweep with subtle L -> R stereo motion."""
    rng = random.Random(7)
    n = int(0.70 * RATE)
    raw = [rng.gauss(0.0, 1.0) for _ in range(n)]
    sweep = [180.0 + 980.0 * (math.sin(math.pi * i / (n - 1)) ** 2) for i in range(n)]
    shaped = lowpass(lowpass(lowpass(raw, sweep), sweep), sweep)
    shaped = dc_block(shaped, cutoff=75.0)
    # Apply smooth envelope and subtle L -> R stereo pan (-0.36 to +0.36) while staying mono-safe.
    left = [0.0] * n
    right = [0.0] * n
    for i in range(n):
        u = i / (n - 1)
        env = math.sin(math.pi * u) ** 2.4
        pan = -0.36 + 0.72 * u  # -0.36 -> +0.36
        angle = (pan + 1.0) * 0.25 * math.pi
        v = shaped[i] * env
        left[i] = v * math.cos(angle)
        right[i] = v * math.sin(angle)
    return fade_ends(left, 0.02, 0.04), fade_ends(right, 0.02, 0.04)


def tap():
    """Organic woody/glass tactile tap: band-limited transient + warm low body."""
    rng = random.Random(19)
    n = int(0.12 * RATE)
    raw = [rng.gauss(0.0, 1.0) for _ in range(n)]
    # Band-pass transient roughly 1.6 kHz - 3.6 kHz.
    hp = dc_block(raw, cutoff=1600.0)
    bp = lowpass(lowpass(hp, 3600.0), 3600.0)
    left = [0.0] * n
    right = [0.0] * n
    for i in range(n):
        s = i / RATE
        att = min(1.0, s / 0.0018)
        # Short crisp glass/wood transient + warm 195 Hz body + 560 Hz woody knock.
        click = bp[i] * att * math.exp(-s * 145.0) * 0.95
        body = math.sin(2.0 * math.pi * 195.0 * s) * min(1.0, s / 0.0025) * math.exp(-s * 42.0) * 0.42
        wood = math.sin(2.0 * math.pi * 560.0 * s) * min(1.0, s / 0.002) * math.exp(-s * 85.0) * 0.24
        # Micro stereo width on the transient so it feels natural, not synthetic mono.
        left[i] = body + wood + click * 1.04
        right[i] = body + wood + click * 0.96
    return fade_ends(dc_block(left, 55.0), 0.001, 0.025), fade_ends(dc_block(right, 55.0), 0.001, 0.025)


def pop():
    """Joyful upward harmonic favorite pop sweeping 420 -> 860 Hz with clean release tail."""
    n = int(0.32 * RATE)
    left = [0.0] * n
    right = [0.0] * n
    phase_l = 0.0
    phase_r = 0.0
    for i in range(n):
        s = i / RATE
        # Smooth upward pitch sweep 420 Hz -> 860 Hz.
        freq = 860.0 - 440.0 * math.exp(-s * 36.0)
        phase_l += 2.0 * math.pi * freq / RATE
        phase_r += 2.0 * math.pi * (freq * 1.0018) / RATE
        att = min(1.0, s / 0.008)
        env = att * math.exp(-s * 13.5)
        h2_env = att * math.exp(-s * 22.0)
        h3_env = att * math.exp(-s * 32.0)
        # Warm soft fundamental + harmonic bloom.
        left[i] = math.sin(phase_l) * env + 0.24 * math.sin(2.0 * phase_l) * h2_env + 0.07 * math.sin(3.0 * phase_l) * h3_env
        right[i] = math.sin(phase_r) * env + 0.24 * math.sin(2.0 * phase_r) * h2_env + 0.07 * math.sin(3.0 * phase_r) * h3_env
    return fade_ends(left, 0.004, 0.05), fade_ends(right, 0.004, 0.05)


def chime():
    """Warm two-note marimba/celesta resolution (A5 -> C6) consonant over the Fmaj9/Am7/Dm9 bed."""
    n = int(1.60 * RATE)
    left = [0.0] * n
    right = [0.0] * n
    # A5 (880 Hz) -> C6 (1046.5 Hz): warm minor-third lift resolving into the Fmaj9 / Am7 key center.
    notes = [
        (0.00, 880.00, 0.56, 0.44),   # slightly left
        (0.19, 1046.50, 0.44, 0.56),  # slightly right
    ]
    for start, f, gl, gr in notes:
        i0 = int(start * RATE)
        for i in range(i0, n):
            u = (i - i0) / RATE
            att = min(1.0, u / 0.012)
            env1 = att * math.exp(-u * 2.9)
            env2 = att * math.exp(-u * 5.8)
            env3 = att * math.exp(-u * 10.5)
            # Subtle stereo shimmer detune (+/- 0.25 Hz) while keeping strong mono coherence.
            vl = (
                math.sin(2.0 * math.pi * f * u) * env1
                + 0.16 * math.sin(2.0 * math.pi * 2.0 * f * u) * env2
                + 0.05 * math.sin(2.0 * math.pi * 3.0 * f * u) * env3
            )
            vr = (
                math.sin(2.0 * math.pi * (f + 0.35) * u) * env1
                + 0.16 * math.sin(2.0 * math.pi * 2.0 * (f + 0.35) * u) * env2
                + 0.05 * math.sin(2.0 * math.pi * 3.0 * (f + 0.35) * u) * env3
            )
            left[i] += vl * gl
            right[i] += vr * gr
    return fade_ends(left, 0.006, 0.22), fade_ends(right, 0.006, 0.22)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in [("whoosh.wav", whoosh), ("tap.wav", tap), ("pop.wav", pop), ("chime.wav", chime)]:
        l, r = fn()
        save_stereo(name, l, r)
        print("made", name)

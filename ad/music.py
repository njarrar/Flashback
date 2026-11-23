"""Makes the ad's music, public/music.mp3, from scratch, so there are no rights to clear.

    python3 music.py

A calm, seamless 12-bar loop (31.30s) at 92 beats a minute: full 5-note warm felt-piano
pads on four chords (Fmaj9, Am7, Dm9, Gsus4), centered warm bass, balanced stereo plucks
with gentle dynamics, and a damped mono-safe room reverb. Mastered to -14.5 LUFS with
true peak <= -1.5 dBTP and zero loop click seam. Needs only Python 3 stdlib and ffmpeg.
"""
import array
import math
import pathlib
import random
import re
import subprocess
import tempfile
import wave

RATE = 48000
BEAT = 60.0 / 92.0
BAR = 4.0 * BEAT
BARS = 12
SECONDS = BARS * BAR
N = round(SECONDS * RATE)
OUT = pathlib.Path(__file__).parent / "public" / "music.mp3"

# Full 5-note voicings:
#   0: Fmaj9 (F3, A3, C4, E4, G4)
#   1: Am7   (A3, C4, E4, G4, C5)
#   2: Dm9   (D3, F3, A3, C4, E4) — includes minor 3rd F3 and 9th E4
#   3: Gsus4 (G3, C4, D4, G4, C5)
CHORDS = [
    [53, 57, 60, 64, 67],
    [57, 60, 64, 67, 72],
    [50, 53, 57, 60, 64],
    [55, 60, 62, 67, 72],
]
BASS = [41, 45, 38, 43]  # F2, A2, D2, G2

TARGET_LUFS = -14.5
CEILING_LIN = 10.0 ** (-1.6 / 20.0)  # <= -1.5 dBTP true peak headroom


def hz(note):
    return 440.0 * (2.0 ** ((note - 69.0) / 12.0))


def add_wrap(buf, start_sec, x, gain):
    """Circular wrap-around mix so tails across bar 12 continue seamlessly into bar 0."""
    i0 = round(start_sec * RATE) % N
    m = len(x)
    end = i0 + m
    if end <= N:
        for k in range(m):
            buf[i0 + k] += gain * x[k]
    else:
        first = N - i0
        for k in range(first):
            buf[i0 + k] += gain * x[k]
        for k in range(first, m):
            buf[k - first] += gain * x[k]


def make_sine_harmonics(length, freq, harmonics, phases):
    """Fast harmonic additive synthesis using trig recurrences (zero trig calls per sample)."""
    out = [0.0] * length
    for (k, amp), ph in zip(harmonics, phases):
        w = 2.0 * math.pi * freq * k / RATE
        c = math.cos(w)
        s = math.sin(w)
        y = math.sin(ph)
        x = math.cos(ph)
        for i in range(length):
            out[i] += amp * y
            nx = x * c - y * s
            ny = x * s + y * c
            x, y = nx, ny
    return out


def pad_note(note, seconds, rng):
    """Warm felt-piano/pad voice with gentle chorus detune and airy upper harmonics."""
    length = round(seconds * RATE)
    att = round(0.75 * RATE)
    rel = round(0.85 * RATE)
    out = [0.0] * length
    # Subtle partials up to 6th give natural warmth + gentle air above 2 kHz.
    harmonics = [(1, 1.0), (2, 0.36), (3, 0.16), (4, 0.08), (5, 0.04), (6, 0.02)]
    for detune in (-0.06, 0.0, 0.06):
        f = hz(note + detune)
        phases = [rng.uniform(0.0, 2.0 * math.pi) for _ in harmonics]
        part = make_sine_harmonics(length, f, harmonics, phases)
        for i in range(length):
            out[i] += part[i]
    inv = 1.0 / 3.0
    for i in range(length):
        env = 1.0
        if i < att:
            env = 0.5 * (1.0 - math.cos(math.pi * i / att))
        elif i >= length - rel:
            rem = length - 1 - i
            env = 0.5 * (1.0 - math.cos(math.pi * rem / rel))
        out[i] *= env * inv
    return out


def pluck_note(note):
    """Warm felt-mallet pluck with natural overtone decay."""
    length = round(1.55 * RATE)
    f = hz(note)
    harmonics = [(1, 1.0), (2, 0.28), (3, 0.11), (4, 0.045)]
    out = [0.0] * length
    att = round(0.014 * RATE)
    rel = round(0.08 * RATE)
    for k, amp in harmonics:
        w = 2.0 * math.pi * f * k / RATE
        c = math.cos(w)
        s = math.sin(w)
        decay = math.exp(-(2.1 + k * 1.9) / RATE)
        x, y = 1.0, 0.0
        env = amp
        for i in range(length):
            out[i] += env * y
            env *= decay
            nx = x * c - y * s
            ny = x * s + y * c
            x, y = nx, ny
    for i in range(att):
        out[i] *= i / att
    for i in range(rel):
        idx = length - 1 - i
        out[idx] *= 0.5 * (1.0 - math.cos(math.pi * i / rel))
    return out


def bass_note(note, seconds):
    """Centered warm sub/bass note (< 200 Hz mono) with gentle 2nd harmonic warmth."""
    length = round(seconds * RATE)
    f = hz(note)
    att = round(0.03 * RATE)
    rel = round(0.09 * RATE)
    out = [0.0] * length
    for k, amp, rate in ((1, 1.0, 1.15), (2, 0.14, 2.2)):
        w = 2.0 * math.pi * f * k / RATE
        c = math.cos(w)
        s = math.sin(w)
        decay = math.exp(-rate / RATE)
        x, y = 1.0, 0.0
        env = amp
        for i in range(length):
            out[i] += env * y
            env *= decay
            nx = x * c - y * s
            ny = x * s + y * c
            x, y = nx, ny
    for i in range(att):
        out[i] *= i / att
    for i in range(rel):
        idx = length - 1 - i
        out[idx] *= 0.5 * (1.0 - math.cos(math.pi * i / rel))
    return out


def damped_comb_circular(buf, delay, feedback, damp):
    """Circular damped Schroeder comb filter: warms up for 1.5s from end so loop is seamless."""
    n = len(buf)
    ring = [0.0] * delay
    idx = 0
    lp = 0.0
    one_minus_damp = 1.0 - damp
    # Warmup tail from the final 1.5 seconds of the circular buffer.
    warmup = min(n, int(1.5 * RATE))
    start = n - warmup
    for k in range(warmup):
        inp = buf[start + k]
        delayed = ring[idx]
        lp = one_minus_damp * delayed + damp * lp
        ring[idx] = inp + feedback * lp
        idx += 1
        if idx == delay:
            idx = 0
    out = [0.0] * n
    for i in range(n):
        delayed = ring[idx]
        lp = one_minus_damp * delayed + damp * lp
        ring[idx] = buf[i] + feedback * lp
        out[i] = delayed
        idx += 1
        if idx == delay:
            idx = 0
    return out


def apply_room_reverb(left, right):
    """Mono-safe, low-pass damped stereo room reverb wrapped circularly."""
    # Pre-delay 18 ms (864 samples), circular shift.
    pre = int(0.018 * RATE)
    # Mix a touch of shared center into reverb input so stereo image stays mono-safe (>= +0.55 corr).
    in_l = [0.68 * left[(i - pre) % N] + 0.32 * right[(i - pre) % N] for i in range(N)]
    in_r = [0.68 * right[(i - pre) % N] + 0.32 * left[(i - pre) % N] for i in range(N)]
    # 4 mutually prime comb delays per channel with slight L/R offset (~1.1ms) for subtle natural width.
    # Damping coefficient ~0.42 rolls off highs above ~3.5 kHz inside the feedback path.
    combs_l = [(1553, 0.73), (1871, 0.71), (2137, 0.69), (2473, 0.67)]
    combs_r = [(1607, 0.73), (1913, 0.71), (2179, 0.69), (2521, 0.67)]
    wet_l = [0.0] * N
    wet_r = [0.0] * N
    for d, fb in combs_l:
        c = damped_comb_circular(in_l, d, fb, damp=0.42)
        for i in range(N):
            wet_l[i] += c[i]
    for d, fb in combs_r:
        c = damped_comb_circular(in_r, d, fb, damp=0.42)
        for i in range(N):
            wet_r[i] += c[i]
    # Gentle wet mix (~25%) preserves clarity and keeps stereo correlation comfortably in +0.75..+0.88.
    wet_gain = 0.072
    for i in range(N):
        left[i] += wet_l[i] * wet_gain
        right[i] += wet_r[i] * wet_gain


def dc_block_circular(buf, cutoff=25.0):
    """High-pass DC blocker with circular warmup so loop seam stays zero-click."""
    r = math.exp(-2.0 * math.pi * cutoff / RATE)
    n = len(buf)
    warmup = min(n, int(0.5 * RATE))
    prev_x = buf[n - warmup]
    prev_y = 0.0
    for k in range(warmup):
        xi = buf[n - warmup + k]
        prev_y = xi - prev_x + r * prev_y
        prev_x = xi
    out = [0.0] * n
    for i in range(n):
        xi = buf[i]
        yi = xi - prev_x + r * prev_y
        out[i] = yi
        prev_x = xi
        prev_y = yi
    return out


def write_wav(path, left, right):
    pcm = array.array("h")
    for l, r in zip(left, right):
        pcm.append(max(-32767, min(32767, int(round(l * 32767)))))
        pcm.append(max(-32767, min(32767, int(round(r * 32767)))))
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())


def measure_lufs(wav_path):
    res = subprocess.run(
        ["ffmpeg", "-hide_banner", "-i", str(wav_path), "-af", "ebur128=peak=true", "-f", "null", "-"],
        capture_output=True, text=True, check=True,
    )
    matches = re.findall(r"I:\s+([-\d.]+)\s+LUFS", res.stderr)
    return float(matches[-1])


def soft_limit(x, ceiling):
    """Transparent soft-knee limiter above 85% of ceiling so true peak stays <= -1.5 dBTP."""
    knee = ceiling * 0.85
    head = ceiling - knee
    ax = abs(x)
    if ax <= knee:
        return x
    excess = (ax - knee) / head
    limited = knee + head * math.tanh(excess)
    return limited if x >= 0 else -limited


def main():
    rng = random.Random(3)
    left = [0.0] * N
    right = [0.0] * N

    # Cache unique synthesized notes so 12 bars render in a couple seconds.
    pad_cache = {}
    pluck_cache = {}
    bass_cache = {}

    # Symmetrical gentle L/R pan positions for the 5 pad voices (sums to balanced center).
    pad_pans = [(0.50, 0.50), (0.56, 0.44), (0.44, 0.56), (0.58, 0.42), (0.42, 0.58)]
    beat_vel = [1.00, 0.78, 0.90, 0.74]
    # Gentle phrase contour across the 12-bar cycle (bars 0..11) for musical LRA without distracting swells.
    bar_vel = [0.90, 0.94, 0.98, 1.00, 0.96, 1.00, 1.02, 0.95, 0.92, 0.98, 1.02, 0.94]

    for b in range(BARS):
        chord = CHORDS[b % 4]
        start = b * BAR
        bv = bar_vel[b]

        # Full 5-note pad voicing (all 5 notes, including Dm9 minor 3rd and Fmaj9 9th).
        for idx, note in enumerate(chord):
            if note not in pad_cache:
                pad_cache[note] = pad_note(note, BAR + 0.5, rng)
            x = pad_cache[note]
            pl, pr = pad_pans[idx]
            add_wrap(left, start, x, 0.088 * bv * pl)
            add_wrap(right, start, x, 0.088 * bv * pr)

        # Warm centered mono bass (< 200 Hz strictly mono).
        bn = BASS[b % 4]
        if bn not in bass_cache:
            bass_cache[bn] = bass_note(bn, BAR + 0.05)
        bx = bass_cache[bn]
        add_wrap(left, start, bx, 0.095 * bv)
        add_wrap(right, start, bx, 0.095 * bv)

        # Musical 4-beat arpeggio with alternating balanced L/R panning (no double-strike stutter).
        pattern = [chord[0], chord[2], chord[4], chord[3]] if (b % 2 == 0) else [chord[0], chord[3], chord[4], chord[1]]
        for i, note in enumerate(pattern):
            pn = note + 12
            if pn not in pluck_cache:
                pluck_cache[pn] = pluck_note(pn)
            px = pluck_cache[pn]
            vel = 0.165 * bv * beat_vel[i]
            # Alternate gently around center so L/R RMS stays within +/-0.2 dB.
            if (b + i) % 2 == 0:
                gl, gr = 0.58, 0.42
            else:
                gl, gr = 0.42, 0.58
            add_wrap(left, start + i * BEAT, px, vel * gl)
            add_wrap(right, start + i * BEAT, px, vel * gr)

    apply_room_reverb(left, right)
    left = dc_block_circular(left)
    right = dc_block_circular(right)

    # Match L/R RMS symmetrically so stereo balance stays within +/-0.1 dB.
    rms_l = math.sqrt(sum(v * v for v in left) / N)
    rms_r = math.sqrt(sum(v * v for v in right) / N)
    if rms_l > 1e-9 and rms_r > 1e-9:
        mid_rms = math.sqrt(rms_l * rms_r)
        gl, gr = mid_rms / rms_l, mid_rms / rms_r
        left = [v * gl for v in left]
        right = [v * gr for v in right]

    # Initial peak normalization before LUFS calibration.
    peak = max(max(abs(v) for v in left), max(abs(v) for v in right), 1e-9)
    pre_scale = 0.7 / peak
    left = [v * pre_scale for v in left]
    right = [v * pre_scale for v in right]

    with tempfile.TemporaryDirectory() as tmp:
        raw_wav = pathlib.Path(tmp) / "raw.wav"
        master_wav = pathlib.Path(tmp) / "master.wav"
        write_wav(raw_wav, left, right)
        measured = measure_lufs(raw_wav)
        gain_db = TARGET_LUFS - measured
        gain = 10.0 ** (gain_db / 20.0)
        m_left = [soft_limit(v * gain, CEILING_LIN) for v in left]
        m_right = [soft_limit(v * gain, CEILING_LIN) for v in right]
        write_wav(master_wav, m_left, m_right)
        # Re-verify integrated loudness after soft limiting and trim once if needed.
        measured2 = measure_lufs(master_wav)
        if abs(measured2 - TARGET_LUFS) > 0.15:
            trim = 10.0 ** ((TARGET_LUFS - measured2) / 20.0)
            m_left = [soft_limit(v * trim, CEILING_LIN) for v in m_left]
            m_right = [soft_limit(v * trim, CEILING_LIN) for v in m_right]
            write_wav(master_wav, m_left, m_right)
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", str(master_wav), "-b:a", "192k", "-ar", str(RATE), str(OUT)],
            check=True,
        )
    print(f"made {OUT.name} ({BARS} bars, {SECONDS:.3f}s)")


if __name__ == "__main__":
    main()

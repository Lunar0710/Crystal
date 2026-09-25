"""Soundtrack for the Nexora trailer, synthesised from scratch (no samples, no licences).

120 BPM, so every cut in trailer.html (3.0, 7.0, 12.0, 15.5, 20.0, 24.5, 27.5 s)
lands on a beat. Writes a 48 kHz stereo WAV: python3 music.py out.wav
"""
import sys
import numpy as np
from scipy.signal import butter, sosfilt

SR = 48000
DUR = 32.0
N = int(SR * DUR)
t = np.arange(N) / SR
rng = np.random.default_rng(7)
L = np.zeros(N)
R = np.zeros(N)


def add(sig, start, gain=1.0, pan=0.0):
    """Mix a mono signal in at `start` seconds, panned -1 (left) .. 1 (right)."""
    i = int(start * SR)
    if i >= N:
        return
    sig = sig[: N - i] * gain
    L[i:i + len(sig)] += sig * np.sqrt((1 - pan) / 2) * np.sqrt(2)
    R[i:i + len(sig)] += sig * np.sqrt((1 + pan) / 2) * np.sqrt(2)


def env(n, a, d):
    """Attack, then exponential decay; lengths in seconds."""
    x = np.arange(n) / SR
    return np.minimum(1, x / max(a, 1e-4)) * np.exp(-np.maximum(0, x - a) / d)


def lp(x, f):
    return sosfilt(butter(2, f, 'low', fs=SR, output='sos'), x)


def hp(x, f):
    return sosfilt(butter(2, f, 'high', fs=SR, output='sos'), x)


def bp(x, lo, hi):
    return sosfilt(butter(2, [lo, hi], 'band', fs=SR, output='sos'), x)


def note(freq):
    return 440 * 2 ** ((freq - 69) / 12)


def kick(big=False):
    n = int(SR * (1.2 if big else .45))
    x = np.arange(n) / SR
    f = 45 + (160 if big else 120) * np.exp(-x * 28)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, .002, .5 if big else .16)
    click = hp(rng.standard_normal(n), 3000) * env(n, .0005, .006) * .3
    return np.tanh((body + click) * (1.6 if big else 1.3))


def impact():
    """Low boom plus a noisy crash for the big cuts."""
    n = int(SR * 3.0)
    x = np.arange(n) / SR
    boom = np.sin(2 * np.pi * np.cumsum(38 + 50 * np.exp(-x * 6)) / SR) * env(n, .003, 1.1)
    crash = hp(rng.standard_normal(n), 2500) * env(n, .002, .7) * .35
    return boom * .9 + crash


def hat(open_=False):
    n = int(SR * (.25 if open_ else .06))
    return hp(rng.standard_normal(n), 7000) * env(n, .0005, .08 if open_ else .018)


def riser(length):
    """Noise sweeping up in pitch and loudness, ending on the next cut."""
    n = int(SR * length)
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    steps = 40
    for k in range(steps):  # piecewise band-pass sweep
        a, b = k * n // steps, (k + 1) * n // steps
        c = 300 * (30 ** (k / steps))
        out[a:b] = bp(noise[max(0, a - 2000):b], c * .7, min(c * 1.4, 20000))[-(b - a):]
    ramp = (np.arange(n) / n) ** 2.2
    tone = np.sin(2 * np.pi * np.cumsum(200 * (8 ** (np.arange(n) / n))) / SR) * .12
    return (out * .5 + tone) * ramp


def whoosh(length=.35):
    n = int(SR * length)
    x = np.arange(n) / n
    return bp(rng.standard_normal(n), 800, 6000) * np.sin(np.pi * x) ** 2 * .35


def bell(freq, length=2.5):
    n = int(SR * length)
    x = np.arange(n) / SR
    s = sum(np.sin(2 * np.pi * freq * m * x) * a for m, a in [(1, 1), (2.76, .4), (5.4, .2)])
    return s * env(n, .002, .6)


def pad_chord(notes, length, bright):
    """Detuned saws, low-passed, with a slow swell: the bed under everything."""
    n = int(SR * length)
    x = np.arange(n) / SR
    s = np.zeros(n)
    for m in notes:
        f = note(m)
        for d in (-.12, 0, .12):
            ph = 2 * np.pi * f * (1 + d / 100) * x
            s += sum(np.sin(ph * h) / h for h in range(1, 8))
    s = lp(s / (len(notes) * 3), bright)
    fade = np.minimum(1, x / .6) * np.minimum(1, (length - x) / .6)
    return s * fade


# --- pad: Am  F  C  G, two bars each (4 s), from the headline on ---
chords = [[45, 57, 60, 64], [41, 53, 57, 60], [48, 55, 60, 64], [43, 55, 59, 62]]
start = 2.0
for i in range(8):
    a = start + i * 3.75
    if a >= DUR - .5:
        break
    length = min(4.4, DUR - a)
    bright = 900 + 2600 * min(1, i / 5)
    sig = pad_chord(chords[i % 4], length, bright)
    add(sig, a, .16, -.25)
    add(sig * .9, a + .012, .16, .25)

# --- intro: soft sub drone, the star's bell ---
n = int(SR * 3.2)
drone = np.sin(2 * np.pi * 55 * np.arange(n) / SR) * np.minimum(1, np.arange(n) / SR / 1.5)
add(lp(drone, 200), 0, .25)
add(bell(note(81)), 1.35, .22, .3)
add(bell(note(88), 2), 1.45, .12, -.3)
add(riser(2.6), .4, .35)

# --- cuts ---
for c in (3.0, 7.0, 12.0, 27.5):
    add(impact(), c, .9 if c in (12.0, 27.5) else .7)
    add(kick(True), c, .9)
for c, length in ((7.0, 2.0), (12.0, 2.5), (15.5, 1.5), (20.0, 1.0), (27.5, 2.0)):
    add(riser(length), c - length, .5)

# --- headline: whooshes as the words rise ---
for w in (3.05, 3.15, 3.45):
    add(whoosh(), w, .8, rng.uniform(-.5, .5))

# --- replay: heartbeat kick, ticks on the hits ---
for b in np.arange(7.5, 12.0, 1.0):
    add(kick(), b, .55)
for b in np.arange(8.0, 12.0, .5):
    add(hat(), b + .25, .25, .4)

# --- kill cam: low heartbeat ---
for b in np.arange(12.5, 15.5, .75):
    add(kick(), b, .5)
    add(kick(), b + .18, .3)

# --- montage and FPS: four on the floor, off-beat hats ---
for b in np.arange(15.5, 24.5, .5):
    add(kick(), b, .8)
    add(hat(open_=(int(b * 2) % 4 == 3)), b + .25, .3, rng.uniform(-.3, .3))
for b in np.arange(15.5, 24.5, .125):
    add(hat(), b, .08, .6)
for w in (15.5, 16.25, 17.0, 17.75, 18.5, 19.25):
    add(whoosh(.3), w - .15, .9)

# --- versions: plucks climbing with each version dot ---
scale = [57, 60, 62, 64, 67, 69, 72, 74, 76]
for i, m in enumerate(scale):
    add(bell(note(m), 1.2) * .7, 25.05 + i * .15, .18, -.6 + i * .15)
for b in np.arange(24.5, 27.5, 1.0):
    add(kick(), b, .5)

# --- end card: bell on the star, long tail ---
add(bell(note(81), 3), 28.2, .25, .2)
add(bell(note(76), 3), 28.3, .15, -.2)

# master: gentle glue, fade out, normalise
mix = np.stack([L, R], 1)
mix = np.tanh(mix * 1.2) / np.tanh(1.2)
fade = np.ones(N)
fi = int(SR * 31.0)
fade[fi:] = np.linspace(1, 0, N - fi) ** 1.5
mix *= fade[:, None]
mix *= .89 / np.max(np.abs(mix))

out = sys.argv[1] if len(sys.argv) > 1 else 'music.wav'
import wave
with wave.open(out, 'wb') as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((mix * 32767).astype('<i2').tobytes())
print('wrote', out)

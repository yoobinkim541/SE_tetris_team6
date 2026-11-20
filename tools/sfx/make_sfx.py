"""SE Tetris 효과음·배경음 합성.

외부 샘플은 쓰지 않는다. 16비트 모노 22050 Hz WAV만 만든다.
효과음은 가장자리를 짧게 페이드해 클릭을 없애고, 배경음은 끝과 처음이
이어지게 원형으로 섞어 루프 이음새가 클릭이 되지 않게 한다(끝 페이드를
넣으면 매 바퀴 음량이 꺼진다).

저장소 루트에서:
    python tools/sfx/make_sfx.py
"""

from __future__ import annotations

import sys
import wave
from pathlib import Path

import numpy as np

SR = 22050
NYQUIST_GUARD = SR * 0.46
SHIMMER = 2.0 ** (4.0 / 1200.0)

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUT = ROOT / "src" / "main" / "resources" / "sfx"

# 레이드: 105 BPM, 16마디. 22050 * 60 / 12600 = 105.
RAID_BEAT = 12600
RAID_BARS = 16
RAID_N = RAID_BEAT * 4 * RAID_BARS

# 메뉴: 72 BPM, 12마디 = 정확히 40초. 22050 * 60 / 18375 = 72.
MENU_BEAT = 18375
MENU_BARS = 12
MENU_N = MENU_BEAT * 4 * MENU_BARS


def hz(midi_note: float) -> float:
    return 440.0 * 2.0 ** ((midi_note - 69.0) / 12.0)


def finish(samples: np.ndarray, peak: float = 0.86, fade_ms: float = 4.0) -> np.ndarray:
    """DC를 빼고 양끝을 페이드한 뒤 목표 피크로 맞춘다."""
    audio = np.asarray(samples, dtype=np.float64)
    if audio.size == 0:
        raise RuntimeError("빈 오디오")
    audio = audio - np.mean(audio)
    fade = int(SR * fade_ms / 1000.0)
    fade = max(2, min(fade, audio.size // 4))
    ramp = np.linspace(0.0, 1.0, fade)
    audio[:fade] *= ramp
    audio[-fade:] *= ramp[::-1]
    level = float(np.max(np.abs(audio)))
    if level < 1e-8:
        raise RuntimeError("무음")
    audio *= peak / level
    return np.clip(audio, -1.0, 1.0)


def normalize_loop(samples: np.ndarray, peak: float) -> np.ndarray:
    """루프용. 가장자리를 0으로 내리지 않고 피크만 맞춘다."""
    audio = np.asarray(samples, dtype=np.float64)
    audio = audio - np.mean(audio)
    level = float(np.max(np.abs(audio)))
    if level < 1e-8:
        raise RuntimeError("무음 루프")
    audio *= peak / level
    if not np.isfinite(audio).all():
        raise RuntimeError("루프에 NaN")
    boundary = abs(float(audio[0] - audio[-1]))
    steps = np.abs(np.diff(audio))
    limit = float(np.max(steps)) * 1.05 + 1e-4
    if boundary > limit:
        raise RuntimeError(f"루프 이음새 불연속 {boundary:.5f} > {limit:.5f}")
    return np.clip(audio, -1.0, 1.0)


def add_wrap(buf: np.ndarray, start: int, wave_in: np.ndarray) -> None:
    n = len(buf)
    audio = np.asarray(wave_in, dtype=np.float64)
    m = len(audio)
    if m == 0:
        return
    if m > n:
        raise ValueError(f"음이 루프보다 깁니다: {m} > {n}")
    start %= n
    end = start + m
    if end <= n:
        buf[start:end] += audio
        return
    head = n - start
    buf[start:] += audio[:head]
    buf[: m - head] += audio[head:]


def crystal(
    freq: float,
    n: int,
    decay: float = 6.5,
    brightness: float = 0.4,
    strike: float = 0.08,
    seed: int = 1,
) -> np.ndarray:
    """감쇠하는 배음 + 짧은 타격 잡음. 세트의 공통 종소리."""
    n = int(n)
    t = np.arange(n, dtype=np.float64) / SR
    audio = np.zeros(n, dtype=np.float64)
    partials = (
        (1.0, 1.0, 1.0),
        (2.0, 0.48, 1.35),
        (2.76, 0.22 * (0.5 + brightness), 1.7),
        (4.07, 0.10 * brightness, 2.2),
        (5.4, 0.05 * brightness, 2.8),
    )
    for ratio, amp, decay_mul in partials:
        f = freq * ratio
        if f >= NYQUIST_GUARD:
            continue
        audio += amp * np.sin(2.0 * np.pi * f * t) * np.exp(-decay * decay_mul * t)
    shimmer = freq * 2.0 * SHIMMER
    if shimmer < NYQUIST_GUARD:
        audio += 0.12 * brightness * np.sin(2.0 * np.pi * shimmer * t) * np.exp(-decay * 1.5 * t)
    if strike > 0.0:
        noise_n = min(n, int(0.010 * SR))
        if noise_n > 4:
            rng = np.random.default_rng(seed)
            noise = rng.standard_normal(noise_n)
            noise = np.convolve(noise, np.ones(3) / 3.0, mode="same")
            audio[:noise_n] += strike * noise * np.linspace(1.0, 0.0, noise_n) ** 2
    attack = min(int(0.003 * SR), max(2, n // 5))
    audio[:attack] *= np.linspace(0.0, 1.0, attack)
    return audio


def tone(freq: float, n: int, decay: float) -> np.ndarray:
    t = np.arange(n, dtype=np.float64) / SR
    audio = np.sin(2.0 * np.pi * freq * t)
    audio += 0.22 * np.sin(2.0 * np.pi * freq * 2.0 * t)
    audio *= np.exp(-decay * t)
    attack = min(int(0.002 * SR), max(2, n // 5))
    audio[:attack] *= np.linspace(0.0, 1.0, attack)
    return audio


def chirp(f0: float, f1: float, n: int, decay: float = 4.0) -> np.ndarray:
    t = np.arange(n, dtype=np.float64) / SR
    duration = n / SR
    curve = np.log(f1 / f0)
    phase = f0 * duration / curve * (np.exp(curve * t / duration) - 1.0)
    audio = np.sin(2.0 * np.pi * phase)
    audio += 0.35 * np.sin(4.0 * np.pi * phase)
    audio *= np.exp(-decay * t)
    attack = min(int(0.003 * SR), max(2, n // 5))
    audio[:attack] *= np.linspace(0.0, 1.0, attack)
    return audio


def thud(freq: float, n: int, decay: float = 16.0) -> np.ndarray:
    t = np.arange(n, dtype=np.float64) / SR
    freq_env = freq * np.exp(-14.0 * t)
    phase = np.cumsum(freq_env) / SR
    audio = np.sin(2.0 * np.pi * phase) * np.exp(-decay * t)
    attack = min(int(0.002 * SR), max(2, n // 5))
    audio[:attack] *= np.linspace(0.0, 1.0, attack)
    return audio


def noise_sweep(n: int, f0: float, f1: float, seed: int) -> np.ndarray:
    rng = np.random.default_rng(seed)
    src = rng.standard_normal(n)
    audio = np.empty(n, dtype=np.float64)
    acc = 0.0
    for i in range(n):
        cutoff = f0 + (f1 - f0) * (i / max(n - 1, 1))
        alpha = min(0.95, max(0.02, cutoff / (SR * 0.5)))
        acc += alpha * (src[i] - acc)
        audio[i] = acc
    return audio


def soft_saw(freq: float, n: int, decay: float = 3.2) -> np.ndarray:
    t = np.arange(n, dtype=np.float64) / SR
    audio = np.zeros(n, dtype=np.float64)
    harmonic = 1
    while harmonic <= 10 and freq * harmonic < 2800.0:
        audio += (1.0 / harmonic) * np.sin(2.0 * np.pi * freq * harmonic * t)
        harmonic += 1
    audio *= np.exp(-decay * t)
    attack = min(int(0.008 * SR), max(2, n // 4))
    audio[:attack] *= np.linspace(0.0, 1.0, attack)
    return audio


def hat(n: int, seed: int) -> np.ndarray:
    rng = np.random.default_rng(seed)
    noise = rng.standard_normal(n + 1)
    audio = np.diff(noise)
    audio *= np.linspace(1.0, 0.0, n) ** 3
    return audio


def sfx_move() -> np.ndarray:
    n = int(0.046 * SR)
    t = np.arange(n) / SR
    freq = hz(88) * np.exp(-9.0 * t)  # E6, 살짝 내려앉음
    phase = np.cumsum(freq) / SR
    audio = np.sin(2.0 * np.pi * phase) + 0.2 * np.sin(4.0 * np.pi * phase)
    audio *= np.exp(-36.0 * t)
    return finish(audio, 0.52, 2.2)


def sfx_rotate() -> np.ndarray:
    n = int(0.085 * SR)
    audio = chirp(hz(66), hz(74), n, decay=9.0)  # F#4 → D5
    return finish(audio, 0.62, 2.5)


def sfx_hold() -> np.ndarray:
    n = int(0.16 * SR)
    audio = crystal(hz(86), n, decay=8.0, brightness=0.28, strike=0.04, seed=3)  # D6
    audio += 0.35 * crystal(hz(81), n, decay=7.0, brightness=0.2, strike=0.0, seed=4)
    return finish(audio, 0.68, 3.0)


def sfx_harddrop() -> np.ndarray:
    whoosh_n = int(0.11 * SR)
    body_n = int(0.16 * SR)
    hit = whoosh_n - int(0.02 * SR)
    whoosh = noise_sweep(whoosh_n, 2800.0, 180.0, seed=11)
    whoosh *= np.linspace(0.2, 1.0, whoosh_n) * np.exp(-6.0 * np.arange(whoosh_n) / SR)
    body = thud(78.0, body_n, decay=18.0)
    audio = np.zeros(hit + body_n, dtype=np.float64)
    audio[:whoosh_n] += whoosh * 0.85
    audio[hit : hit + body_n] += body * 1.1
    return finish(audio, 0.8, 3.0)


def sfx_lock() -> np.ndarray:
    n = int(0.07 * SR)
    click_n = int(0.018 * SR)
    t = np.arange(click_n) / SR
    click = np.sin(2.0 * np.pi * 1680.0 * t) * np.exp(-80.0 * t)
    audio = np.zeros(n, dtype=np.float64)
    audio[:click_n] += click
    audio += 0.8 * thud(150.0, n, decay=28.0)
    return finish(audio, 0.6, 2.0)


def sfx_clear1() -> np.ndarray:
    n = int(0.28 * SR)
    return finish(crystal(hz(79), n, decay=5.5, brightness=0.55, seed=21), 0.76, 4.0)


def sfx_clear2() -> np.ndarray:
    tail = int(0.30 * SR)
    step = int(0.09 * SR)
    audio = np.zeros(step + tail, dtype=np.float64)
    audio[:tail] += crystal(hz(79), tail, decay=5.8, brightness=0.5, seed=22)
    audio[step:] += crystal(hz(83), tail, decay=5.4, brightness=0.55, seed=23)
    return finish(audio, 0.78, 4.0)


def sfx_clear3() -> np.ndarray:
    tail = int(0.32 * SR)
    step = int(0.08 * SR)
    notes = (hz(79), hz(83), hz(86))
    audio = np.zeros(step * 2 + tail, dtype=np.float64)
    for i, freq in enumerate(notes):
        audio[i * step : i * step + tail] += crystal(freq, tail, decay=5.2, brightness=0.55, seed=30 + i)
    return finish(audio, 0.8, 4.0)


def sfx_tetris() -> np.ndarray:
    tail = int(0.42 * SR)
    step = int(0.11 * SR)
    notes = (hz(72), hz(76), hz(79), hz(84))
    audio = np.zeros(step * 3 + tail, dtype=np.float64)
    for i, freq in enumerate(notes):
        audio[i * step : i * step + tail] += crystal(freq, tail, decay=4.4, brightness=0.62, seed=40 + i)
    audio[step * 3 :] += 0.55 * crystal(hz(76), tail, decay=4.0, brightness=0.4, strike=0.0, seed=45)
    return finish(audio, 0.86, 5.0)


def sfx_tspin() -> np.ndarray:
    n = int(0.40 * SR)
    t = np.arange(n) / SR
    duration = n / SR
    f0, f1 = 490.0, 1680.0
    inst = f0 * (f1 / f0) ** (t / duration)
    inst *= 1.0 + 0.014 * np.sin(2.0 * np.pi * 24.0 * t)
    phase = np.cumsum(inst) / SR
    audio = np.sin(2.0 * np.pi * phase)
    audio += 0.38 * np.sin(4.0 * np.pi * phase)
    audio += 0.12 * np.sin(2.0 * np.pi * phase * 2.76)
    audio *= np.exp(-3.4 * t)
    spark_n = int(0.05 * SR)
    audio[:spark_n] += 0.25 * hat(spark_n, 77)
    attack = int(0.004 * SR)
    audio[:attack] *= np.linspace(0.0, 1.0, attack)
    return finish(audio, 0.8, 4.0)


def sfx_combo() -> np.ndarray:
    """음이 올라가는 콤보. 재생 쪽에서 피치를 더 올리지 않아도 상승이 들린다."""
    tail = int(0.22 * SR)
    step = int(0.07 * SR)
    notes = (hz(76), hz(79), hz(83), hz(88))
    audio = np.zeros(step * 3 + tail, dtype=np.float64)
    for i, freq in enumerate(notes):
        audio[i * step : i * step + tail] += crystal(
            freq, tail, decay=7.5, brightness=0.7, strike=0.05, seed=50 + i
        )
    return finish(audio, 0.8, 3.5)


def sfx_attack_send() -> np.ndarray:
    n = int(0.20 * SR)
    audio = chirp(320.0, 2100.0, n, decay=6.5)
    audio += 0.35 * noise_sweep(n, 400.0, 2400.0, seed=13) * np.exp(-8.0 * np.arange(n) / SR)
    return finish(audio, 0.78, 3.0)


def sfx_attack_receive() -> np.ndarray:
    n = int(0.26 * SR)
    audio = thud(96.0, n, decay=11.0)
    audio += 0.55 * chirp(640.0, 90.0, n, decay=7.0)
    burst = int(0.04 * SR)
    audio[:burst] += 0.4 * hat(burst, 14)
    return finish(audio, 0.78, 3.0)


def sfx_boss_spawn() -> np.ndarray:
    n = int(1.12 * SR)
    t = np.arange(n) / SR
    freq = 55.0 * (110.0 / 55.0) ** (t / (n / SR))
    phase = np.cumsum(freq) / SR
    drone = np.sin(2.0 * np.pi * phase) * np.linspace(0.15, 1.0, n)
    drone += 0.4 * np.sin(4.0 * np.pi * phase)
    audio = drone * 0.7
    hits = (int(0.28 * SR), int(0.52 * SR), int(0.74 * SR))
    bells = (hz(61), hz(66), hz(73))  # 불협에서 해결로
    for start, freq_hit in zip(hits, bells, strict=True):
        bell = crystal(freq_hit, int(0.34 * SR), decay=4.8, brightness=0.35, strike=0.12, seed=80)
        audio[start : start + len(bell)] += bell * 0.55
    swell = noise_sweep(n, 80.0, 700.0, seed=21) * np.linspace(0.0, 1.0, n) ** 2
    audio += swell * 0.22
    return finish(audio, 0.86, 8.0)


def sfx_boss_attack_warn() -> np.ndarray:
    pulse = int(0.09 * SR)
    gap = int(0.035 * SR)
    parts: list[np.ndarray] = []
    for i in range(4):
        freq = 740.0 if i % 2 == 0 else 880.0
        t = np.arange(pulse) / SR
        beep = np.sin(2.0 * np.pi * freq * t) + 0.28 * np.sin(2.0 * np.pi * freq * 3.0 * t) / 3.0
        beep *= np.exp(-11.0 * t)
        beep[: int(0.003 * SR)] *= np.linspace(0.0, 1.0, int(0.003 * SR))
        parts.append(beep)
        if i < 3:
            parts.append(np.zeros(gap))
    return finish(np.concatenate(parts), 0.78, 3.0)


def sfx_boss_hit() -> np.ndarray:
    n = int(0.18 * SR)
    audio = thud(128.0, n, decay=20.0)
    burst = int(0.045 * SR)
    audio[:burst] += 0.7 * noise_sweep(burst, 1800.0, 240.0, seed=22)
    audio += 0.25 * crystal(hz(67), n, decay=14.0, brightness=0.2, strike=0.0, seed=81)
    return finish(audio, 0.82, 2.5)


def sfx_boss_defeat() -> np.ndarray:
    n = int(1.10 * SR)
    audio = np.zeros(n, dtype=np.float64)
    boom = thud(70.0, int(0.45 * SR), decay=8.0)
    audio[: len(boom)] += boom * 0.7
    tail = int(0.55 * SR)
    step = int(0.12 * SR)
    start = int(0.18 * SR)
    for i, freq in enumerate((hz(57), hz(64), hz(67), hz(69))):
        bell = crystal(freq, tail, decay=3.6, brightness=0.45, strike=0.04, seed=90 + i)
        at = start + i * step
        audio[at : at + len(bell)] += bell * 0.8
    return finish(audio, 0.86, 8.0)


def sfx_critical() -> np.ndarray:
    tail = int(0.16 * SR)
    step = int(0.035 * SR)
    notes = (hz(84), hz(88), hz(91), hz(96))
    audio = np.zeros(step * 3 + tail, dtype=np.float64)
    for i, freq in enumerate(notes):
        audio[i * step : i * step + tail] += crystal(
            freq, tail, decay=9.0, brightness=0.9, strike=0.14, seed=60 + i
        )
    return finish(audio, 0.84, 3.0)


def sfx_revive() -> np.ndarray:
    tail = int(0.42 * SR)
    step = int(0.12 * SR)
    notes = (hz(69), hz(72), hz(76), hz(81))
    audio = np.zeros(step * 3 + tail, dtype=np.float64)
    for i, freq in enumerate(notes):
        audio[i * step : i * step + tail] += crystal(
            freq, tail, decay=3.8, brightness=0.32, strike=0.03, seed=70 + i
        )
    return finish(audio, 0.8, 6.0)


def sfx_ultimate() -> np.ndarray:
    n = int(0.95 * SR)
    audio = np.zeros(n, dtype=np.float64)
    audio += chirp(180.0, 880.0, n, decay=1.6) * np.linspace(0.2, 1.0, n) * 0.45
    audio += 0.3 * noise_sweep(n, 200.0, 1800.0, seed=30) * np.linspace(0.0, 1.0, n)
    stab_at = int(0.62 * SR)
    stab_n = n - stab_at
    for freq, amp in ((hz(57), 0.7), (hz(64), 0.55), (hz(69), 0.7), (hz(73), 0.4), (hz(76), 0.45)):
        audio[stab_at:] += amp * crystal(freq, stab_n, decay=5.5, brightness=0.5, strike=0.1, seed=100 + int(freq))
    audio[: int(0.01 * SR)] *= np.linspace(0.0, 1.0, int(0.01 * SR))
    return finish(audio, 0.88, 6.0)


def sfx_card_flip() -> np.ndarray:
    n = int(0.09 * SR)
    flick = noise_sweep(n, 2200.0, 500.0, seed=40)
    flick *= np.exp(-28.0 * np.arange(n) / SR)
    tick = tone(1860.0, n, decay=40.0)
    return finish(flick * 0.8 + tick * 0.35, 0.58, 2.0)


def sfx_card_pick() -> np.ndarray:
    tail = int(0.26 * SR)
    step = int(0.08 * SR)
    audio = np.zeros(step + tail, dtype=np.float64)
    audio[:tail] += crystal(hz(79), tail, decay=6.0, brightness=0.4, seed=41)
    audio[step:] += crystal(hz(84), tail, decay=5.5, brightness=0.5, seed=42)
    return finish(audio, 0.72, 3.5)


def sfx_countdown() -> np.ndarray:
    n = int(0.15 * SR)
    t = np.arange(n) / SR
    audio = np.sin(2.0 * np.pi * 880.0 * t)
    audio += 0.22 * np.sin(2.0 * np.pi * 1760.0 * t)
    audio *= np.exp(-14.0 * t)
    return finish(audio, 0.7, 2.5)


def sfx_go() -> np.ndarray:
    n = int(0.36 * SR)
    audio = np.zeros(n, dtype=np.float64)
    for freq, amp in ((hz(72), 1.0), (hz(76), 0.8), (hz(79), 0.85), (hz(84), 0.7)):
        audio += amp * crystal(freq, n, decay=6.5, brightness=0.55, strike=0.1, seed=110 + int(freq))
    return finish(audio, 0.86, 4.0)


def sfx_victory() -> np.ndarray:
    tail = int(0.5 * SR)
    step = int(0.13 * SR)
    notes = (hz(72), hz(76), hz(79), hz(84), hz(88))
    audio = np.zeros(step * 4 + tail, dtype=np.float64)
    for i, freq in enumerate(notes):
        audio[i * step : i * step + tail] += crystal(freq, tail, decay=3.8, brightness=0.6, seed=120 + i)
    last = step * 4
    for freq, amp in ((hz(72), 0.4), (hz(76), 0.35), (hz(79), 0.4)):
        audio[last:] += amp * crystal(freq, tail, decay=3.2, brightness=0.35, strike=0.0, seed=130 + int(freq))
    return finish(audio, 0.88, 6.0)


def sfx_defeat() -> np.ndarray:
    tail = int(0.42 * SR)
    step = int(0.16 * SR)
    notes = (hz(81), hz(79), hz(76), hz(72), hz(69))
    audio = np.zeros(step * 4 + tail, dtype=np.float64)
    for i, freq in enumerate(notes):
        audio[i * step : i * step + tail] += crystal(
            freq, tail, decay=3.2, brightness=0.12, strike=0.02, seed=140 + i
        )
    n = len(audio)
    t = np.arange(n) / SR
    audio += 0.25 * np.sin(2.0 * np.pi * hz(45) * t) * np.exp(-1.6 * t)
    return finish(audio, 0.78, 8.0)


def sfx_menu_move() -> np.ndarray:
    n = int(0.034 * SR)
    audio = tone(hz(83), n, decay=48.0)  # B5
    return finish(audio, 0.46, 2.0)


def sfx_menu_select() -> np.ndarray:
    tail = int(0.14 * SR)
    step = int(0.055 * SR)
    audio = np.zeros(step + tail, dtype=np.float64)
    audio[:tail] += crystal(hz(76), tail, decay=10.0, brightness=0.25, strike=0.03, seed=8)
    audio[step:] += crystal(hz(81), tail, decay=9.0, brightness=0.3, strike=0.03, seed=9)
    return finish(audio, 0.62, 2.5)


def pad_segment(freqs: tuple[float, ...], n: int, fade: int) -> np.ndarray:
    t = np.arange(n, dtype=np.float64) / SR
    audio = np.zeros(n, dtype=np.float64)
    for freq in freqs:
        audio += np.sin(2.0 * np.pi * freq * t)
        audio += 0.18 * np.sin(2.0 * np.pi * freq * 2.0 * t)
    audio /= max(len(freqs), 1)
    env = np.ones(n, dtype=np.float64)
    ramp = np.linspace(0.0, 1.0, fade)
    env[:fade] = ramp
    env[-fade:] = ramp[::-1]
    return audio * env


def render_bgm_raid() -> np.ndarray:
    """가벼운 신스웨이브. 2마디마다 Am F C G / Am Dm F G."""
    chords = (
        (hz(45), hz(52), (hz(69), hz(72), hz(76), hz(81))),  # Am
        (hz(41), hz(48), (hz(65), hz(69), hz(72), hz(77))),  # F
        (hz(48), hz(55), (hz(60), hz(64), hz(67), hz(72))),  # C
        (hz(43), hz(50), (hz(67), hz(71), hz(74), hz(79))),  # G
        (hz(45), hz(52), (hz(69), hz(72), hz(76), hz(81))),  # Am
        (hz(38), hz(45), (hz(62), hz(65), hz(69), hz(74))),  # Dm
        (hz(41), hz(48), (hz(65), hz(69), hz(72), hz(77))),  # F
        (hz(43), hz(50), (hz(67), hz(71), hz(74), hz(79))),  # G
    )
    bar = RAID_BEAT * 4
    region = bar * 2
    fade = int(0.09 * SR)
    arp = np.zeros(RAID_N, dtype=np.float64)
    bass = np.zeros(RAID_N, dtype=np.float64)
    pad = np.zeros(RAID_N, dtype=np.float64)
    hats = np.zeros(RAID_N, dtype=np.float64)
    sparkle = np.zeros(RAID_N, dtype=np.float64)
    eighth = RAID_BEAT // 2
    patterns = ((0, 1, 2, 3, 2, 1, 0, 2), (0, 2, 3, 2, 1, 3, 2, 1))
    bell_n = int(0.30 * SR)
    for bar_i in range(RAID_BARS):
        root, fifth, tones = chords[bar_i // 2]
        pattern = patterns[bar_i % 2]
        for step, degree in enumerate(pattern):
            start = bar_i * bar + step * eighth
            amp = 1.05 if step == 0 else (0.78 if step % 2 == 0 else 0.62)
            add_wrap(arp, start, crystal(tones[degree], bell_n, decay=8.0, brightness=0.48, strike=0.04, seed=200 + bar_i * 8 + step) * amp)
            if step % 2 == 0:
                add_wrap(hats, start, hat(int(0.03 * SR), 1000 + bar_i * 8 + step) * (0.9 if step == 0 else 0.55))
        add_wrap(bass, bar_i * bar, soft_saw(root, int(0.42 * SR), decay=3.4) * 1.0)
        add_wrap(bass, bar_i * bar + RAID_BEAT * 2, soft_saw(fifth, int(0.28 * SR), decay=4.0) * 0.75)
        if bar_i % 2 == 0:
            add_wrap(sparkle, bar_i * bar, crystal(hz(88), int(0.22 * SR), decay=9.0, brightness=0.8, strike=0.02, seed=300 + bar_i) * 0.45)
    for index, (_root, _fifth, tones) in enumerate(chords):
        segment = pad_segment(tuple(freq / 2.0 for freq in tones), region + fade, fade)
        add_wrap(pad, index * region - fade, segment * 0.55)
    mixed = arp * 0.62 + bass * 0.30 + pad * 0.24 + hats * 0.055 + sparkle * 0.12
    return normalize_loop(mixed, 0.74)


def render_bgm_menu() -> np.ndarray:
    """잔잔한 패드와 종. 12마디, C–Am–F–G–C–G, 끝 G가 다음 C로 이어진다."""
    chords = (
        (hz(48), (hz(60), hz(64), hz(67))),  # C
        (hz(45), (hz(57), hz(60), hz(64))),  # Am
        (hz(41), (hz(53), hz(57), hz(60))),  # F
        (hz(43), (hz(55), hz(59), hz(62))),  # G
        (hz(48), (hz(60), hz(64), hz(67))),  # C
        (hz(43), (hz(55), hz(59), hz(62))),  # G
    )
    bar = MENU_BEAT * 4
    region = bar * 2
    fade = int(0.14 * SR)
    pad = np.zeros(MENU_N, dtype=np.float64)
    bells = np.zeros(MENU_N, dtype=np.float64)
    bass = np.zeros(MENU_N, dtype=np.float64)
    for index, (root, tones) in enumerate(chords):
        segment = pad_segment(tones, region + fade, fade)
        add_wrap(pad, index * region - fade, segment)
        for bar_i in range(2):
            at = index * region + bar_i * bar
            add_wrap(bells, at, crystal(tones[0] * 2.0, int(1.15 * SR), decay=2.4, brightness=0.22, strike=0.02, seed=400 + index * 2 + bar_i))
            add_wrap(bells, at + MENU_BEAT * 2, crystal(tones[-1] * 2.0, int(0.8 * SR), decay=2.8, brightness=0.16, strike=0.0, seed=420 + index) * 0.55)
            add_wrap(bass, at, soft_saw(root / 2.0, int(1.4 * SR), decay=1.3) * 0.8)
    mixed = pad * 0.50 + bells * 0.34 + bass * 0.22
    return normalize_loop(mixed, 0.62)


CATALOG: list[tuple[str, str, object]] = [
    ("move", "블록 이동 틱", sfx_move),
    ("rotate", "회전. 짧게 올라가는 꺾임", sfx_rotate),
    ("hold", "홀드. 부드러운 종", sfx_hold),
    ("harddrop", "하드드롭. 내려꽂는 소리와 착지", sfx_harddrop),
    ("lock", "착지 클릭", sfx_lock),
    ("clear1", "1줄 삭제. 단일 종", sfx_clear1),
    ("clear2", "2줄 삭제. 두 음", sfx_clear2),
    ("clear3", "3줄 삭제. 세 음", sfx_clear3),
    ("tetris", "4줄 삭제. 장조 팡파르", sfx_tetris),
    ("tspin", "T스핀. 올라가는 반짝임", sfx_tspin),
    ("combo", "콤보. 음이 올라감", sfx_combo),
    ("attack_send", "공격을 보냄", sfx_attack_send),
    ("attack_receive", "공격을 맞음", sfx_attack_receive),
    ("boss_spawn", "보스 등장", sfx_boss_spawn),
    ("boss_attack_warn", "보스 패턴 경고", sfx_boss_attack_warn),
    ("boss_hit", "보스 타격", sfx_boss_hit),
    ("boss_defeat", "보스 처치", sfx_boss_defeat),
    ("critical", "크리티컬 스파클", sfx_critical),
    ("revive", "부활. 올라가는 장조", sfx_revive),
    ("ultimate", "필살기", sfx_ultimate),
    ("card_flip", "카드 뒤집기", sfx_card_flip),
    ("card_pick", "카드 선택", sfx_card_pick),
    ("countdown", "카운트 비프", sfx_countdown),
    ("go", "시작 화음", sfx_go),
    ("victory", "승리 팡파르", sfx_victory),
    ("defeat", "패배. 내려가는 단조", sfx_defeat),
    ("menu_move", "메뉴 이동 틱", sfx_menu_move),
    ("menu_select", "메뉴 결정", sfx_menu_select),
    ("bgm_raid", "레이드 배경음. 신스웨이브 아르페지오 루프", render_bgm_raid),
    ("bgm_menu", "메뉴 배경음. 잔잔한 루프", render_bgm_menu),
]


def write_wav(path: Path, samples: np.ndarray) -> None:
    pcm = np.clip(samples, -1.0, 1.0)
    ints = (pcm * 32767.0).astype("<i2")
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(SR)
        handle.writeframes(ints.tobytes())


def file_stats(path: Path) -> tuple[float, float, float, int]:
    with wave.open(str(path), "rb") as handle:
        if handle.getnchannels() != 1 or handle.getsampwidth() != 2 or handle.getframerate() != SR:
            raise RuntimeError(f"형식 불일치: {path.name}")
        frames = handle.getnframes()
        raw = handle.readframes(frames)
    ints = np.frombuffer(raw, dtype="<i2").astype(np.float64) / 32767.0
    duration = frames / SR
    peak = float(np.max(np.abs(ints))) if ints.size else 0.0
    rms = float(np.sqrt(np.mean(ints * ints))) if ints.size else 0.0
    return duration, peak, rms, path.stat().st_size


def main() -> None:
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUT
    out.mkdir(parents=True, exist_ok=True)
    written: list[str] = []
    for name, _desc, build in CATALOG:
        samples = build()
        path = out / f"{name}.wav"
        write_wav(path, samples)
        written.append(name)
    expected = [name for name, _desc, _build in CATALOG]
    if written != expected:
        raise SystemExit("파일 목록이 카탈로그와 다릅니다")
    total = 0
    print(f"{'file':<22} {'sec':>8} {'peak':>7} {'rms':>7} {'bytes':>10}")
    for name, desc, _build in CATALOG:
        path = out / f"{name}.wav"
        duration, peak, rms, size = file_stats(path)
        total += size
        if peak < 0.2 or rms < 0.01:
            raise SystemExit(f"너무 작음: {name} peak={peak:.3f} rms={rms:.3f}")
        if peak > 0.98:
            raise SystemExit(f"클리핑: {name} peak={peak:.3f}")
        limit = 60.0 if name == "bgm_raid" else 40.0 if name == "bgm_menu" else 2.5
        if duration > limit + 1e-6:
            raise SystemExit(f"길이 초과: {name} {duration:.3f}s > {limit}")
        print(f"{name + '.wav':<22} {duration:8.3f} {peak:7.3f} {rms:7.3f} {size:10d}  {desc}")
    print(f"total_bytes {total} ({total / (1024 * 1024):.2f} MiB)")
    if total > 6 * 1024 * 1024:
        raise SystemExit("sfx 합계가 6 MiB를 넘습니다")
    if MENU_N != 40 * SR:
        raise SystemExit("메뉴 루프가 40초가 아닙니다")
    if RAID_N / SR > 60.0:
        raise SystemExit("레이드 루프가 60초를 넘습니다")


if __name__ == "__main__":
    main()

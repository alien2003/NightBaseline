#!/usr/bin/env python3
"""Fit the navigation spring of the interaction framework (ADR 0011) to the ColorOS back recording.

Input: the per-frame offsets measured from the local frames and recorded in docs/design/reference-analysis.md 8.1
(no raw frames are read here; the frames stay in the git-ignored .data/design-refs/). Model: one scalar p(t), a
spring from rest (free damping ratio zeta, free stiffness k, free start offset t0), and two pure poses of p:

  lower (incoming) page   x_lower(p) = -W/3 * (1 - p)
  upper (outgoing) page   x_upper(p) =  W * (1 - (1 - p) ** gamma)

gamma = 1 is the linear coupling; the fit tests it against the measured 200 ms exit. A cubic-bezier-with-duration
family is fitted as a control. Pure Python (grid search), no dependencies. Prints the best parameters and the rms of
both layers in px.
"""

import math

W = 1140.0  # outer screen width of the recording, px
# Incoming page offsets (px) at times (ms) from the start of the motion (reference-analysis 8.1).
LOWER = [(100.0, -124.0), (150.0, -63.0), (215.0, -24.0), (285.0, -8.0), (350.0, -3.0), (470.0, 0.0)]
# Outgoing page: +41 px in the first frame (16.7 ms), off screen (< 2 px of the page left) at about 200 ms.
UPPER = [(16.7, 41.0), (200.0, W - 1.0)]


def spring_p(t, zeta, omega):
    """Position of a unit spring released from rest at 0 towards 1 (t in s)."""
    if t <= 0.0:
        return 0.0
    if abs(zeta - 1.0) < 1e-6:
        return 1.0 - (1.0 + omega * t) * math.exp(-omega * t)
    if zeta < 1.0:
        wd = omega * math.sqrt(1.0 - zeta * zeta)
        e = math.exp(-zeta * omega * t)
        return 1.0 - e * (math.cos(wd * t) + zeta * omega / wd * math.sin(wd * t))
    s = math.sqrt(zeta * zeta - 1.0)
    r1 = -omega * (zeta - s)
    r2 = -omega * (zeta + s)
    c2 = r1 / (r2 - r1)
    c1 = -1.0 - c2
    return 1.0 + c1 * math.exp(r1 * t) + c2 * math.exp(r2 * t)


def bezier_p(t, x1, y1, x2, y2, duration):
    """Cubic bezier easing (0,0)-(x1,y1)-(x2,y2)-(1,1) over duration seconds."""
    if t <= 0.0:
        return 0.0
    if t >= duration:
        return 1.0
    x = t / duration
    lo, hi = 0.0, 1.0
    for _ in range(40):
        m = (lo + hi) / 2
        bx = 3 * (1 - m) ** 2 * m * x1 + 3 * (1 - m) * m * m * x2 + m ** 3
        if bx < x:
            lo = m
        else:
            hi = m
    m = (lo + hi) / 2
    return 3 * (1 - m) ** 2 * m * y1 + 3 * (1 - m) * m * m * y2 + m ** 3


def lower_x(p):
    return -W / 3.0 * (1.0 - p)


def upper_x(p, gamma):
    return W * (1.0 - max(0.0, 1.0 - p) ** gamma)


def rms(curve, gamma, t0):
    lo = [lower_x(curve((t - t0) / 1000.0)) - x for t, x in LOWER]
    up = [upper_x(curve((t - t0) / 1000.0), gamma) - x for t, x in UPPER]
    return math.sqrt(sum(e * e for e in lo) / len(lo)), math.sqrt(sum(e * e for e in up) / len(up))


def search(gammas, zetas):
    best = None
    for zeta in zetas:
        for k in range(150, 1200, 10):
            omega = math.sqrt(k)
            for t0 in [-10.0 + 2.0 * i for i in range(21)]:
                for gamma in gammas:
                    lo, up = rms(lambda s, z=zeta, o=omega: spring_p(s, z, o), gamma, t0)
                    score = lo * lo + up * up
                    if best is None or score < best[0]:
                        best = (score, zeta, k, t0, gamma, lo, up)
    return best


def fit_bezier():
    best = None
    for x1 in [0.1 * i for i in range(1, 6)]:
        for x2 in [0.1 * i for i in range(0, 5)]:
            for dur in [0.30 + 0.02 * i for i in range(20)]:
                for gamma in [1.0 + 0.2 * i for i in range(16)]:
                    lo, up = rms(lambda s, a=x1, b=x2, d=dur: bezier_p(s, a, 0.0, b, 1.0, d), gamma, 0.0)
                    score = lo * lo + up * up
                    if best is None or score < best[0]:
                        best = (score, x1, x2, dur, gamma, lo, up)
    return best


if __name__ == "__main__":
    zetas = [0.8 + 0.05 * i for i in range(9)]
    lin = search([1.0], zetas)
    print("linear coupling: zeta=%.2f k=%d t0=%.0fms gamma=%.1f rms lower=%.1fpx upper=%.1fpx" % lin[1:])
    crit = search([1.0 + 0.1 * i for i in range(31)], [1.0])
    print("critical + gamma: zeta=%.2f k=%d t0=%.0fms gamma=%.1f rms lower=%.1fpx upper=%.1fpx" % crit[1:])
    free = search([1.0 + 0.1 * i for i in range(31)], zetas)
    print("free zeta + gamma: zeta=%.2f k=%d t0=%.0fms gamma=%.1f rms lower=%.1fpx upper=%.1fpx" % free[1:])
    b = fit_bezier()
    print("bezier control: x1=%.1f x2=%.1f duration=%.2fs gamma=%.1f rms lower=%.1fpx upper=%.1fpx" % b[1:])

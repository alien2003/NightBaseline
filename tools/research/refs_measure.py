#!/usr/bin/env python3
"""Measurement helpers for design reference screenshots (git-ignored .data/design-refs/).

Embeds no reference data: every number comes from the files passed on the command line.
Run with the project venv, isolated:  mise exec -- python -I tools/research/refs_measure.py <cmd> ...

  nodes   <dump.xml> [--density 378] [--text]         uiautomator nodes with bounds in px and dp
                                                       (text is hidden unless --text; it may be personal)
  pixel   <shot.png> x,y [x,y ...]                     sRGB hex of single pixels
  row     <shot.png> y x0 x1 [--step 1]                run-length colours along a pixel row
  col     <shot.png> x y0 y1 [--step 1]                run-length colours along a pixel column
  radius  <shot.png> x0 y0 x1 y1 [--corner tl|tr|bl|br] estimates the corner radius of a filled rounded rect whose
                                                       px bounds are given: for each row near the corner, finds where
                                                       the fill starts, fits a circle to the profile
  ink     <shot.png> x0 y0 x1 y1 [--bg #hex] [--tol 40] vertical extent of "ink" (pixels far from background)
                                                       inside a box: glyph cap/x-height measurement
  mode    <shot.png> x0 y0 x1 y1                       most frequent colour inside a box (surface sampling)
  track   <frame.png ...> --y Y --color #hex [--min 200]   for each video frame (e.g. extracted with ffmpeg), the long runs of
                                                       one colour along row y: follows a sliding card edge over time
"""
import argparse
import math
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter

BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def hexc(p):
    return "#%02X%02X%02X" % tuple(p[:3])


def parse_hex(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def dist(a, b):
    return math.sqrt(sum((int(x) - int(y)) ** 2 for x, y in zip(a[:3], b[:3])))


def flat(im):
    return list(im.get_flattened_data() if hasattr(im, "get_flattened_data") else im.getdata())


def load(path):
    from PIL import Image
    return Image.open(path).convert("RGB")


def cmd_nodes(a):
    k = a.density / 160.0
    root = ET.parse(a.xml).getroot()

    def walk(n, depth):
        b = n.get("bounds")
        if b:
            x0, y0, x1, y1 = map(int, BOUNDS.match(b).groups())
            cls = (n.get("class") or "").rsplit(".", 1)[-1]
            rid = (n.get("resource-id") or "").split("/")[-1]
            desc = n.get("content-desc") or ""
            txt = n.get("text") or ""
            label = txt if a.text else (f"<text:{len(txt)}>" if txt else "")
            flags = "".join(f for f, attr in (("C", "clickable"), ("K", "checkable"), ("S", "scrollable"))
                            if n.get(attr) == "true")
            print(f"{'  ' * depth}{cls} id={rid} px=[{x0},{y0},{x1},{y1}] "
                  f"dp=[{x0 / k:.1f},{y0 / k:.1f},{x1 / k:.1f},{y1 / k:.1f}] "
                  f"w={(x1 - x0) / k:.1f}dp h={(y1 - y0) / k:.1f}dp {flags} "
                  f"{('desc=' + desc) if (desc and a.text) else ''} {label}")
        for c in n:
            walk(c, depth + 1)

    walk(root, 0)


def runs(pixels, coords, tol):
    out = []
    for c, p in zip(coords, pixels):
        if out and dist(out[-1][2], p) <= tol:
            out[-1][1] = c
        else:
            out.append([c, c, p])
    return out


def cmd_row(a):
    im = load(a.png)
    xs = list(range(a.a0, a.a1, a.step))
    for s, e, p in runs([im.getpixel((x, a.fixed)) for x in xs], xs, a.tol):
        print(f"x {s}-{e} ({e - s + 1}px) {hexc(p)}")


def cmd_col(a):
    im = load(a.png)
    ys = list(range(a.a0, a.a1, a.step))
    for s, e, p in runs([im.getpixel((a.fixed, y)) for y in ys], ys, a.tol):
        print(f"y {s}-{e} ({e - s + 1}px) {hexc(p)}")


def cmd_pixel(a):
    im = load(a.png)
    for xy in a.points:
        x, y = map(int, xy.split(","))
        print(f"{x},{y} {hexc(im.getpixel((x, y)))}")


def cmd_mode(a):
    im = load(a.png).crop((a.x0, a.y0, a.x1, a.y1))
    c = Counter(flat(im))
    total = sum(c.values())
    for p, n in c.most_common(5):
        print(f"{hexc(p)} {100 * n / total:.1f}%")


def cmd_radius(a):
    """Fill colour = colour at the box centre row, 40 px in from the edge; outside colour = the box corner pixel.
    For each row from the corner edge inwards, the inset where the colour crosses the 50 % point between outside
    and fill gives the profile d(y); a circle of radius r gives d = r - sqrt(r^2 - (r - y)^2). Least squares over r."""
    im = load(a.png)
    x0, y0, x1, y1 = a.x0, a.y0, a.x1, a.y1
    left = a.corner in ("tl", "bl")
    top = a.corner in ("tl", "tr")
    ey = y0 if top else y1 - 1
    sy = 1 if top else -1
    ex = x0 if left else x1 - 1
    sx = 1 if left else -1
    cy = (y0 + y1) // 2
    fill = im.getpixel((ex + sx * 40, cy))
    out = im.getpixel((ex, ey))  # the bounding-box corner itself lies outside any rounded corner
    span = dist(fill, out) or 1
    prof = []
    for i in range(0, min(120, (y1 - y0) // 2)):
        y = ey + sy * i
        d = None
        for j in range(0, min(200, (x1 - x0) // 2)):
            p = im.getpixel((ex + sx * j, y))
            if dist(p, out) / span >= 0.5:
                d = j
                break
        if d is None:
            break
        prof.append((i + 0.5, d))
        if d == 0:
            break
    best = None
    for r10 in range(20, 2400):
        r = r10 / 10
        err = 0.0
        for yy, d in prof:
            if yy > r:
                pred = 0.0
            else:
                pred = r - math.sqrt(max(0.0, r * r - (r - yy) ** 2))
            err += (pred - d) ** 2
        if best is None or err < best[1]:
            best = (r, err)
    k = a.density / 160.0
    print(f"fill={hexc(fill)} outside={hexc(out)} rows={len(prof)} profile={prof[:12]}...")
    if best:
        print(f"circular fit r={best[0]:.1f}px = {best[0] / k:.1f}dp rms={math.sqrt(best[1] / max(1, len(prof))):.2f}px")
    # smoothed (squircle) corners start curving earlier: report the row where the inset first reaches 0
    zero = next((yy for yy, d in prof if d == 0), None)
    if zero:
        print(f"inset reaches 0 at {zero:.1f}px = {zero / k:.1f}dp from the edge (curve extent)")


def cmd_ink(a):
    im = load(a.png).crop((a.x0, a.y0, a.x1, a.y1))
    w, h = im.size
    bg = parse_hex(a.bg) if a.bg else Counter(flat(im)).most_common(1)[0][0]
    rows = [y for y in range(h) if any(dist(im.getpixel((x, y)), bg) > a.tol for x in range(w))]
    k = a.density / 160.0
    if not rows:
        print("no ink")
        return
    # contiguous blocks
    blocks = []
    for y in rows:
        if blocks and y == blocks[-1][1] + 1:
            blocks[-1][1] = y
        else:
            blocks.append([y, y])
    print(f"bg={hexc(bg)} ink rows {rows[0] + a.y0}-{rows[-1] + a.y0}")
    # glyph colour: most common pixels far from the background (stroke cores, not anti-aliased edges)
    inkc = Counter(px for px in flat(im) if dist(px, bg) > a.tol)
    print("  ink colours: " + ", ".join(f"{hexc(c)}x{n}" for c, n in inkc.most_common(3)))
    for s, e in blocks:
        print(f"  block y {s + a.y0}-{e + a.y0}: {e - s + 1}px = {(e - s + 1) / k:.1f}dp")


def cmd_track(a):
    target = parse_hex(a.color)
    for path in a.frames:
        im = load(path)
        w = im.size[0]
        found, start = [], None
        for x in range(w + 1):
            hit = x < w and dist(im.getpixel((x, a.y)), target) <= a.tol
            if hit and start is None:
                start = x
            elif not hit and start is not None:
                if x - start >= a.min:
                    found.append((start, x - 1))
                start = None
        print(f"{path.rsplit('/', 1)[-1]} " + " ".join(f"[{s0}-{s1}]" for s0, s1 in found))


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--density", type=float, default=378.0, help="dpi used for px->dp (default 378)")
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("nodes"); s.add_argument("xml"); s.add_argument("--text", action="store_true")
    s = sub.add_parser("pixel"); s.add_argument("png"); s.add_argument("points", nargs="+")
    for name in ("row", "col"):
        s = sub.add_parser(name); s.add_argument("png"); s.add_argument("fixed", type=int)
        s.add_argument("a0", type=int); s.add_argument("a1", type=int)
        s.add_argument("--step", type=int, default=1); s.add_argument("--tol", type=float, default=6)
    for name in ("radius", "ink", "mode"):
        s = sub.add_parser(name); s.add_argument("png")
        for c in ("x0", "y0", "x1", "y1"):
            s.add_argument(c, type=int)
        if name == "radius":
            s.add_argument("--corner", default="tl", choices=["tl", "tr", "bl", "br"])
        if name == "ink":
            s.add_argument("--bg"); s.add_argument("--tol", type=float, default=40)
    s = sub.add_parser("track"); s.add_argument("frames", nargs="+"); s.add_argument("--y", type=int, required=True)
    s.add_argument("--color", required=True); s.add_argument("--min", type=int, default=200)
    s.add_argument("--tol", type=float, default=6)
    a = p.parse_args()
    {"nodes": cmd_nodes, "pixel": cmd_pixel, "row": cmd_row, "col": cmd_col, "radius": cmd_radius,
     "ink": cmd_ink, "mode": cmd_mode, "track": cmd_track}[a.cmd](a)


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""WCAG contrast check for NightBaseline design tokens.

Reads `docs/design/tokens.md` (or the file given as the first argument) and checks every row of the
contrast pairs table against the WCAG 2.x contrast ratio. Standard library only, no embedded colours:
every value comes from the Markdown file.

Formats understood (see tokens.md section "Contrast pairs"):

* Colour tables: any Markdown table whose header contains the columns `token`, `light` and `dark`.
  A value cell is `#RRGGBB`, `#RRGGBB @ NN%` (translucent, NN = alpha in percent) or `= other.token`
  (alias). Backticks are ignored. Any other value in such a table is an error.
* Pairs table: a Markdown table whose header is exactly `| pair | fg | bg | theme | min |`.
  - `pair`: name; its first dot-separated part is the kind and fixes the lowest allowed threshold:
    `text` >= 4.5, `large` (text >= 24 sp, or >= 19 sp bold) >= 3.0, `graphic` >= 3.0, `focus` >= 3.0.
  - `fg`, `bg`: a token name or a literal `#RRGGBB` / `#RRGGBB @ NN%`.
  - `theme`: `light`, `dark` or `both`.
  - `min`: the required ratio; it may be stricter than the kind's rule but never lower.
* Foreground colours must be opaque. A translucent background is a glass tint: the backdrop under it is
  unknown, so it is composited over both pure white and pure black and the worse ratio counts.
* Coverage: every text or on-colour token (`color.text.*`, or any name segment like `onPrimary`,
  `onGlass`, `onContainer`) must be the foreground of at least one pair.

Exit status: 0 when every pair passes, 1 on any failure or format error.

Usage: python3 -I tools/design/contrast_check.py [path/to/tokens.md]
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

KIND_MIN = {"text": 4.5, "large": 3.0, "graphic": 3.0, "focus": 3.0}
BACKDROPS = (("white", (255, 255, 255)), ("black", (0, 0, 0)))
THEMES = ("light", "dark")

HEX_RE = re.compile(r"#([0-9A-Fa-f]{6})(?:\s*@\s*(\d+(?:\.\d+)?)\s*%)?")
ALIAS_RE = re.compile(r"=\s*([A-Za-z][\w.]*)")


@dataclass(frozen=True)
class Color:
    rgb: tuple[int, int, int]
    alpha: float  # 0..1

    @property
    def opaque(self) -> bool:
        return self.alpha >= 0.9999

    def hex(self) -> str:
        h = "#%02X%02X%02X" % self.rgb
        return h if self.opaque else f"{h}@{round(self.alpha * 100, 1):g}%"


class FormatError(Exception):
    pass


def parse_literal(text: str) -> Color | None:
    m = HEX_RE.fullmatch(text.strip())
    if not m:
        return None
    raw = m.group(1)
    rgb = (int(raw[0:2], 16), int(raw[2:4], 16), int(raw[4:6], 16))
    alpha = float(m.group(2)) / 100.0 if m.group(2) is not None else 1.0
    if not 0.0 < alpha <= 1.0:
        raise FormatError(f"alpha out of range in {text!r}")
    return Color(rgb, alpha)


def clean(cell: str) -> str:
    return cell.replace("`", "").strip()


def iter_tables(lines: list[str]):
    """Yield (start_line_no, header_cells, rows) for every Markdown table."""
    i = 0
    while i < len(lines):
        if lines[i].lstrip().startswith("|"):
            start = i
            block = []
            while i < len(lines) and lines[i].lstrip().startswith("|"):
                block.append(lines[i].strip())
                i += 1
            cells = [split_row(r) for r in block]
            if len(cells) >= 2 and all(re.fullmatch(r":?-{3,}:?", c.strip()) for c in cells[1] if c.strip()):
                yield start + 1, [clean(c).lower() for c in cells[0]], [(start + 3 + k, r) for k, r in enumerate(cells[2:])]
        else:
            i += 1


def split_row(row: str) -> list[str]:
    row = row.strip()
    if row.startswith("|"):
        row = row[1:]
    if row.endswith("|"):
        row = row[:-1]
    return [c.strip() for c in row.split("|")]


def parse(path: Path):
    lines = path.read_text(encoding="utf-8").splitlines()
    tokens: dict[str, dict[str, str]] = {}  # name -> theme -> raw value
    pairs = []
    errors: list[str] = []
    for line_no, header, rows in iter_tables(lines):
        if header == ["pair", "fg", "bg", "theme", "min"]:
            for row_no, cells in rows:
                if len(cells) != 5:
                    errors.append(f"line {row_no}: pairs row needs 5 cells, has {len(cells)}")
                    continue
                pairs.append((row_no, *[clean(c) for c in cells]))
        elif "token" in header and "light" in header and "dark" in header:
            ti, li, di = header.index("token"), header.index("light"), header.index("dark")
            for row_no, cells in rows:
                if len(cells) <= max(ti, li, di):
                    errors.append(f"line {row_no}: colour row is too short")
                    continue
                name = clean(cells[ti])
                if name in tokens:
                    errors.append(f"line {row_no}: token {name} defined twice")
                    continue
                values = {}
                for theme, idx in (("light", li), ("dark", di)):
                    raw = clean(cells[idx])
                    if not (HEX_RE.fullmatch(raw) or ALIAS_RE.fullmatch(raw)):
                        errors.append(f"line {row_no}: {name} {theme} value {raw!r} is not #RRGGBB, '#RRGGBB @ NN%' or '= token'")
                    values[theme] = raw
                tokens[name] = values
    return tokens, pairs, errors


def resolve(ref: str, theme: str, tokens, seen=()) -> Color:
    lit = parse_literal(ref)
    if lit is not None:
        return lit
    if ref not in tokens:
        raise FormatError(f"unknown token {ref!r}")
    if ref in seen:
        raise FormatError(f"alias cycle: {' -> '.join(seen + (ref,))}")
    raw = tokens[ref][theme]
    m = ALIAS_RE.fullmatch(raw)
    if m:
        return resolve(m.group(1), theme, tokens, seen + (ref,))
    lit = parse_literal(raw)
    if lit is None:
        raise FormatError(f"bad value for {ref} ({theme}): {raw!r}")
    return lit


def linear(c: int) -> float:
    s = c / 255.0
    return s / 12.92 if s <= 0.04045 else ((s + 0.055) / 1.055) ** 2.4


def luminance(rgb: tuple[int, int, int]) -> float:
    r, g, b = (linear(v) for v in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(a: tuple[int, int, int], b: tuple[int, int, int]) -> float:
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def over(top: Color, bottom: tuple[int, int, int]) -> tuple[int, int, int]:
    """Source-over compositing in sRGB space (how Android blends a translucent fill)."""
    return tuple(round(t * top.alpha + b * (1.0 - top.alpha)) for t, b in zip(top.rgb, bottom))


def check(path: Path) -> int:
    tokens, pairs, errors = parse(path)
    if not tokens:
        errors.append("no colour tables found (header with token / light / dark)")
    if not pairs:
        errors.append("no pairs table found (header | pair | fg | bg | theme | min |)")

    results = []
    used_fg: set[str] = set()
    for row_no, name, fg_ref, bg_ref, theme_cell, min_cell in pairs:
        kind = name.split(".", 1)[0]
        if kind not in KIND_MIN:
            errors.append(f"line {row_no}: pair {name!r}: kind {kind!r} is not one of {sorted(KIND_MIN)}")
            continue
        try:
            required = float(min_cell)
        except ValueError:
            errors.append(f"line {row_no}: pair {name!r}: min {min_cell!r} is not a number")
            continue
        if required < KIND_MIN[kind]:
            errors.append(f"line {row_no}: pair {name!r}: min {required} is below the {kind} rule {KIND_MIN[kind]}")
            continue
        if theme_cell not in ("light", "dark", "both"):
            errors.append(f"line {row_no}: pair {name!r}: theme {theme_cell!r} must be light, dark or both")
            continue
        used_fg.add(fg_ref)
        for theme in THEMES if theme_cell == "both" else (theme_cell,):
            try:
                fg = resolve(fg_ref, theme, tokens)
                bg = resolve(bg_ref, theme, tokens)
            except FormatError as e:
                errors.append(f"line {row_no}: pair {name!r} ({theme}): {e}")
                continue
            if not fg.opaque:
                errors.append(f"line {row_no}: pair {name!r} ({theme}): foreground {fg_ref} must be opaque")
                continue
            if bg.opaque:
                worst, where = ratio(fg.rgb, bg.rgb), "solid"
            else:
                worst, where = min(
                    (ratio(fg.rgb, over(bg, rgb)), f"over {label} = #%02X%02X%02X" % over(bg, rgb))
                    for label, rgb in BACKDROPS
                )
            results.append((worst >= required, name, theme, worst, required, fg.hex(), bg.hex(), where))

    for name in sorted(tokens):
        segments = name.split(".")
        is_text = "text" in segments[:2] or any(re.fullmatch(r"on[A-Z]\w*", s) for s in segments)
        if is_text and name not in used_fg:
            errors.append(f"coverage: {name} is a text/on-colour token but is the foreground of no pair")

    width = max((len(r[1]) for r in results), default=10)
    for ok, name, theme, value, required, fg, bg, where in results:
        print(f"{'PASS' if ok else 'FAIL'}  {name:<{width}}  {theme:<5}  {value:6.2f} >= {required:<4}  fg {fg} bg {bg} ({where})")
    failed = [r for r in results if not r[0]]
    for e in errors:
        print(f"ERROR {e}")
    print(f"\n{len(results)} checks, {len(results) - len(failed)} passed, {len(failed)} failed, {len(errors)} errors "
          f"({len(tokens)} colour tokens, {len(pairs)} pair rows)")
    return 1 if failed or errors else 0


def main(argv: list[str]) -> int:
    default = Path(__file__).resolve().parents[2] / "docs" / "design" / "tokens.md"
    path = Path(argv[1]) if len(argv) > 1 else default
    if not path.is_file():
        print(f"ERROR tokens file not found: {path}")
        return 1
    return check(path)


if __name__ == "__main__":
    sys.exit(main(sys.argv))

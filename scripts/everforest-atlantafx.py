#!/usr/bin/env python3
"""Generates the Everforest override stylesheets for AtlantaFX.

    python3 scripts/everforest-atlantafx.py

Writes `everforest-light.css` and `everforest-dark.css` into
`app/src/main/resources/com/contextswitcher/ui/`. Those files are derived
data — edit this script, not them.

An AtlantaFX theme resolves every colour through the 113 lookup variables of
its `.root` block; outside that block its stylesheet holds no literal colour
at all. So a `.root` block appended after the theme's own recolours the whole
UI, dialogs included, without touching AtlantaFX's SASS sources. See
`dsn~everforest-theme~1` and MADR 0025.

Palette: https://github.com/sainnhe/everforest/blob/master/palette.md
(Dark Medium and Light Medium).
"""

from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app/src/main/resources/com/contextswitcher/ui"


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def hex_of(c):
    return "#%02x%02x%02x" % tuple(max(0, min(255, round(v))) for v in c)


def mix(a, b, t):
    """`a` moved `t` of the way towards `b` (t=0 → a, t=1 → b)."""
    return tuple(x + (y - x) * t for x, y in zip(rgb(a) if isinstance(a, str) else a,
                                                 rgb(b) if isinstance(b, str) else b))


def ramp(seed, light_end, dark_end, slope):
    """AtlantaFX's 10-step ramp: index 0 lightest, 5 the seed, 9 darkest.

    The same shape both themes use — only which end the semantic variables
    read from flips between light and dark mode. `slope` is how far index 9
    travels towards `dark_end`: the light theme needs a steeper one, because
    Everforest's light hues are bright enough that Nord's gentle slope never
    reaches a shade that carries pale text (see the contrast check below).
    """
    steps = []
    for i in range(5):
        steps.append(hex_of(mix(seed, light_end, (5 - i) / 5 * 0.85)))
    steps.append(seed)
    for i in range(6, 10):
        steps.append(hex_of(mix(seed, dark_end, (i - 5) / 4 * slope)))
    return steps


def luminance(c):
    """WCAG relative luminance."""
    out = 0.0
    for channel, weight in zip(rgb(c) if isinstance(c, str) else c, (0.2126, 0.7152, 0.0722)):
        s = channel / 255
        out += weight * (s / 12.92 if s <= 0.03928 else ((s + 0.055) / 1.055) ** 2.4)
    return out


def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    lo, hi = min(la, lb), max(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def rgba(color, alpha):
    r, g, b = rgb(color) if isinstance(color, str) else color
    return f"rgba({round(r)}, {round(g)}, {round(b)}, {alpha})"


# Everforest, medium contrast. Neutral ramps run lightest → darkest, as
# AtlantaFX expects, and are Everforest's own named steps rather than an
# interpolation: bg0…bg5 then grey0…grey2 then fg for light, the reverse
# walk for dark.
LIGHT = {
    "neutral": ["#fdf6e3", "#f4f0d9", "#efebd4", "#e6e2cc", "#e0dcc7",
                "#bdc3af", "#a6b0a0", "#939f91", "#829181", "#5c6a72"],
    "light_end": "#fdf6e3",   # bg0
    "dark_end": "#2f3831",    # darker than fg: the ramps need somewhere to go
    "slope": 0.75,
    "accent": "#8da101",      # green — Everforest's signature hue
    "success": "#35a77c",     # aqua, so success never reads as the accent
    "warning": "#dfa000",     # yellow
    "danger": "#f85552",      # red
    "chart": ["#8da101", "#3a94c5", "#f85552", "#dfa000",
              "#df69ba", "#35a77c", "#f57d26", "#829181"],
}

DARK = {
    "neutral": ["#d3c6aa", "#9da9a0", "#859289", "#7a8478", "#56635f",
                "#4f585e", "#475258", "#3d484d", "#343f44", "#2d353b"],
    "light_end": "#d3c6aa",   # fg
    "dark_end": "#232a2e",    # bg_dim
    "slope": 0.55,
    "accent": "#a7c080",
    "success": "#83c092",
    "warning": "#dbbc7f",
    "danger": "#e67e80",
    "chart": ["#a7c080", "#7fbbb3", "#e67e80", "#dbbc7f",
              "#d699b6", "#83c092", "#e69875", "#9da9a0"],
}


def build(p, dark):
    n = p["neutral"]
    ramps = {name: ramp(p[name], p["light_end"], p["dark_end"], p["slope"])
             for name in ("accent", "success", "warning", "danger")}
    # Which end of a ramp the semantic variables read from, and how far a
    # "muted"/"subtle" tint is blended into the background — both lifted from
    # what Nord does, so AtlantaFX's own contrast intent survives the swap.
    if dark:
        bg = n[9]
        pick, emphasis, muted_t, subtle_t = 2, 5, 0.44, 0.85
        semantic = {
            "-color-dark": p["dark_end"],
            "-color-light": p["light_end"],
            "-color-fg-default": n[0],
            "-color-fg-muted": n[1],
            "-color-fg-subtle": n[3],
            # Text on an emphasis (accent/danger/…) fill. Everforest's dark
            # hues are light ones, so that text has to be dark, not white.
            "-color-fg-emphasis": p["dark_end"],
            "-color-bg-default": n[9],
            "-color-bg-overlay": n[8],
            "-color-bg-subtle": n[8],
            "-color-bg-inset": p["dark_end"],
            "-color-border-default": n[5],
            "-color-border-muted": n[6],
            "-color-border-subtle": n[7],
            "-color-shadow-default": p["dark_end"],
            "-color-neutral-emphasis-plus": n[5],
            "-color-neutral-emphasis": n[6],
        }
    else:
        bg = n[0]
        # Two steps deeper than Nord picks: Everforest's light hues are
        # bright, so its emphasis fill has to sit further down the ramp to
        # carry the cream `fg-emphasis` text.
        pick, emphasis, muted_t, subtle_t = 8, 9, 0.60, 0.90
        semantic = {
            "-color-dark": p["dark_end"],
            "-color-light": p["light_end"],
            "-color-fg-default": n[9],
            "-color-fg-muted": n[8],
            "-color-fg-subtle": n[6],
            "-color-fg-emphasis": n[0],
            "-color-bg-default": n[0],
            "-color-bg-overlay": n[0],
            "-color-bg-subtle": n[1],
            "-color-bg-inset": n[2],
            "-color-border-default": n[5],
            "-color-border-muted": n[4],
            "-color-border-subtle": n[3],
            "-color-shadow-default": n[5],
            "-color-neutral-emphasis-plus": n[9],
            "-color-neutral-emphasis": n[8],
        }
    semantic["-color-neutral-muted"] = hex_of(mix(n[6], bg, muted_t))
    semantic["-color-neutral-subtle"] = hex_of(mix(n[6], bg, subtle_t))
    for name, steps in ramps.items():
        semantic[f"-color-{name}-fg"] = steps[pick]
        semantic[f"-color-{name}-emphasis"] = steps[emphasis]
        # The shade a tint is blended from: dark mode tints its emphasis
        # shade, light mode a lighter step — Nord's own choice.
        tint = steps[emphasis] if dark else steps[emphasis - 5]
        semantic[f"-color-{name}-muted"] = hex_of(mix(tint, bg, muted_t))
        semantic[f"-color-{name}-subtle"] = hex_of(mix(tint, bg, subtle_t))

    # Accessibility floor. AtlantaFX puts `fg-emphasis` on every emphasis
    # fill (default button, badges, selected rows) and `X-fg` on links and
    # status text over the plain background — both are body text and owe
    # WCAG AA. A palette swap is exactly where that quietly breaks, so it
    # fails here rather than on someone's screen.
    checks = [("fg-default on bg-default", semantic["-color-fg-default"], semantic["-color-bg-default"])]
    for name in ramps:
        checks.append((f"fg-emphasis on {name}-emphasis",
                       semantic["-color-fg-emphasis"], semantic[f"-color-{name}-emphasis"]))
        checks.append((f"{name}-fg on bg-default",
                       semantic[f"-color-{name}-fg"], semantic["-color-bg-default"]))
    failed = []
    for label, a, b in checks:
        ratio = contrast(a, b)
        mark = "ok " if ratio >= 4.5 else "LOW"
        print(f"    {mark} {ratio:5.2f}  {label}")
        if ratio < 4.5:
            failed.append(f"{label} = {ratio:.2f}")
    if failed:
        raise SystemExit("contrast below WCAG AA 4.5:1 — " + "; ".join(failed))

    lines = []
    lines.append(f"/* Everforest {'Dark' if dark else 'Light'} Medium for AtlantaFX —"
                 " GENERATED, do not edit. */")
    lines.append("/* Regenerate with: python3 scripts/everforest-atlantafx.py"
                 "   (dsn~everforest-theme~1) */")
    lines.append("")
    lines.append(".root {")
    lines.append("")
    lines.append("  /* Base ramps: 0 lightest … 9 darkest. */")
    for i, c in enumerate(n):
        lines.append(f"  -color-base-{i}: {c};")
    for name in ("accent", "success", "warning", "danger"):
        lines.append("")
        for i, c in enumerate(ramps[name]):
            lines.append(f"  -color-{name}-{i}: {c};")
    lines.append("")
    lines.append("  /* Semantic colours. */")
    for k, v in semantic.items():
        lines.append(f"  {k}: {v};")
    lines.append("")
    lines.append("  /* Chart series. */")
    for i, c in enumerate(p["chart"], start=1):
        lines.append(f"  -color-chart-{i}: {c};")
    for alpha, suffix in ((0.7, "alpha70"), (0.2, "alpha20")):
        lines.append("")
        for i, c in enumerate(p["chart"], start=1):
            lines.append(f"  -color-chart-{i}-{suffix}: {rgba(c, alpha)};")
    lines.append("}")
    return "\n".join(lines) + "\n"


for name, palette, is_dark in (("everforest-light.css", LIGHT, False),
                               ("everforest-dark.css", DARK, True)):
    (OUT / name).write_text(build(palette, is_dark), encoding="utf-8")
    print("wrote", OUT / name)

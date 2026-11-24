"""Illustration set for the health app.

One source of truth: each illustration is a list of layers (token, alpha, path, stroke width).
Usage (from this folder):
  python gen.py --kotlin ../../app/src/main/java/com/healthapp/presentation/ui/theme/Illustrations.kt
  python gen.py --gallery        # gallery.html: light + dark preview for design review
Outputs Compose ImageVectors whose colours come from HealthPalette at runtime.
Tokens: BG = main accent soft, M = main strong, M2 = main light (gradient start), S = second strong,
        SS = second soft, P = paper (white / dark surface), K = ink, G = muted grey.
"""
import math, os, sys

OUT = os.path.dirname(os.path.abspath(__file__))
W, H = 200, 160

def f(x):  # compact number
    return ("%.1f" % x).rstrip("0").rstrip(".")

def circle(cx, cy, r):
    return f"M{f(cx-r)} {f(cy)}a{f(r)} {f(r)} 0 1 0 {f(2*r)} 0a{f(r)} {f(r)} 0 1 0 {f(-2*r)} 0Z"

def ellipse(cx, cy, rx, ry):
    return f"M{f(cx-rx)} {f(cy)}a{f(rx)} {f(ry)} 0 1 0 {f(2*rx)} 0a{f(rx)} {f(ry)} 0 1 0 {f(-2*rx)} 0Z"

def rrect(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    return (f"M{f(x+r)} {f(y)}H{f(x+w-r)}a{f(r)} {f(r)} 0 0 1 {f(r)} {f(r)}V{f(y+h-r)}"
            f"a{f(r)} {f(r)} 0 0 1 {f(-r)} {f(r)}H{f(x+r)}a{f(r)} {f(r)} 0 0 1 {f(-r)} {f(-r)}V{f(y+r)}"
            f"a{f(r)} {f(r)} 0 0 1 {f(r)} {f(-r)}Z")

def heart(cx, cy, s):
    # heart of width ~2s, top at cy - .55s, tip at cy + s
    return (f"M{f(cx)} {f(cy+s)}C{f(cx-.35*s)} {f(cy+.7*s)} {f(cx-1.05*s)} {f(cy+.25*s)} {f(cx-1.05*s)} {f(cy-.2*s)}"
            f"C{f(cx-1.05*s)} {f(cy-.75*s)} {f(cx-.35*s)} {f(cy-.95*s)} {f(cx)} {f(cy-.45*s)}"
            f"C{f(cx+.35*s)} {f(cy-.95*s)} {f(cx+1.05*s)} {f(cy-.75*s)} {f(cx+1.05*s)} {f(cy-.2*s)}"
            f"C{f(cx+1.05*s)} {f(cy+.25*s)} {f(cx+.35*s)} {f(cy+.7*s)} {f(cx)} {f(cy+s)}Z")

def poly(*pts):
    return "M" + "L".join(f"{f(x)} {f(y)}" for x, y in pts)

def sparkle(cx, cy, r):
    k = r * .28
    return (f"M{f(cx)} {f(cy-r)}Q{f(cx+k)} {f(cy-k)} {f(cx+r)} {f(cy)}Q{f(cx+k)} {f(cy+k)} {f(cx)} {f(cy+r)}"
            f"Q{f(cx-k)} {f(cy+k)} {f(cx-r)} {f(cy)}Q{f(cx-k)} {f(cy-k)} {f(cx)} {f(cy-r)}Z")

def leaf(x, y, length, angle):
    a = math.radians(angle)
    ex, ey = x + length * math.cos(a), y + length * math.sin(a)
    nx, ny = -math.sin(a) * length * .38, math.cos(a) * length * .38
    mx, my = (x + ex) / 2, (y + ey) / 2
    return f"M{f(x)} {f(y)}Q{f(mx+nx)} {f(my+ny)} {f(ex)} {f(ey)}Q{f(mx-nx)} {f(my-ny)} {f(x)} {f(y)}Z"

def blob(dx=0, dy=0, s=1.0):
    pts = [(100, 16), (178, 58), (170, 122), (96, 148), (24, 118), (22, 52)]
    pts = [(100 + (x - 100) * s + dx, 82 + (y - 82) * s + dy) for x, y in pts]
    d = f"M{f(pts[0][0])} {f(pts[0][1])}"
    n = len(pts)
    for i in range(n):  # smooth closed curve (Catmull-Rom -> Bezier)
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[(i + 1) % n], pts[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += f"C{f(c1[0])} {f(c1[1])} {f(c2[0])} {f(c2[1])} {f(p2[0])} {f(p2[1])}"
    return d + "Z"

def L(token, d, alpha=1.0, stroke=0.0):
    return (token, alpha, d, stroke)

def base(dx=0, dy=0, s=1.0):
    return [L("BG", blob(dx, dy, s)), L("SS", circle(36, 34, 7), .9), L("S", sparkle(168, 30, 7), .7), L("M2", circle(170, 132, 4), .6)]

ILLOS = {}

# 1 Welcome: phone with a heart and pulse, leaves
ILLOS["WELCOME"] = ("emerald", "coral", base() + [
    L("M", leaf(64, 118, 34, -120)), L("M2", leaf(60, 122, 30, -150), .9), L("M", leaf(140, 120, 30, -55), .8),
    L("K", rrect(72, 26, 58, 108, 12), .12),
    L("P", rrect(70, 22, 58, 108, 12)),
    L("M", rrect(70, 22, 58, 108, 12), 1, 3),
    L("G", rrect(91, 28, 16, 3, 1.5), .6),
    L("S", heart(99, 62, 16)),
    L("P", poly((82, 98), (90, 98), (95, 88), (101, 108), (106, 94), (116, 94)), 1, 0),
    L("M", poly((80, 98), (90, 98), (95, 88), (101, 108), (106, 94), (118, 94)), 1, 3.5),
    L("G", rrect(84, 114, 30, 5, 2.5), .5),
])

# 2 About you: profile card with avatar and calendar badge
ILLOS["ABOUT"] = ("pink", "amber", base(4, 0) + [
    L("K", rrect(48, 42, 104, 76, 12), .1),
    L("P", rrect(46, 38, 104, 76, 12)),
    L("M", circle(78, 70, 15)),
    L("P", circle(78, 66, 6)),
    L("P", "M68 80Q78 70 88 80Z"),
    L("G", rrect(100, 58, 38, 6, 3), .7), L("G", rrect(100, 70, 28, 6, 3), .45), L("G", rrect(60, 96, 76, 6, 3), .3),
    L("S", rrect(126, 86, 36, 34, 8)),
    L("P", rrect(130, 96, 28, 20, 4), .95),
    L("S", rrect(133, 82, 4, 9, 2)), L("S", rrect(151, 82, 4, 9, 2)),
    L("SS", circle(144, 106, 4)),
])

# 3 Body: bathroom scale, measuring tape, sneaker hint
ILLOS["BODY"] = ("emerald", "sky", base(-2, 2) + [
    L("K", rrect(52, 64, 92, 70, 18), .1),
    L("P", rrect(50, 60, 92, 70, 18)),
    L("M", rrect(50, 60, 92, 70, 18), 1, 3),
    L("M", rrect(76, 72, 40, 22, 8)),
    L("P", rrect(82, 78, 28, 10, 4), .9),
    L("M", poly((96, 84), (104, 79)), 1, 2.5),
    L("G", ellipse(76, 110, 10, 6), .35), L("G", ellipse(116, 110, 10, 6), .35),
    L("K", rrect(152, 30, 18, 100, 5), .1),
    L("S", rrect(150, 26, 18, 100, 5)),
    L("P", "M150 40h8M150 52h5M150 64h8M150 76h5M150 88h8M150 100h5M150 112h8", .95, 2),
])

# 4 Goals: target with arrow and water drop
ILLOS["GOALS"] = ("orange", "sky", base(2, -2) + [
    L("P", circle(92, 84, 46)),
    L("M", circle(92, 84, 46), 1, 3),
    L("M", circle(92, 84, 32), .35),
    L("P", circle(92, 84, 20)),
    L("M", circle(92, 84, 10)),
    L("K", poly((94, 82), (138, 40)), .85, 3.5),
    L("M", "M138 40l2-14 10 6-6 10z"),
    L("M", "M138 40l14-2-6 10-10-4z", .7),
    L("S", "M150 96c0 0-12 13-12 21a12 12 0 0 0 24 0c0-8-12-21-12-21z"),
    L("P", "M145 116a5 5 0 0 0 5 5", .8, 2.2),
])

# 5 Medication (empty): pill bottle, capsule, clock
ILLOS["MEDS"] = ("indigo", "amber", base() + [
    L("K", rrect(60, 46, 54, 86, 10), .1),
    L("M", rrect(58, 52, 54, 80, 10)),
    L("K", rrect(54, 36, 62, 20, 6), .8),
    L("P", rrect(64, 74, 42, 34, 5)),
    L("M", rrect(80, 80, 10, 22, 2), .9), L("M", rrect(74, 86, 22, 10, 2), .9),
    L("S", "M124 112l14-14a9 9 0 0 1 13 13l-14 14a9 9 0 0 1-13-13z"),
    L("P", "M131 105l13 13", .9, 2.4),
    L("P", circle(146, 62, 20)), L("S", circle(146, 62, 20), 1, 3),
    L("K", poly((146, 50), (146, 62), (155, 68)), .85, 3),
])

# 6 Meals (empty): bowl of rice, chopsticks, steam, leaf
ILLOS["MEALS"] = ("orange", "emerald", base(0, 3) + [
    L("G", "M82 40c-6 8 6 12 0 20M100 34c-6 8 6 12 0 20M118 40c-6 8 6 12 0 20", .5, 3),
    L("K", poly((128, 54), (168, 30)), .75, 4), L("K", poly((132, 62), (172, 40)), .75, 4),
    L("P", "M56 86q44-30 88 0z"),
    L("S", leaf(98, 74, 24, -30)), L("S", leaf(98, 74, 18, -150), .85),
    L("K", ellipse(100, 128, 34, 5), .1),
    L("M", "M50 84h100a50 44 0 0 1-100 0z"),
    L("M2", "M58 92h84", .9, 3),
    L("P", rrect(86, 124, 28, 6, 3), .6),
])

# 7 Habits (empty): seedling in a pot with a check badge
ILLOS["HABITS"] = ("violet", "emerald", base(-3, 0) + [
    L("S", poly((96, 92), (96, 56)), 1, 4),
    L("S", leaf(96, 70, 30, -150)), L("S", leaf(96, 62, 28, -30), .9), L("S", leaf(96, 80, 22, -20), .75),
    L("K", rrect(70, 98, 56, 40, 10), .1),
    L("M", "M68 92h60l-8 42a6 6 0 0 1-6 5H82a6 6 0 0 1-6-5z"),
    L("M2", rrect(64, 88, 68, 12, 5)),
    L("P", circle(146, 56, 17)), L("M", circle(146, 56, 17), 1, 3),
    L("M", poly((138, 56), (144, 62), (155, 50)), 1, 4),
])

# 8 Reminders (empty): bell with sparkles and a small clock
ILLOS["REMINDERS"] = ("amber", "violet", base(2, 0) + [
    L("K", "M100 132a10 10 0 0 0 10-10H90a10 10 0 0 0 10 10z", .8),
    L("M", "M100 34c-22 0-36 17-36 40v22l-10 14v6h92v-6l-10-14V74c0-23-14-40-36-40z"),
    L("M2", "M74 76c0-14 8-26 22-30", .9, 4),
    L("K", rrect(94, 26, 12, 10, 5), .8),
    L("S", sparkle(152, 56, 10)), L("S", sparkle(46, 64, 7), .8), L("SS", circle(150, 92, 5)),
    L("S", "M136 30c8 4 12 10 13 18M44 44c2-8 7-13 14-16", .7, 3),
])

# 9 Readings (empty): heart with pulse over a small chart card
ILLOS["READINGS"] = ("coral", "pink", base() + [
    L("K", rrect(66, 86, 96, 52, 10), .1),
    L("P", rrect(62, 82, 96, 52, 10)),
    L("S", rrect(74, 112, 10, 14, 3), .5), L("S", rrect(90, 104, 10, 22, 3), .7), L("S", rrect(106, 96, 10, 30, 3)),
    L("S", rrect(122, 108, 10, 18, 3), .6), L("S", rrect(138, 100, 10, 26, 3), .8),
    L("M", heart(78, 56, 26)),
    L("P", poly((56, 60), (68, 60), (74, 48), (82, 72), (88, 56), (100, 56)), 1, 3.5),
])

# 10 Mood journal (empty): notebook with a smile and pencil
ILLOS["JOURNAL"] = ("pink", "violet", base(-2, 2) + [
    L("K", rrect(62, 34, 76, 98, 10), .1),
    L("P", rrect(58, 30, 76, 98, 10)),
    L("M", rrect(58, 30, 16, 98, 8)),
    L("P", circle(66, 50, 3), .9), L("P", circle(66, 80, 3), .9), L("P", circle(66, 110, 3), .9),
    L("M", circle(104, 66, 16), .25),
    L("K", circle(98, 62, 2.4), .8), L("K", circle(110, 62, 2.4), .8),
    L("K", "M96 70q8 8 16 0", .8, 3),
    L("G", rrect(86, 96, 38, 5, 2.5), .45), L("G", rrect(86, 106, 28, 5, 2.5), .3),
    L("S", "M140 120l26-44 10 6-26 44-12 6z"),
    L("SS", "M166 76l10 6 4-7-10-6z"),
])

# 11 Food scanner: camera frame around a plate
ILLOS["SCAN"] = ("orange", "amber", base() + [
    L("P", circle(100, 82, 38)), L("G", circle(100, 82, 38), .25, 2),
    L("S", circle(90, 74, 12)), L("M", circle(110, 88, 14), .9), L("SS", circle(96, 98, 8)),
    L("M", "M52 52V42a10 10 0 0 1 10-10h12M126 32h12a10 10 0 0 1 10 10v10M148 112v10a10 10 0 0 1-10 10h-12M74 132H62a10 10 0 0 1-10-10v-10", 1, 5),
    L("S", sparkle(160, 120, 8)),
])

# 12 AI chat: two speech bubbles with sparkles
ILLOS["CHAT"] = ("violet", "sky", base(0, 2) + [
    L("K", rrect(44, 38, 82, 52, 16), .1),
    L("M", "M58 34h64a16 16 0 0 1 16 16v20a16 16 0 0 1-16 16H82l-16 12v-12h-8a16 16 0 0 1-16-16V50a16 16 0 0 1 16-16z"),
    L("P", circle(76, 60, 4.5)), L("P", circle(92, 60, 4.5)), L("P", circle(108, 60, 4.5)),
    L("P", "M98 92h48a14 14 0 0 1 14 14v12a14 14 0 0 1-14 14h-4v10l-14-10H98a14 14 0 0 1-14-14v-12a14 14 0 0 1 14-14z"),
    L("S", "M98 92h48a14 14 0 0 1 14 14v12a14 14 0 0 1-14 14h-4v10l-14-10H98a14 14 0 0 1-14-14v-12a14 14 0 0 1 14-14z", 1, 3),
    L("S", rrect(98, 106, 46, 5, 2.5), .6), L("S", rrect(98, 116, 30, 5, 2.5), .4),
    L("S", sparkle(152, 44, 10)), L("M2", sparkle(36, 104, 7), .9),
])

# 13 Health Connect: watch and phone with sync arrows
ILLOS["SYNC"] = ("sky", "coral", base() + [
    L("K", rrect(112, 30, 50, 96, 10), .1),
    L("P", rrect(110, 26, 50, 96, 10)), L("M", rrect(110, 26, 50, 96, 10), 1, 3),
    L("S", heart(135, 62, 11)),
    L("G", rrect(120, 86, 30, 5, 2.5), .5), L("G", rrect(120, 96, 22, 5, 2.5), .35),
    L("K", rrect(46, 52, 20, 64, 8), .75),
    L("P", rrect(36, 64, 40, 40, 12)), L("M", rrect(36, 64, 40, 40, 12), 1, 3),
    L("M", poly((44, 86), (50, 86), (54, 78), (58, 94), (62, 84), (68, 84)), 1, 2.6),
    L("M", "M84 64q12-10 22-2", 1, 3.5), L("M", poly((100, 56), (107, 62), (99, 66)), 1, 3.5),
    L("M", "M106 104q-12 10-22 2", 1, 3.5), L("M", poly((90, 112), (83, 106), (91, 102)), 1, 3.5),
])

LIGHT = {
    "emerald": ("#12B886", "#E3FAF2", "#20C997"), "coral": ("#FA5252", "#FFEDED", "#FF8787"),
    "orange": ("#FD7E14", "#FFF1E3", "#FFA94D"), "sky": ("#228BE6", "#E5F3FF", "#4DABF7"),
    "violet": ("#7048E8", "#F1ECFF", "#9775FA"), "pink": ("#E64980", "#FFEBF3", "#F783AC"),
    "amber": ("#F59F00", "#FFF7DB", "#FFD43B"), "indigo": ("#4263EB", "#EBEFFF", "#748FFC"),
}
DARK_SURF = "#1A1D27"

def mix(hex1, hex2, a):  # hex1 at alpha a over hex2
    c1 = [int(hex1[i:i+2], 16) for i in (1, 3, 5)]; c2 = [int(hex2[i:i+2], 16) for i in (1, 3, 5)]
    return "#" + "".join("%02X" % round(c1[i] * a + c2[i] * (1 - a)) for i in range(3))

def colors(main, second, dark):
    m, s = LIGHT[main], LIGHT[second]
    if dark:
        return {"BG": mix(m[0], DARK_SURF, .22), "M": m[2], "M2": m[2], "S": s[2], "SS": mix(s[0], DARK_SURF, .22),
                "P": "#242836", "K": "#ECEEF6", "G": "#A3A9BE"}
    return {"BG": m[1], "M": m[0], "M2": m[2], "S": s[0], "SS": s[1], "P": "#FFFFFF", "K": "#181B2C", "G": "#6B7088"}

def svg(name, dark):
    main, second, layers = ILLOS[name]
    c = colors(main, second, dark)
    out = [f'<svg viewBox="0 0 {W} {H}" width="200" height="160" xmlns="http://www.w3.org/2000/svg">']
    for t, a, d, sw in layers:
        if sw:
            out.append(f'<path d="{d}" fill="none" stroke="{c[t]}" stroke-opacity="{a}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round"/>')
        else:
            out.append(f'<path d="{d}" fill="{c[t]}" fill-opacity="{a}"/>')
    out.append("</svg>")
    return "".join(out)

def gallery():
    cells = []
    for dark in (False, True):
        bg = "#0F1118" if dark else "#F6F7FB"
        row = "".join(f'<div style="text-align:center;font:12px sans-serif;color:{"#A3A9BE" if dark else "#6B7088"}">{svg(n, dark)}<div>{n.lower()}</div></div>' for n in ILLOS)
        cells.append(f'<div style="background:{bg};padding:12px;border-radius:14px;display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:8px">{row}</div>')
    return '<div style="display:flex;flex-direction:column;gap:12px">' + "".join(cells) + "</div>"

TOK = {"BG": 0, "M": 1, "M2": 2, "S": 3, "SS": 4, "P": 5, "K": 6, "G": 7}

def kotlin():
    acc = {k: k.capitalize() for k in LIGHT}
    lines = []
    for name, (main, second, layers) in ILLOS.items():
        ls = ",\n        ".join(
            f'L({TOK[t]}, {f(a)}f, "{d}"' + (f", {f(sw)}f)" if sw else ")") for t, a, d, sw in layers)
        lines.append(f'    Illustration.{name} to Spec(AccentKey.{main.upper()}, AccentKey.{second.upper()}, listOf(\n        {ls}\n    ))')
    enum = ", ".join(ILLOS)
    return KT.replace("__ENUM__", enum).replace("__SPECS__", ",\n".join(lines))

KT = '''package com.healthapp.presentation.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Original flat illustrations for onboarding, empty states and feature cards.
 *
 * Generated by `tools/illustrations/gen.py` (one source for the Kotlin and the SVG preview) — edit the
 * generator, not this file. Colours are tokens resolved from [HealthPalette] at runtime, so every drawing
 * follows light/dark mode and the accent of the screen it sits on. Purely decorative: no content description.
 */
enum class Illustration { __ENUM__ }

private enum class AccentKey { EMERALD, CORAL, ORANGE, SKY, VIOLET, PINK, AMBER, INDIGO }

/** token: 0 bg (main soft) · 1 main · 2 main light · 3 second · 4 second soft · 5 paper · 6 ink · 7 grey */
private class L(val token: Int, val alpha: Float, val path: String, val stroke: Float = 0f)
private class Spec(val main: AccentKey, val second: AccentKey, val layers: List<L>)

@Composable
private fun AccentKey.accent(): Accent = when (this) {
    AccentKey.EMERALD -> HealthPalette.Emerald
    AccentKey.CORAL -> HealthPalette.Coral
    AccentKey.ORANGE -> HealthPalette.Orange
    AccentKey.SKY -> HealthPalette.Sky
    AccentKey.VIOLET -> HealthPalette.Violet
    AccentKey.PINK -> HealthPalette.Pink
    AccentKey.AMBER -> HealthPalette.Amber
    AccentKey.INDIGO -> HealthPalette.Indigo
}

@Composable
fun IllustrationImage(kind: Illustration, modifier: Modifier = Modifier) {
    val spec = SPECS.getValue(kind)
    val main = spec.main.accent()
    val second = spec.second.accent()
    val dark = LocalHealthColors.current.isDark
    // Light variant of the main colour: the brighter end of its gradient in light mode.
    val mainLight = main.strong.copy(alpha = if (dark) 1f else 0.6f)
    val palette = listOf(
        main.soft, main.strong, mainLight, second.strong, second.soft,
        if (dark) HealthPalette.SurfaceAlt else Color.White, HealthPalette.Ink, HealthPalette.Muted
    )
    val vector = remember(kind, palette) { build(kind.name, spec, palette) }
    Image(vector, contentDescription = null, modifier = modifier)
}

private fun build(name: String, spec: Spec, palette: List<Color>): ImageVector {
    val b = ImageVector.Builder(name, defaultWidth = 200.dp, defaultHeight = 160.dp, viewportWidth = 200f, viewportHeight = 160f)
    spec.layers.forEach { l ->
        val c = SolidColor(palette[l.token])
        if (l.stroke > 0f) {
            b.addPath(addPathNodes(l.path), stroke = c, strokeAlpha = l.alpha, strokeLineWidth = l.stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        } else {
            b.addPath(addPathNodes(l.path), fill = c, fillAlpha = l.alpha)
        }
    }
    return b.build()
}

private val SPECS: Map<Illustration, Spec> = mapOf(
__SPECS__
)
'''

if __name__ == "__main__":
    if "--gallery" in sys.argv:  # preview page for design review (not committed)
        open(os.path.join(OUT, "gallery.html"), "w", encoding="utf-8").write(gallery())
    if "--kotlin" in sys.argv:
        dest = sys.argv[sys.argv.index("--kotlin") + 1]
        open(dest, "w", encoding="utf-8").write(kotlin())
        print("wrote", dest)
    print("illustrations:", len(ILLOS))

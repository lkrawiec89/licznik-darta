#!/usr/bin/env python3
"""Generuje ikony launchera 'Licznik Darta' (tarcza do darta)."""
import math, os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")

def dartboard(size, margin=0.06):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2
    r = size / 2 * (1 - margin)

    # kolo zewnetrzne - czarne
    d.ellipse([c - r, c - r, c + r, c + r], fill=(24, 24, 24, 255), outline=(0, 0, 0, 255), width=max(1, int(size * 0.02)))
    seg_colors = [("green", (16, 110, 48, 255)), ("red", (200, 40, 40, 255)), ("cream", (244, 228, 196, 255))]
    ring_names = [(1.0, 0.76, 1), (0.76, 0.70, 0), (0.70, 0.55, 1), (0.55, 0.49, 0), (0.49, 0.37, 1), (0.37, 0.31, 0), (0.31, 0.13, 1)]
    n = 20
    for ri, (r_out, r_in, alternate) in enumerate(ring_names):
        for i in range(n):
            if ri % 2 == 0:
                color = seg_colors[i % 2][1]
            else:
                color = seg_colors[2][1] if i % 2 == 0 else (240, 240, 240, 255)
            a0 = -90 + i * 360 / n
            a1 = a0 + 360 / n
            pts = [(c, c)]
            steps = 24
            for s in range(steps + 1):
                a = math.radians(a0 + (a1 - a0) * s / steps)
                x = c + r_out * r * math.cos(a)
                y = c + r_out * r * math.sin(a)
                pts.append((x, y))
            for s in range(steps, -1, -1):
                a = math.radians(a0 + (a1 - a0) * s / steps)
                x = c + r_in * r * math.cos(a)
                y = c + r_in * r * math.sin(a)
                pts.append((x, y))
            d.polygon(pts, fill=color)

    # bull
    d.ellipse([c - 0.13 * r, c - 0.13 * r, c + 0.13 * r, c + 0.13 * r], fill=(16, 110, 48, 255))
    d.ellipse([c - 0.06 * r, c - 0.06 * r, c + 0.06 * r, c + 0.06 * r], fill=(200, 40, 40, 255))

    # lotka
    dart_angle = math.radians(-35)
    tip = (c + r * 0.78 * math.cos(dart_angle), c + r * 0.78 * math.sin(dart_angle))
    tail = (c + r * 1.28 * math.cos(dart_angle), c + r * 1.28 * math.sin(dart_angle))
    perp = math.radians(90)
    tw = size * 0.035
    t1 = (tail[0] + tw * math.cos(dart_angle + perp), tail[1] + tw * math.sin(dart_angle + perp))
    t2 = (tail[0] - tw * math.cos(dart_angle + perp), tail[1] - tw * math.sin(dart_angle + perp))
    d.polygon([tip, t1, t2], fill=(230, 230, 230, 255))
    wing1 = (tail[0] + size * 0.14 * math.cos(dart_angle + perp * 1.6), tail[1] + size * 0.14 * math.sin(dart_angle + perp * 1.6))
    wing2 = (tail[0] + size * 0.14 * math.cos(dart_angle - perp * 1.6), tail[1] + size * 0.14 * math.sin(dart_angle - perp * 1.6))
    d.polygon([tail, wing1, t1], fill=(210, 30, 30, 255))
    d.polygon([tail, wing2, t2], fill=(210, 30, 30, 255))
    d.line([tail, tip], fill=(180, 180, 180, 255), width=max(1, int(size * 0.012)))
    return img

# mipmap PNG (legacy)
for dpi, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    out = os.path.join(ROOT, f"mipmap-{dpi}")
    os.makedirs(out, exist_ok=True)
    dartboard(size).save(os.path.join(out, "ic_launcher.png"))

# adaptive icon (API 26+)
fg = os.path.join(ROOT, "mipmap-xxxhdpi")
dartboard(432, margin=0.16).save(os.path.join(fg, "ic_launcher_foreground.png"))
bg = Image.new("RGBA", (432, 432), (20, 30, 48, 255))
bg.save(os.path.join(fg, "ic_launcher_background.png"))

print("ikony wygenerowane")

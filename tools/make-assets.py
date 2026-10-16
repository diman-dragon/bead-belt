#!/usr/bin/env python3
"""Рисует иконку, сплэш и графику для Google Play кодом (Pillow).
Запуск:  python3 tools/make-assets.py
Результат: assets/*.png (для @capacitor/assets) и store/*.png (для карточки в Play Console).
Всё нарисовано с нуля — своих авторских прав не нарушает.
"""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.makedirs(os.path.join(ROOT, "assets"), exist_ok=True)
os.makedirs(os.path.join(ROOT, "store"), exist_ok=True)

COLORS = {
    "R": ((252, 165, 165), (239, 68, 68), (153, 27, 27)),
    "Y": ((254, 240, 138), (250, 204, 21), (133, 77, 14)),
    "G": ((134, 239, 172), (34, 197, 94), (20, 83, 45)),
    "B": ((147, 197, 253), (59, 130, 246), (30, 58, 138)),
}


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient_bg(size, top=(30, 58, 95), bottom=(11, 18, 32)):
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        c = lerp(top, bottom, y / max(1, h - 1))
        for x in range(w):
            px[x, y] = c
    return img


def marble(img, cx, cy, r, key):
    """Шарик: тень, радиальный градиент (концентрические круги), блик."""
    light, base, dark = COLORS[key]
    S = 4
    size = int(r * 2 * 1.5)
    layer = Image.new("RGBA", (size * S, size * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    m = size * S / 2
    R = r * S
    # тень
    sh = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse([m - R, m - R + R * 0.12, m + R, m + R + R * 0.12], fill=(0, 0, 0, 110))
    sh = sh.filter(ImageFilter.GaussianBlur(R * 0.06))
    layer.alpha_composite(sh)
    steps = 40
    for i in range(steps):
        t = i / (steps - 1)                     # 0 — край, 1 — центр блика
        rad = R * (1 - t * 0.92)
        ox = -R * 0.34 * t
        oy = -R * 0.38 * t
        col = lerp(dark, base, min(1, t * 2.2)) if t < 0.5 else lerp(base, light, (t - 0.5) * 2)
        d.ellipse([m + ox - rad, m + oy - rad, m + ox + rad, m + oy + rad], fill=col + (255,))
    # блик
    d.ellipse([m - R * 0.58, m - R * 0.66, m - R * 0.1, m - R * 0.32], fill=(255, 255, 255, 150))
    layer = layer.resize((size, size), Image.LANCZOS)
    img.alpha_composite(layer, (int(cx - size / 2), int(cy - size / 2)))


def stadium_path(cx, cy, w, h, n):
    """n точек по контуру «стадиона» (прямые + полуокружности), по часовой стрелке."""
    import math
    r = h / 2
    half = (w - h) / 2
    straight = 2 * half
    arc = math.pi * r
    total = 2 * straight + 2 * arc
    pts = []
    for i in range(n):
        s = (i / n) * total
        if s < straight:
            pts.append((cx - half + s, cy - r))
        elif s < straight + arc:
            phi = (s - straight) / r
            pts.append((cx + half + r * math.sin(phi), cy - r * math.cos(phi)))
        elif s < 2 * straight + arc:
            pts.append((cx + half - (s - straight - arc), cy + r))
        else:
            phi = (s - 2 * straight - arc) / r
            pts.append((cx - half - r * math.sin(phi), cy + r * math.cos(phi)))
    return pts


def logo(size, scale=1.0, with_bg=False):
    """Логотип: овальная лента конвейера с четырьмя шариками."""
    img = gradient_bg((size, size)).convert("RGBA") if with_bg else Image.new("RGBA", (size, size), (0, 0, 0, 0))
    S = 2
    big = Image.new("RGBA", (size * S, size * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    cx, cy = size * S / 2, size * S / 2
    w, h = 0.60 * size * S * scale, 0.34 * size * S * scale
    track = h * 0.24
    # лента
    for width, color in ((track * 1.25, (8, 15, 35, 255)), (track, (71, 85, 105, 255))):
        r = h / 2
        d.rounded_rectangle([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], radius=r, outline=color, width=int(width))
    big = big.resize((size, size), Image.LANCZOS)
    out = img if with_bg else big
    if with_bg:
        out.alpha_composite(big)
    tw = 0.34 * size * scale * 0.24          # толщина ленты (та же, что выше), шарики идут по её центру
    pts = stadium_path(size / 2, size / 2, 0.60 * size * scale - tw, 0.34 * size * scale - tw, 8)
    order = ["R", "Y", "G", "B"]
    mr = 0.07 * size * scale
    for i, key in enumerate(order):
        x, y = pts[i * 2 + 1]
        marble(out, x, y, mr, key)
    return out


def save(img, rel, mode=None):
    p = os.path.join(ROOT, rel)
    (img.convert(mode) if mode else img).save(p, optimize=True)
    print("✓", rel, img.size)


def main():
    # Иконки для @capacitor/assets
    icon = logo(1024, scale=1.18, with_bg=True)
    save(icon, "assets/icon-only.png")
    fg = logo(1024, scale=0.96, with_bg=False)              # в безопасной зоне адаптивной иконки
    save(fg, "assets/icon-foreground.png")
    save(gradient_bg((1024, 1024)), "assets/icon-background.png", "RGB")

    # Сплэш
    for name, top, bot in (("splash", (30, 58, 95), (11, 18, 32)), ("splash-dark", (11, 18, 32), (5, 8, 16))):
        bg = gradient_bg((2732, 2732), top, bot).convert("RGBA")
        mark = logo(1000, scale=1.0, with_bg=False)
        bg.alpha_composite(mark, ((2732 - 1000) // 2, (2732 - 1000) // 2))
        save(bg, f"assets/{name}.png", "RGB")

    # Google Play: иконка 512x512
    save(icon.resize((512, 512), Image.LANCZOS), "store/icon-512.png")

    # Google Play: графика 1024x500
    fg_img = gradient_bg((1024, 500), (30, 58, 95), (11, 18, 32)).convert("RGBA")
    mark = logo(460, scale=1.1, with_bg=False)
    fg_img.alpha_composite(mark, (30, 20))
    d = ImageDraw.Draw(fg_img)
    font_path = next((p for p in (
        "/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf") if os.path.exists(p)), None)
    big = ImageFont.truetype(font_path, 96) if font_path else ImageFont.load_default()
    small = ImageFont.truetype(font_path, 30) if font_path else ImageFont.load_default()
    d.text((490, 150), "Bead Belt", font=big, fill=(226, 232, 240))
    d.text((494, 275), "Sort the beads. Fill the boxes.", font=small, fill=(148, 163, 184))
    save(fg_img, "store/feature-graphic-1024x500.png", "RGB")


if __name__ == "__main__":
    main()

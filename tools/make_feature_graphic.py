# Ouralia's Play feature graphic (1024x500), drawn from the icon's geometry (docs/icon/ouralia-icon.svg).
#   python3 tools/make_feature_graphic.py docs/icon/ouralia-feature-1024x500.png
import math, sys
from PIL import Image, ImageDraw, ImageFont
K = 4; W, H = 1024 * K, 500 * K
BG = (13, 11, 19); GREEN = (85, 231, 165); PURPLE = (162, 139, 249)
im = Image.new("RGB", (W, H), BG); d = ImageDraw.Draw(im)
cx, cy, u = 270 * K, 250 * K, 3.1 * K          # icon centre and px per icon unit (icon is 108 units)
def P(x, y): return (cx + (x - 54) * u, cy + (y - 54) * u)
def mix(a, b, t): return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))
def blend(c, a): return mix(BG, c, a)
# faint rings, the room's sound spreading out
for i, r in enumerate([40, 52, 64, 76, 88]):
    rr = r * u; d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), outline=blend(PURPLE, 0.10 - i * 0.017), width=int(1.2 * u))
def arc(a0, a1, w, col, r=26):
    n = max(2, int(abs(a1 - a0) * 3))
    for i in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * i / n)
        x, y = P(54 + r * math.cos(a), 54 + r * math.sin(a)); c = col(x, y) if callable(col) else col
        d.ellipse((x - w * u / 2, y - w * u / 2, x + w * u / 2, y + w * u / 2), fill=c)
arc(146, 394, 6.5, blend(PURPLE, 0.18))
g0, g1 = P(28, 68.539), P(70.712, 28); gx, gy = g1[0] - g0[0], g1[1] - g0[1]; gl = gx * gx + gy * gy
arc(146, 310, 6.5, lambda x, y: mix(GREEN, PURPLE, min(1, max(0, ((x - g0[0]) * gx + (y - g0[1]) * gy) / gl))))
arc(126, 150, 14, GREEN); arc(30, 54, 14, PURPLE)
for (x, y, r, c) in [(70.712, 34.083, 5, (255, 255, 255)), (54, 54, 10.5, PURPLE)]:
    X, Y = P(x, y); d.ellipse((X - r * u, Y - r * u, X + r * u, Y + r * u), fill=c)
bold = ImageFont.truetype("/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf", 118 * K)
reg = ImageFont.truetype("/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf", 34 * K)
tx = 500 * K
d.text((tx, 238 * K), "ouralia", font=bold, fill=(240, 238, 246), anchor="ls")
d.text((tx + 4 * K, 300 * K), "The world around you,", font=reg, fill=(200, 194, 222), anchor="ls")
d.text((tx + 4 * K, 345 * K), "turned into music.", font=reg, fill=(200, 194, 222), anchor="ls")
im.resize((1024, 500), Image.LANCZOS).save(sys.argv[1], optimize=True)

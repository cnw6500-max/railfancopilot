"""
Generate a Google Play Store feature graphic for Railfan Copilot.
Required size: 1024x500 px, PNG format.
"""
from PIL import Image, ImageDraw, ImageFont
import os

W, H = 1024, 500
OUT  = os.path.join(os.path.dirname(__file__), "play_feature_graphic.png")

# ── Colours ───────────────────────────────────────────────────────────────────
BG          = (10,  22,  54)
GRADIENT_R  = (18,  40,  95)
LOCO_BODY   = (50, 100, 230)
LOCO_CAB    = (30,  70, 180)
LOCO_DARK   = (20,  50, 140)
WHEEL_RIM   = (30,  65, 170)
WHEEL_HUB   = (60, 115, 245)
HEADLIGHT   = (255, 215,  60)
WINDOW      = (170, 205, 255)
TRACK_TIE   = (55,  80, 155)
TRACK_RAIL  = (90, 120, 190)
ACCENT_LINE = (50,  95, 200)
TEXT_MAIN   = (160, 200, 255)
TEXT_SUB    = (120, 165, 230)
TEXT_TAG    = (100, 145, 210)
BORDER_CLR  = (30,  60, 130)

img  = Image.new("RGB", (W, H), BG)
draw = ImageDraw.Draw(img)

# ── Gradient background ───────────────────────────────────────────────────────
for x in range(W):
    t = x / W
    r = int(BG[0] + (GRADIENT_R[0] - BG[0]) * t)
    g = int(BG[1] + (GRADIENT_R[1] - BG[1]) * t)
    b = int(BG[2] + (GRADIENT_R[2] - BG[2]) * t)
    draw.line([(x, 0), (x, H)], fill=(r, g, b))

# ── Motion lines ──────────────────────────────────────────────────────────────
MID_Y = H // 2
for (lx, ly, llen, lw, alpha) in [
    (30, MID_Y - 72, 260, 3, 30),
    (30, MID_Y - 42, 320, 4, 45),
    (30, MID_Y - 10, 360, 6, 60),
    (30, MID_Y + 26, 310, 4, 45),
    (30, MID_Y + 58, 220, 3, 30),
]:
    col = tuple(int(c * alpha / 100) for c in ACCENT_LINE)
    draw.rectangle([lx, ly, lx + llen, ly + lw], fill=col)

# ── Track ─────────────────────────────────────────────────────────────────────
TRACK_Y = H - 118
TRACK_H = 26
TRACK_L = 30
TRACK_R = W - 30

TIE_W, TIE_H, TIE_GAP = 16, TRACK_H - 4, 22
x = TRACK_L
while x < TRACK_R:
    draw.rectangle([x, TRACK_Y + 2, x + TIE_W, TRACK_Y + TIE_H], fill=TRACK_TIE)
    x += TIE_W + TIE_GAP

for ry in (TRACK_Y + 3, TRACK_Y + TRACK_H - 7):
    draw.rectangle([TRACK_L, ry, TRACK_R, ry + 5], fill=TRACK_RAIL)

# ── Locomotive ────────────────────────────────────────────────────────────────
def rrect(xy, r, fill, outline=None, width=1):
    draw.rounded_rectangle(xy, radius=r, fill=fill, outline=outline, width=width)

L_LEFT  = 52
L_RIGHT = 480
L_TOP   = H - 272
L_BOT   = TRACK_Y + 3
L_W     = L_RIGHT - L_LEFT

CAB_W    = int(L_W * 0.27)
BODY_R   = L_RIGHT - CAB_W
BODY_TOP = L_TOP + int((L_BOT - L_TOP) * 0.12)

# Boiler body
rrect([L_LEFT + 26, BODY_TOP, BODY_R, L_BOT], r=14, fill=LOCO_BODY)

# Nose
NOSE_CX = L_LEFT + 26
draw.polygon([
    (NOSE_CX, BODY_TOP + 8),
    (NOSE_CX, L_BOT - 18),
    (L_LEFT,  L_BOT - 8),
    (L_LEFT,  BODY_TOP + 24),
], fill=LOCO_DARK)

# Cab
CAB_LEFT = BODY_R - 5
rrect([CAB_LEFT, L_TOP, L_RIGHT, L_BOT], r=10, fill=LOCO_CAB)

WIN_PAD = 14
draw.rounded_rectangle(
    [CAB_LEFT + WIN_PAD, L_TOP + WIN_PAD,
     L_RIGHT  - WIN_PAD, L_TOP + int((L_BOT - L_TOP) * 0.50)],
    radius=8, fill=WINDOW
)

# Chimney
SCX = L_LEFT + 82
STOP = BODY_TOP - 54
draw.rounded_rectangle([SCX - 14, STOP, SCX + 14, BODY_TOP], radius=6, fill=LOCO_DARK)
draw.ellipse([SCX - 20, STOP - 10, SCX + 20, STOP + 14], fill=LOCO_DARK)

# Dome
DCX = L_LEFT + 160
draw.ellipse([DCX - 22, BODY_TOP - 28, DCX + 22, BODY_TOP + 10], fill=LOCO_BODY)

# Headlight
HCX = L_LEFT + 14
HCY = BODY_TOP + int((L_BOT - BODY_TOP) * 0.42)
draw.ellipse([HCX - 14, HCY - 14, HCX + 14, HCY + 14], fill=LOCO_DARK)
draw.ellipse([HCX - 9,  HCY - 9,  HCX + 9,  HCY + 9],  fill=HEADLIGHT)

# Wheels
WY = L_BOT - 4
DR = 32
PR = 22
for wx in [L_LEFT + 78, L_LEFT + 152, L_LEFT + 226, L_LEFT + 300]:
    draw.ellipse([wx-DR,   WY-DR,   wx+DR,   WY+DR],   fill=LOCO_DARK)
    draw.ellipse([wx-DR+4, WY-DR+4, wx+DR-4, WY+DR-4], fill=WHEEL_RIM)
    draw.ellipse([wx-10,   WY-10,   wx+10,   WY+10],   fill=WHEEL_HUB)

PX = L_LEFT + 36
draw.ellipse([PX-PR,   WY-PR,   PX+PR,   WY+PR],   fill=LOCO_DARK)
draw.ellipse([PX-PR+4, WY-PR+4, PX+PR-4, WY+PR-4], fill=WHEEL_RIM)
draw.ellipse([PX-8,    WY-8,    PX+8,    WY+8],     fill=WHEEL_HUB)

# ── Text (right half) ─────────────────────────────────────────────────────────
FONT_BOLD   = "C:/Windows/Fonts/arialbd.ttf"
FONT_REG    = "C:/Windows/Fonts/arial.ttf"
FONT_ITALIC = "C:/Windows/Fonts/ariali.ttf"

try:
    f_title = ImageFont.truetype(FONT_BOLD,   112)
    f_sub   = ImageFont.truetype(FONT_REG,     56)
    f_tag   = ImageFont.truetype(FONT_ITALIC,  34)
except Exception:
    f_title = f_sub = f_tag = ImageFont.load_default()

TEXT_X  = 510
TEXT_CX = (TEXT_X + W) // 2

def cx(text, font, colour, y):
    bb = draw.textbbox((0, 0), text, font=font)
    tw = bb[2] - bb[0]
    draw.text((TEXT_CX - tw // 2, y), text, font=font, fill=colour)

cx("RAILFAN", f_title, TEXT_MAIN, H // 2 - 148)

# Divider
dw = 310
dy = H // 2 - 10
draw.rectangle([TEXT_CX - dw//2, dy, TEXT_CX + dw//2, dy + 3], fill=ACCENT_LINE)

cx("COPILOT",                       f_sub, TEXT_SUB, H // 2 + 2)
cx("Your AI-powered train companion", f_tag, TEXT_TAG, H // 2 + 72)

# ── Border lines ──────────────────────────────────────────────────────────────
draw.rectangle([0, 0,   W, 4],   fill=BORDER_CLR)
draw.rectangle([0, H-5, W, H],   fill=BORDER_CLR)

img.save(OUT, "PNG")
print(f"Saved {W}x{H}: {OUT}")

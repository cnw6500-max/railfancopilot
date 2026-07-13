"""
Generate a Facebook Page cover photo for Railfan Copilot.
Output: 1640x624 px (2x retina — displays at 820x312).
"""
from PIL import Image, ImageDraw, ImageFont
import math, os

W, H = 1640, 624
OUT  = os.path.join(os.path.dirname(__file__), "facebook_cover.png")

# ── Colours ──────────────────────────────────────────────────────────────────
BG          = (10,  22,  54)
GRADIENT_R  = (18,  38,  90)      # right-side gradient hint
LOCO_BODY   = (50, 100, 230)
LOCO_CAB    = (30,  70, 180)
LOCO_DARK   = (20,  50, 140)
WHEEL_RIM   = (30,  65, 170)
WHEEL_HUB   = (60, 115, 245)
HEADLIGHT   = (255, 215,  60)
WINDOW      = (170, 205, 255)
TRACK_TIE   = (55,  80, 155)
TRACK_RAIL  = (90, 120, 190)
ACCENT_LINE = (60, 110, 220)      # decorative speed lines
TEXT_MAIN   = (160, 200, 255)
TEXT_SUB    = (120, 165, 230)
TEXT_TAG    = (100, 145, 210)
BORDER_CLR  = (30,  60, 130)

img  = Image.new("RGB", (W, H), BG)
draw = ImageDraw.Draw(img)

# ── Background gradient (left dark → right slightly lighter) ─────────────────
for x in range(W):
    t = x / W
    r = int(BG[0] + (GRADIENT_R[0] - BG[0]) * t)
    g = int(BG[1] + (GRADIENT_R[1] - BG[1]) * t)
    b = int(BG[2] + (GRADIENT_R[2] - BG[2]) * t)
    draw.line([(x, 0), (x, H)], fill=(r, g, b))

# ── Speed / motion lines (subtle, behind locomotive) ─────────────────────────
LINE_Y0 = H // 2 - 10
for i, (lx, ly, llen, lw, alpha) in enumerate([
    (60,  LINE_Y0 - 55, 340, 3, 35),
    (60,  LINE_Y0 - 25, 440, 5, 50),
    (60,  LINE_Y0 + 10, 480, 7, 65),
    (60,  LINE_Y0 + 48, 400, 5, 50),
    (60,  LINE_Y0 + 80, 280, 3, 35),
]):
    col = tuple(int(c * alpha / 100) for c in ACCENT_LINE)
    draw.rectangle([lx, ly, lx + llen, ly + lw], fill=col)

# ── Track ─────────────────────────────────────────────────────────────────────
TRACK_Y  = H - 160
TRACK_H  = 34
TRACK_L  = 40
TRACK_R  = W - 40

TIE_W, TIE_H, TIE_GAP = 22, TRACK_H - 6, 28
x = TRACK_L
while x < TRACK_R:
    draw.rectangle([x, TRACK_Y + 3, x + TIE_W, TRACK_Y + TIE_H], fill=TRACK_TIE)
    x += TIE_W + TIE_GAP

for ry in (TRACK_Y + 4, TRACK_Y + TRACK_H - 9):
    draw.rectangle([TRACK_L, ry, TRACK_R, ry + 7], fill=TRACK_RAIL)

# ── Locomotive (centred on left half) ────────────────────────────────────────
def rrect(draw, xy, r, fill, outline=None, width=1):
    draw.rounded_rectangle(xy, radius=r, fill=fill, outline=outline, width=width)

# Loco bounding box
L_LEFT  = 80
L_RIGHT = 740
L_TOP   = H - 340
L_BOT   = TRACK_Y + 4

L_W = L_RIGHT - L_LEFT
L_H = L_BOT - L_TOP

CAB_W   = int(L_W * 0.26)
BODY_R  = L_RIGHT - CAB_W
BODY_TOP = L_TOP + int(L_H * 0.12)

# Main boiler body
rrect(draw, [L_LEFT + 34, BODY_TOP, BODY_R, L_BOT], r=18, fill=LOCO_BODY)

# Nose / cow-catcher
NOSE_CX = L_LEFT + 34
draw.polygon([
    (NOSE_CX,   BODY_TOP + 10),
    (NOSE_CX,   L_BOT - 22),
    (L_LEFT,    L_BOT - 10),
    (L_LEFT,    BODY_TOP + 30),
], fill=LOCO_DARK)

# Cab
CAB_LEFT = BODY_R - 6
CAB_TOP  = L_TOP
rrect(draw, [CAB_LEFT, CAB_TOP, L_RIGHT, L_BOT], r=12, fill=LOCO_CAB)

WIN_PAD = 18
draw.rounded_rectangle(
    [CAB_LEFT + WIN_PAD, CAB_TOP + WIN_PAD,
     L_RIGHT  - WIN_PAD, CAB_TOP + int((L_BOT - CAB_TOP) * 0.50)],
    radius=9, fill=WINDOW
)

# Chimney
STACK_CX  = L_LEFT + 108
STACK_BOT = BODY_TOP
STACK_TOP = STACK_BOT - 68
draw.rounded_rectangle([STACK_CX - 18, STACK_TOP, STACK_CX + 18, STACK_BOT], radius=8, fill=LOCO_DARK)
draw.ellipse([STACK_CX - 26, STACK_TOP - 12, STACK_CX + 26, STACK_TOP + 18], fill=LOCO_DARK)

# Steam dome
DOME_CX = L_LEFT + 210
draw.ellipse([DOME_CX - 28, BODY_TOP - 36, DOME_CX + 28, BODY_TOP + 14], fill=LOCO_BODY)

# Headlight
HL_CX = L_LEFT + 20
HL_CY = BODY_TOP + int((L_BOT - BODY_TOP) * 0.42)
draw.ellipse([HL_CX - 18, HL_CY - 18, HL_CX + 18, HL_CY + 18], fill=LOCO_DARK)
draw.ellipse([HL_CX - 12, HL_CY - 12, HL_CX + 12, HL_CY + 12], fill=HEADLIGHT)

# Wheels
WHEEL_Y   = L_BOT - 5
DRIVE_R   = 42
PILOT_R   = 28
for wx in [L_LEFT + 105, L_LEFT + 200, L_LEFT + 295, L_LEFT + 390]:
    draw.ellipse([wx-DRIVE_R,     WHEEL_Y-DRIVE_R,     wx+DRIVE_R,     WHEEL_Y+DRIVE_R],     fill=LOCO_DARK)
    draw.ellipse([wx-DRIVE_R+5,   WHEEL_Y-DRIVE_R+5,   wx+DRIVE_R-5,   WHEEL_Y+DRIVE_R-5],   fill=WHEEL_RIM)
    draw.ellipse([wx-13, WHEEL_Y-13, wx+13, WHEEL_Y+13], fill=WHEEL_HUB)

PX = L_LEFT + 48
draw.ellipse([PX-PILOT_R,     WHEEL_Y-PILOT_R,     PX+PILOT_R,     WHEEL_Y+PILOT_R],     fill=LOCO_DARK)
draw.ellipse([PX-PILOT_R+5,   WHEEL_Y-PILOT_R+5,   PX+PILOT_R-5,   WHEEL_Y+PILOT_R-5],   fill=WHEEL_RIM)
draw.ellipse([PX-9, WHEEL_Y-9, PX+9, WHEEL_Y+9], fill=WHEEL_HUB)

# ── Text (right half) ─────────────────────────────────────────────────────────
FONT_BOLD   = "C:/Windows/Fonts/arialbd.ttf"
FONT_REG    = "C:/Windows/Fonts/arial.ttf"
FONT_ITALIC = "C:/Windows/Fonts/ariali.ttf"

try:
    f_title   = ImageFont.truetype(FONT_BOLD,   148)
    f_sub     = ImageFont.truetype(FONT_REG,     72)
    f_tag     = ImageFont.truetype(FONT_ITALIC,  46)
except Exception:
    f_title = f_sub = f_tag = ImageFont.load_default()

TEXT_X = 820        # right half starts here
TEXT_CENTER = (TEXT_X + W) // 2

def cx_text(text, font, colour, y):
    bb = draw.textbbox((0, 0), text, font=font)
    tw = bb[2] - bb[0]
    x  = TEXT_CENTER - tw // 2
    draw.text((x, y), text, font=font, fill=colour)

cx_text("RAILFAN",  f_title, TEXT_MAIN, H // 2 - 185)
cx_text("COPILOT",  f_sub,   TEXT_SUB,  H // 2 + 0)

# Accent divider line
DIV_Y = H // 2 - 12
div_w = 380
draw.rectangle(
    [TEXT_CENTER - div_w // 2, DIV_Y, TEXT_CENTER + div_w // 2, DIV_Y + 4],
    fill=ACCENT_LINE
)

cx_text("Your AI-powered train companion", f_tag, TEXT_TAG, H // 2 + 90)

# ── Thin top & bottom border lines ───────────────────────────────────────────
draw.rectangle([0, 0,    W, 5],    fill=BORDER_CLR)
draw.rectangle([0, H-6,  W, H],    fill=BORDER_CLR)

img.save(OUT, "PNG")
print(f"Saved {W}x{H}: {OUT}")

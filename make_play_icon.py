"""
Generate a 512x512 Play Store icon for Railfan Copilot,
matching the iOS App Store icon design (full-bleed, no outer frame).
"""
from PIL import Image, ImageDraw, ImageFont
import math, os

SIZE = 512
OUT  = os.path.join(os.path.dirname(__file__), "play_store_icon.png")

# ── Colours ──────────────────────────────────────────────────────────────────
BG          = (10,  22,  54)      # dark navy background
INNER_BG    = (13,  26,  64)      # very slightly lighter inner area
BORDER      = (30,  60, 130)      # subtle rounded-rect border
LOCO_BODY   = (50, 100, 230)      # bright royal blue locomotive body
LOCO_CAB    = (30,  70, 180)      # darker cab section
LOCO_DARK   = (20,  50, 140)      # darkest blue (undercarriage, stack base)
WHEEL_RIM   = (30,  65, 170)      # wheel outer ring
WHEEL_HUB   = (60, 115, 245)      # wheel centre hub
HEADLIGHT   = (255, 215,  60)     # warm yellow headlight
WINDOW      = (170, 205, 255)     # light blue cab window
TRACK_TIE   = (55,  80, 155)      # railway sleeper/tie colour
TRACK_RAIL  = (90, 120, 190)      # rail colour
TEXT_MAIN   = (160, 200, 255)     # "RAILFAN" text
TEXT_SUB    = (120, 165, 230)     # "COPILOT" sub-text

img  = Image.new("RGB", (SIZE, SIZE), BG)
draw = ImageDraw.Draw(img)

# ── Inner rounded rectangle (card) ───────────────────────────────────────────
MARGIN = 22
RADIUS = 52
draw.rounded_rectangle(
    [MARGIN, MARGIN, SIZE - MARGIN, SIZE - MARGIN],
    radius=RADIUS,
    fill=INNER_BG,
    outline=BORDER,
    width=3,
)

# ── Helper: filled rounded-rect ───────────────────────────────────────────────
def rrect(xy, r, fill, outline=None, width=1):
    draw.rounded_rectangle(xy, radius=r, fill=fill,
                           outline=outline, width=width)

# ── Track ─────────────────────────────────────────────────────────────────────
TRACK_Y   = 330          # top of track band
TRACK_H   = 28
TRACK_L   = 68
TRACK_R   = SIZE - 68

# Ties (sleepers)
TIE_W, TIE_H, TIE_GAP = 18, TRACK_H - 4, 22
x = TRACK_L
while x < TRACK_R:
    draw.rectangle([x, TRACK_Y + 2, x + TIE_W, TRACK_Y + TIE_H], fill=TRACK_TIE)
    x += TIE_W + TIE_GAP

# Rails (two horizontal bars)
for ry in (TRACK_Y + 3, TRACK_Y + TRACK_H - 7):
    draw.rectangle([TRACK_L, ry, TRACK_R, ry + 5], fill=TRACK_RAIL)

# ── Locomotive ────────────────────────────────────────────────────────────────
# Overall bounding box, centred horizontally
L_LEFT  = 88
L_RIGHT = SIZE - 68
L_TOP   = 185
L_BOT   = TRACK_Y + 2          # sits on top of track

L_W = L_RIGHT - L_LEFT
L_H = L_BOT - L_TOP

# Main boiler body (left 68% of loco width)
CAB_W   = int(L_W * 0.28)
BODY_R  = L_RIGHT - CAB_W      # right edge of boiler
BODY_TOP = L_TOP + int(L_H * 0.15)

rrect([L_LEFT + 28, BODY_TOP, BODY_R, L_BOT], r=14, fill=LOCO_BODY)

# Nose cowcatcher at far left
NOSE_CX = L_LEFT + 28
draw.polygon([
    (NOSE_CX,      BODY_TOP + 8),
    (NOSE_CX,      L_BOT - 18),
    (L_LEFT,       L_BOT - 8),
    (L_LEFT,       BODY_TOP + 24),
], fill=LOCO_DARK)

# Cab (right section)
CAB_LEFT = BODY_R - 4
CAB_TOP  = L_TOP
rrect([CAB_LEFT, CAB_TOP, L_RIGHT, L_BOT], r=10, fill=LOCO_CAB)

# Cab window
WIN_PAD = 14
draw.rounded_rectangle(
    [CAB_LEFT + WIN_PAD, CAB_TOP + WIN_PAD,
     L_RIGHT  - WIN_PAD, CAB_TOP + int((L_BOT - CAB_TOP) * 0.52)],
    radius=7, fill=WINDOW
)

# Chimney / stack
STACK_CX  = L_LEFT + 85
STACK_BOT = BODY_TOP
STACK_TOP = STACK_BOT - 52
draw.rounded_rectangle(
    [STACK_CX - 14, STACK_TOP, STACK_CX + 14, STACK_BOT],
    radius=7, fill=LOCO_DARK
)
# Flared top
draw.ellipse(
    [STACK_CX - 20, STACK_TOP - 10, STACK_CX + 20, STACK_TOP + 14],
    fill=LOCO_DARK
)

# Dome (steam dome on boiler)
DOME_CX = L_LEFT + 150
draw.ellipse(
    [DOME_CX - 22, BODY_TOP - 28, DOME_CX + 22, BODY_TOP + 10],
    fill=LOCO_BODY
)

# Headlight
HL_CX = L_LEFT + 16
HL_CY = BODY_TOP + int((L_BOT - BODY_TOP) * 0.42)
draw.ellipse([HL_CX - 14, HL_CY - 14, HL_CX + 14, HL_CY + 14], fill=LOCO_DARK)
draw.ellipse([HL_CX - 10, HL_CY - 10, HL_CX + 10, HL_CY + 10], fill=HEADLIGHT)

# Wheels (4 drive wheels + 1 small front pilot)
WHEEL_Y   = L_BOT - 4           # wheel centre Y
DRIVE_R   = 34
PILOT_R   = 22
WHEEL_POSITIONS = [
    (L_LEFT + 80,  DRIVE_R),
    (L_LEFT + 156, DRIVE_R),
    (L_LEFT + 232, DRIVE_R),
    (L_LEFT + 308, DRIVE_R),
]
for (wx, wr) in WHEEL_POSITIONS:
    # Outer rim
    draw.ellipse([wx - wr,     WHEEL_Y - wr,     wx + wr,     WHEEL_Y + wr],     fill=LOCO_DARK)
    draw.ellipse([wx - wr + 4, WHEEL_Y - wr + 4, wx + wr - 4, WHEEL_Y + wr - 4], fill=WHEEL_RIM)
    # Hub
    draw.ellipse([wx - 10, WHEEL_Y - 10, wx + 10, WHEEL_Y + 10], fill=WHEEL_HUB)

# Small pilot wheel (front)
PX = L_LEFT + 38
draw.ellipse([PX - PILOT_R,     WHEEL_Y - PILOT_R,     PX + PILOT_R,     WHEEL_Y + PILOT_R],     fill=LOCO_DARK)
draw.ellipse([PX - PILOT_R + 4, WHEEL_Y - PILOT_R + 4, PX + PILOT_R - 4, WHEEL_Y + PILOT_R - 4], fill=WHEEL_RIM)
draw.ellipse([PX - 7, WHEEL_Y - 7, PX + 7, WHEEL_Y + 7], fill=WHEEL_HUB)

# ── Text ─────────────────────────────────────────────────────────────────────
# Try to load a clean sans-serif; fall back to default
FONT_PATH_BOLD = "C:/Windows/Fonts/arialbd.ttf"
FONT_PATH_REG  = "C:/Windows/Fonts/arial.ttf"

try:
    font_main = ImageFont.truetype(FONT_PATH_BOLD, 52)
    font_sub  = ImageFont.truetype(FONT_PATH_REG,  30)
except Exception:
    font_main = ImageFont.load_default()
    font_sub  = font_main

TEXT_Y_MAIN = TRACK_Y + TRACK_H + 12
TEXT_Y_SUB  = TEXT_Y_MAIN + 58

for text, font, colour, ty in [
    ("RAILFAN", font_main, TEXT_MAIN, TEXT_Y_MAIN),
    ("COPILOT", font_sub,  TEXT_SUB,  TEXT_Y_SUB),
]:
    bb = draw.textbbox((0, 0), text, font=font)
    tw = bb[2] - bb[0]
    draw.text(((SIZE - tw) / 2, ty), text, font=font, fill=colour)

# ── Save ─────────────────────────────────────────────────────────────────────
img.save(OUT, "PNG")
print(f"Saved: {OUT}")

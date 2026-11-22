"""Fails a screenshot that is blank: one flat color, as when a screen fails to draw.

Usage: screen_image.py shot.png label problems.txt
"""
import sys

from PIL import Image

path, label, problems_path = sys.argv[1:4]
im = Image.open(path).convert("RGB")
# Leave out the status bar and navigation bar, which draw even when the app does not.
w, h = im.size
body = im.crop((0, int(h * 0.06), w, int(h * 0.92))).resize((54, 120))
colors = body.getcolors(54 * 120) or []
if len(colors) < 4:
    with open(problems_path, "a", encoding="utf-8") as f:
        f.write(f"{label}: blank screen ({len(colors)} colors)\n")
    print(f"{label}: blank screen")

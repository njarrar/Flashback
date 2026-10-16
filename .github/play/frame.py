"""Turns raw app screenshots into Play Store phone screenshots with a caption on top.

    python frame.py <raw shots dir> <out dir>

Raw files are named like the README ones (today.png, fr-today.png, ar-today.png).
Output goes to <out>/<lang>/phone-N-<scene>.png at 1080 x 1920 (9:16), which the
Play Store accepts for phones and needs for its larger app cards.
"""
import pathlib
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
FONTS = ROOT / "android/app/src/main/res/font"
W, H = 1080, 1920
BG = (246, 246, 243)
INK = (26, 26, 28)
RED = (255, 75, 62)

SCENES = ["1-welcome", "today", "calendar", "viewer", "favorites", "album"]
CAPTIONS = {
    "en": ["Every year, on this day", "Today's photos, year by year", "Pick any day to look back",
           "Zoom in, heart it, share it", "Your best moments, close by", "Make albums and share them"],
    "fr": ["Chaque année, ce jour-là", "Les photos du jour, année par année", "Revoyez n'importe quel jour",
           "Zoomez, aimez, partagez", "Vos plus beaux moments, tout près", "Créez des albums à partager"],
    "es": ["Cada año, en este día", "Las fotos de hoy, año por año", "Elige un día y recuerda",
           "Amplía, guarda y comparte", "Tus mejores momentos, cerca", "Crea álbumes y compártelos"],
    "ar": ["كل عام، في مثل هذا اليوم", "صور اليوم، سنة بعد سنة", "اختر أي يوم واسترجع ذكرياته",
           "كبّر الصورة، احفظها، شاركها", "أجمل لحظاتك دائمًا قريبة", "أنشئ ألبومات وشاركها"],
}


def font(lang, size):
    name = "tajawal_extrabold.ttf" if lang == "ar" else "nunito_extrabold.ttf"
    return ImageFont.truetype(str(FONTS / name), size)


def wrap(draw, text, f, width, lang):
    """One line if it fits, else two lines of about the same width."""
    kw = {"direction": "rtl"} if lang == "ar" else {}
    if draw.textlength(text, font=f, **kw) <= width:
        return [text]
    words = text.split()
    splits = [(" ".join(words[:i]), " ".join(words[i:])) for i in range(1, len(words))]
    return list(min(splits, key=lambda t: max(draw.textlength(x, font=f, **kw) for x in t)))


def frame(shot, caption, lang):
    canvas = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(canvas)
    rtl = lang == "ar"
    f = font(lang, 80)
    lines = wrap(draw, caption, f, W - 140, lang)
    y = 170 if len(lines) == 1 else 110
    for line in lines:
        kw = {"direction": "rtl", "language": "ar"} if rtl else {}
        tw = draw.textlength(line, font=f, **kw)
        draw.text(((W - tw) / 2, y), line, font=f, fill=INK, **kw)
        y += 104
    draw.rounded_rectangle(((W - 90) / 2, y + 14, (W + 90) / 2, y + 26), 6, fill=RED)

    # The phone screen, rounded, with a soft shadow, running off the bottom edge.
    top = 470
    sh = Image.open(shot).convert("RGB")
    sw = 860
    sh = sh.resize((sw, round(sh.height * sw / sh.width)), Image.LANCZOS)
    radius = 56
    mask = Image.new("L", sh.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, *sh.size), radius, fill=255)
    shadow = Image.new("L", (W, H), 0)
    x = (W - sw) // 2
    ImageDraw.Draw(shadow).rounded_rectangle((x, top + 16, x + sw, top + 16 + sh.height), radius, fill=70)
    shadow = shadow.filter(ImageFilter.GaussianBlur(30))
    canvas.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, 0), shadow)
    border = Image.new("RGB", (sw + 16, sh.height + 16), (255, 255, 255))
    bmask = Image.new("L", border.size, 0)
    ImageDraw.Draw(bmask).rounded_rectangle((0, 0, *border.size), radius + 8, fill=255)
    canvas.paste(border, (x - 8, top - 8), bmask)
    canvas.paste(sh, (x, top), mask)
    return canvas


def main(src, out):
    src, out = pathlib.Path(src), pathlib.Path(out)
    for lang, caps in CAPTIONS.items():
        pre = "" if lang == "en" else f"{lang}-"
        for i, (scene, cap) in enumerate(zip(SCENES, caps), 1):
            shot = src / f"{pre}{scene}.png"
            if not shot.exists():
                print("missing", shot)
                continue
            dest = out / lang / f"phone-{i}-{scene.removeprefix('1-')}.png"
            dest.parent.mkdir(parents=True, exist_ok=True)
            frame(shot, cap, lang).save(dest, optimize=True)
            print("made", dest)


if __name__ == "__main__":
    main(*sys.argv[1:3])

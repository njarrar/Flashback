"""Makes the Play Store app icon (512 x 512) and feature graphics (1024 x 500).

    python graphics.py <out dir>

Writes <out>/icon-512.png and <out>/<lang>/feature-graphic.png.
"""
import pathlib
import sys

from PIL import Image, ImageDraw, ImageFont, features

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "android/app/src/main/res"
BLUE = (46, 123, 255)
WHITE = (255, 255, 255)

TAGLINES = {
    "en": "Your photos from this day, every year",
    "fr": "Vos photos de ce jour, chaque année",
    "es": "Tus fotos de este día, cada año",
    "ar": "صورك في مثل هذا اليوم، كل عام",
    "pt": "Suas fotos deste dia, todo ano",
    "zh": "每一年的今天，你的照片",
}


def foreground(size):
    # The adaptive icon art is 108 units wide with the middle 72 showing, so crop to that.
    art = Image.open(RES / "drawable-nodpi/ic_launcher_foreground.png").convert("RGBA")
    m = art.width * 18 // 108
    return art.crop((m, m, art.width - m, art.height - m)).resize((size, size), Image.LANCZOS)


def icon(out):
    img = Image.new("RGBA", (512, 512), BLUE + (255,))
    img.alpha_composite(foreground(512))
    img.convert("RGB").save(out / "icon-512.png", optimize=True)


def cjk(name, size):
    """Noto Sans CJK SC from the system (apt package fonts-noto-cjk). Each .ttc holds
    several CJK fonts, so find the simplified Chinese one."""
    path = f"/usr/share/fonts/opentype/noto/NotoSansCJK-{name}.ttc"
    for i in range(10):
        f = ImageFont.truetype(path, size, index=i)
        if f.getname()[0] == "Noto Sans CJK SC":
            return f
    sys.exit(f"No Noto Sans CJK SC in {path}")


def font(name, size):
    return ImageFont.truetype(str(RES / "font" / name), size)


def wrap(draw, text, f, width, kw):
    """One line if it fits, else two lines of about the same width. Chinese has no
    spaces, so it can break between any two characters."""
    if draw.textlength(text, font=f, **kw) <= width:
        return [text]
    cjk = any("\u4e00" <= c <= "\u9fff" for c in text)
    sep = "" if cjk else " "
    words = list(text) if cjk else text.split()
    splits = [(sep.join(words[:i]), sep.join(words[i:])) for i in range(1, len(words))]
    return list(min(splits, key=lambda t: max(draw.textlength(x, font=f, **kw) for x in t)))


def feature(out, lang):
    W, H = 1024, 500
    img = Image.new("RGBA", (W, H), BLUE + (255,))
    rtl = lang == "ar"
    # Soft circles for depth.
    deco = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(deco)
    cx = 200 if rtl else W - 230
    d.ellipse((cx - 260, 250 - 260, cx + 260, 250 + 260), fill=(255, 255, 255, 28))
    d.ellipse((cx - 180, 250 - 180, cx + 180, 250 + 180), fill=(255, 255, 255, 30))
    img.alpha_composite(deco)
    art = foreground(470)
    img.alpha_composite(art, (cx - 235, 250 - 235))

    draw = ImageDraw.Draw(img)
    kw = {"direction": "rtl", "language": "ar"} if rtl else {}
    title_f = font("nunito_black.ttf", 96)
    tag_f = cjk("Bold", 44) if lang == "zh" else font("tajawal_bold.ttf" if rtl else "nunito_bold.ttf", 44)
    x0, width = (430, 540) if rtl else (64, 520)
    lines = wrap(draw, TAGLINES[lang], tag_f, width, kw)
    block = 110 + 58 * len(lines)
    y = (H - block) // 2
    title = "Flashback"
    tw = draw.textlength(title, font=title_f)
    draw.text((x0 + width - tw if rtl else x0, y - 14), title, font=title_f, fill=WHITE)
    y += 120
    for line in lines:
        lw = draw.textlength(line, font=tag_f, **kw)
        draw.text((x0 + width - lw if rtl else x0, y), line, font=tag_f, fill=(255, 255, 255, 235), **kw)
        y += 58
    dest = out / lang / "feature-graphic.png"
    dest.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(dest, optimize=True)


def main(out):
    if not features.check("raqm"):
        sys.exit("Pillow has no raqm, so Arabic would come out in the wrong order.")
    out = pathlib.Path(out)
    out.mkdir(parents=True, exist_ok=True)
    icon(out)
    for lang in TAGLINES:
        feature(out, lang)


if __name__ == "__main__":
    main(sys.argv[1])

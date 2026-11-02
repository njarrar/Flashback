"""Puts App Store screenshots in a phone frame on the app's blue, with a headline on top.

    python frame.py <raw shots> <out>

<raw shots> holds <lang>/<n>-<scene>.png as the App Store workflow takes them. The
headline and line under it for each shot come from app-store/listing/<locale>/captions.txt:
one shot per block, headline on the first line, the line under it on the second, blocks
split by a blank line, in the order of SHOTS below.
"""
import pathlib
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont, features

ROOT = pathlib.Path(__file__).parents[2]
FONTS = ROOT / "ios" / "BackThen" / "Fonts"
LOCALES = {"en": "en-US", "fr": "fr-FR", "es": "es-ES", "ar": "ar-SA"}
# (raw file, framed file), in store order: real photos first, the welcome screen last.
SHOTS = [("2-today", "1-today"), ("3-calendar", "2-calendar"), ("4-viewer", "3-viewer"),
         ("5-favorites", "4-favorites"), ("6-album", "5-album"), ("1-welcome", "6-welcome")]
W, H = 1320, 2868  # 6.9" iPhone, the size Apple scales down for smaller iPhones
BLUE = (46, 123, 255)  # the app icon's blue
GLOW = (120, 170, 255)
SUB = (232, 240, 255)
EDGE = (200, 205, 215)
BEZEL = (18, 18, 20)
PHONE_W = 1000  # screen width inside the frame


def font(lang, bold, size):
    if lang == "ar":
        name = "Tajawal-Black.ttf" if bold else "Tajawal-Medium.ttf"
    else:
        name = "Nunito-Black.ttf" if bold else "Nunito-SemiBold.ttf"
    return ImageFont.truetype(str(FONTS / name), size)


def mask(size, r):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), r, fill=255)
    return m


def backdrop():
    im = Image.new("RGB", (W, H), BLUE)
    glow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(glow).ellipse((-500, -700, W + 500, 1100), fill=90)
    im.paste(Image.new("RGB", (W, H), GLOW), (0, 0), glow.filter(ImageFilter.GaussianBlur(220)))
    return im.convert("RGBA")


def phone(shot):
    sw = PHONE_W
    sh = round(shot.height * sw / shot.width)
    shot = shot.resize((sw, sh), Image.LANCZOS)
    b, edge, r = 26, 6, round(sw * 0.15)
    body = Image.new("RGBA", (sw + 2 * b, sh + 2 * b), (0, 0, 0, 0))
    d = ImageDraw.Draw(body)
    d.rounded_rectangle((0, 0, body.width - 1, body.height - 1), r + b, fill=EDGE)
    d.rounded_rectangle((edge, edge, body.width - 1 - edge, body.height - 1 - edge), r + b - edge, fill=BEZEL)
    body.paste(shot, (b, b), mask((sw, sh), r))
    return body


def shadow(im, box, r, alpha, blur):
    s = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(s).rounded_rectangle(box, r, fill=(10, 30, 90, alpha))
    im.alpha_composite(s.filter(ImageFilter.GaussianBlur(blur)))


def wrap(d, text, f, width, kw):
    """Splits text into lines no wider than width, as evenly as it can."""
    words = text.split()
    for n in range(1, len(words) + 1):
        # Try n lines: fill each to about len/n characters, then check the widths.
        lines, line, goal = [], [], len(text) / n
        for w in words:
            if line and len(" ".join(line + [w])) > goal + 4 and len(lines) < n - 1:
                lines.append(" ".join(line))
                line = []
            line.append(w)
        lines.append(" ".join(line))
        if all(d.textlength(l, font=f, **kw) <= width for l in lines):
            return lines
    return [text]


def calendar_card(shot):
    """The calendar sheet cut from the shot: the screen behind it is dimmed, so the
    sheet shows on its own, larger, over the Today screen."""
    px = shot.load()
    top = next(y for y in range(900, H - 200)
               if all(px[x, y + k] == (255, 255, 255) for x in (200, W // 2, W - 200) for k in (0, 8, 16)))
    return shot.crop((24, top + 30, W - 24, H - 148))


def frame(raw, lang, head, sub):
    rtl = lang == "ar"
    kw = {"direction": "rtl", "language": "ar"} if rtl else {}
    im = backdrop()
    d = ImageDraw.Draw(im)
    size = 120
    hf = font(lang, True, size)
    lines = wrap(d, head, hf, W - 160, kw)
    while len(lines) > 2 and size > 90:
        size -= 6
        hf = font(lang, True, size)
        lines = wrap(d, head, hf, W - 160, kw)
    y = 190
    for line in lines:
        d.text((W / 2, y), line, font=hf, fill="white", anchor="ma", **kw)
        y += round(size * 1.12)
    sf = font(lang, False, 54)
    y += 40
    for line in wrap(d, sub, sf, W - 220, kw):
        d.text((W / 2, y), line, font=sf, fill=SUB, anchor="ma", **kw)
        y += 68

    name = raw.stem
    shot = Image.open(raw).convert("RGB")
    under = shot
    if name == "3-calendar":
        under = Image.open(raw.with_name("2-today.png")).convert("RGB")
    p = phone(under)
    top = max(y + 90, 820)
    x = (W - p.width) // 2
    shadow(im, (x, top + 30, x + p.width, top + 30 + p.height), 180, 110, 50)
    im.alpha_composite(p, (x, top))
    if name == "3-calendar":
        card = calendar_card(shot)
        cw = 1200
        card = card.resize((cw, round(card.height * cw / card.width)), Image.LANCZOS)
        cx, cy = (W - cw) // 2, max(top + 560, 1380)
        shadow(im, (cx, cy + 30, cx + cw, cy + 30 + card.height), 70, 140, 45)
        im.paste(card, (cx, cy), mask(card.size, 70))
    return im.convert("RGB")


def captions(lang):
    text = (ROOT / "app-store" / "listing" / LOCALES[lang] / "captions.txt").read_text(encoding="utf-8")
    blocks = [b.strip().split("\n") for b in text.strip().split("\n\n")]
    if len(blocks) != len(SHOTS) or any(len(b) != 2 for b in blocks):
        sys.exit(f"captions.txt for {lang} needs {len(SHOTS)} blocks of two lines.")
    return blocks


def main():
    src, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    if not features.check("raqm"):
        sys.exit("Pillow has no raqm, so Arabic would come out in the wrong order.")
    for lang in LOCALES:
        if not (src / lang).is_dir():
            continue
        (out / lang).mkdir(parents=True, exist_ok=True)
        for (raw, framed), (head, sub) in zip(SHOTS, captions(lang)):
            frame(src / lang / f"{raw}.png", lang, head, sub).save(out / lang / f"{framed}.png", optimize=True)
            print(out / lang / f"{framed}.png")


if __name__ == "__main__":
    main()

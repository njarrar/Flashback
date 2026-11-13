"""Downloads sample photos and stamps them with capture dates on today's date in past years.

Used only by the screenshots workflows, to fill a fresh simulator or emulator.
The photos come from picsum.photos. When that site is down, it draws simple stand-in
pictures instead and leaves a DRAWN file next to them, so build checks still run.
Pass --real where only real photos will do (store and README screenshots): then it stops.
"""
import datetime
import io
import pathlib
import random
import sys
import time
import urllib.request

import piexif
from PIL import Image, ImageDraw

real = "--real" in sys.argv
out = pathlib.Path([a for a in sys.argv[1:] if a != "--real"][0])
out.mkdir(parents=True, exist_ok=True)
today = datetime.date.today()

# (years ago, picsum photo ids). Landscape and portrait mixed for the mosaic.
plan = [
    (1, [1015, 1016, 1018, 1019, 1020]),
    (2, [1022, 1024, 1025]),
    (4, [1036, 1037, 1039, 1040]),
    (5, [1043, 1044]),
    (7, [1047, 1049, 1050, 1051]),
    (9, [1053, 1055, 1056]),
    (11, [1057, 1059]),
]
# A few photos on nearby days, so the calendar shows dots.
nearby = [(-3, 1060), (-1, 1062), (2, 1063), (5, 1067), (9, 1068), (-8, 1069)]


site_down = False


def fetch(pid, portrait):
    global site_down
    w, h = (900, 1200) if portrait else (1200, 900)
    for attempt in range(3 if not site_down else 0):
        try:
            with urllib.request.urlopen(f"https://picsum.photos/id/{pid}/{w}/{h}", timeout=60) as r:
                return Image.open(io.BytesIO(r.read())).convert("RGB")
        except Exception as e:
            print(f"picsum.photos photo {pid}: {e}")
            time.sleep(5 * (attempt + 1))
    if real:
        sys.exit("picsum.photos is down, and store screenshots need real photos. Run again later.")
    if not site_down:
        print("::warning::picsum.photos is down, so these are drawn stand-in photos.")
        (out / "DRAWN").write_text("picsum.photos was down\n")
    site_down = True
    return draw(pid, w, h)


def draw(pid, w, h):
    """A plain landscape: sky, sun and two hills, in colors picked from the photo id."""
    rnd = random.Random(pid)
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    top, low = [rnd.randint(60, 200) for _ in range(3)], [rnd.randint(150, 255) for _ in range(3)]
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, low)))
    r = rnd.randint(w // 14, w // 8)
    x, y = rnd.randint(r, w - r), rnd.randint(r, h // 3)
    d.ellipse([x - r, y - r, x + r, y + r], fill=(255, rnd.randint(200, 240), rnd.randint(120, 200)))
    for k in range(2):
        base = h * (0.55 + 0.15 * k)
        pts = [(0, h)] + [(i * w / 6, base - rnd.randint(0, h // 6)) for i in range(7)] + [(w, h)]
        d.polygon(pts, fill=tuple(rnd.randint(30, 120) for _ in range(3)))
    return img


def save(img, when, name):
    stamp = when.strftime("%Y:%m:%d %H:%M:%S").encode()
    # Android only trusts the capture time when it also has a time zone offset.
    exif = piexif.dump({"0th": {piexif.ImageIFD.DateTime: stamp, piexif.ImageIFD.Make: b"Apple",
                                piexif.ImageIFD.Model: b"iPhone"},
                        "Exif": {piexif.ExifIFD.DateTimeOriginal: stamp, piexif.ExifIFD.DateTimeDigitized: stamp,
                                 piexif.ExifIFD.OffsetTime: b"+00:00", piexif.ExifIFD.OffsetTimeOriginal: b"+00:00",
                                 piexif.ExifIFD.OffsetTimeDigitized: b"+00:00"}})
    img.save(out / name, "JPEG", quality=88, exif=exif)


n = 0
for years_ago, ids in plan:
    day = today.replace(year=today.year - years_ago)
    for i, pid in enumerate(ids):
        when = datetime.datetime.combine(day, datetime.time(10 + i, 5 * i))
        save(fetch(pid, portrait=(n % 3 == 1)), when, f"IMG_{n:04d}.jpg")
        n += 1
for delta, pid in nearby:
    day = today.replace(year=today.year - 3) + datetime.timedelta(days=delta)
    save(fetch(pid, portrait=False), datetime.datetime.combine(day, datetime.time(12)), f"IMG_{n:04d}.jpg")
    n += 1
# The App Store calendar shot needs a month full of photo days. Early in a month most days
# are still to come (and faded), so the demo calendar opens on last month until the 15th.
first = today.replace(day=1)
shown = today if today.day >= 15 else first - datetime.timedelta(days=1)
ids = [pid for _, group in plan for pid in group]
for i, d in enumerate(d for d in (2, 4, 5, 8, 9, 12, 14, 17, 20, 21, 24, 27) if shown != today or d < today.day):
    day = datetime.date(shown.year - 2, shown.month, d)
    save(fetch(ids[i % len(ids)], portrait=False), datetime.datetime.combine(day, datetime.time(15)), f"IMG_{n:04d}.jpg")
    n += 1
print(f"made {n} photos in {out}")

"""Downloads sample photos and stamps them with capture dates on today's date in past years.

Used only by the screenshots workflow, to fill a fresh simulator or emulator.
"""
import datetime
import io
import pathlib
import sys
import urllib.request

import piexif
from PIL import Image

out = pathlib.Path(sys.argv[1])
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


def fetch(pid, portrait):
    w, h = (900, 1200) if portrait else (1200, 900)
    with urllib.request.urlopen(f"https://picsum.photos/id/{pid}/{w}/{h}", timeout=60) as r:
        return Image.open(io.BytesIO(r.read())).convert("RGB")


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
print(f"made {n} photos in {out}")
print(piexif.load(str(out / "IMG_0000.jpg"))["Exif"])

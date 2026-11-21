# Flashback video ad

The app's ad, made in code from real screens of the iPhone app, so it stays true to the
app and can be made again after each release, in every language.

| File | Size | For |
| --- | --- | --- |
| `flashback-ad-30s-<lang>.mp4` | 1080 × 1920, 30 s | Reels, TikTok, Shorts, and the Play listing (as a YouTube link) |
| `flashback-ad-15s-<lang>.mp4` | 1080 × 1920, 15 s | Paid ads |
| `flashback-app-store-preview-<lang>.mp4` | 886 × 1920, 25 s | App Store app preview |

## How it is made

1. The [Video ad](../.github/workflows/video-ad.yml) workflow builds the iPhone app, loads
   the sample photos into the newest Pro Max simulator, and for each language films the
   welcome screen and the demo scenes (today, calendar, viewer, favorites, album, select,
   cards, reel) with [capture.sh](../.github/video-ad/capture.sh). Each scene gives a
   screen recording and a screenshot in `public/shots/<lang>/`.
2. [render.mjs](render.mjs) keeps the last 4.5 seconds of each recording and renders
   every cut with [Remotion](https://www.remotion.dev/) (video from React code).
3. The videos land on the `video-ad` release of this repo.

The shots, their order and their timing are in [src/Ad.tsx](src/Ad.tsx) (`CUTS`). The
captions come from the App Store screenshot captions
(`app-store/listing/<locale>/captions.txt`) plus the few lines in [text.json](text.json).

## Rules it keeps

- The app's blue and Nunito headlines, as on the App Store screenshots. Coral shows only
  on the heart.
- The App Store preview shows only app footage under a caption: no phone frame, no
  opening question, no store badges, as Apple asks.
- Every cut has a sound track, since App Store Connect wants one.

## Sound

Soft sound effects play on each cut, on the heart tap and on the end card. They are
made from scratch by [sfx.py](sfx.py) (`python3 sfx.py` writes `public/sfx/`), so
there are no rights to clear.

There is no music by default. To add music, put a track you have the rights to at
`public/music.mp3` (it is not checked in) and render again. It fades in and out.

## Run it on a computer

```sh
cd ad
npm ci
# Put screens in public/shots/<lang>/ (the workflow's "video-ad-screens" artifact).
npm run studio   # preview and scrub in the browser
npm run render   # or: node render.mjs --langs en,ar --cuts ad-15
```

Remotion is free for individuals and teams of up to three people.

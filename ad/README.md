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

Soft background music plays under every cut, with soft sound effects on each cut, on the
heart tap and on the end card. [music.py](music.py) makes the music
(`public/music.mp3`) and [sfx.py](sfx.py) makes the effects (`public/sfx/`), both from
scratch, so there are no rights to clear.

The effects always play quieter than the music: `SFX` and `MUSIC` in
[src/Ad.tsx](src/Ad.tsx) set each effect about 4 dB under the music. Change both together.
To use other music, replace `public/music.mp3` with a track you have the rights to.

## Store specs

- App Store app preview: 886 × 1920, 30 fps, 15 to 30 seconds, H.264 at about 10 Mbps,
  stereo AAC at 256 kbps. The "App Store review" workflow puts
  `flashback-app-store-preview-<lang>.mp4` from the `video-ad` release on each store
  language (6.9" iPhone).
- Google Play takes a YouTube link only. Put the link in
  `play-store/listing/<locale>/video.txt` and run the Play Store workflow with the listing on.

## When the app changes a lot

After a big update, run the Screenshots, App Store and Video ad workflows again so the
store screenshots and videos show the new app, then send the listing to both stores.

## Run it on a computer

```sh
cd ad
npm ci
# Put screens in public/shots/<lang>/ (the workflow's "video-ad-screens" artifact).
npm run studio   # preview and scrub in the browser
npm run render   # or: node render.mjs --langs en,ar --cuts ad-15
```

Remotion is free for individuals and teams of up to three people.

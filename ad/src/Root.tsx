import React from "react";
import { Composition } from "remotion";
import { Social, Preview, CUTS, FPS, AdProps, cutFrames } from "./Ad";

// Studio defaults. render.mjs passes the real props for each language.
const defaults: AdProps = {
  lang: "en",
  other: "ar",
  date: "2026-11-21",
  caps: [
    ["This day, every year", "Photos you took on today's date, sorted by year."],
    ["Jump to any day", "A tiny photo marks each day that has memories."],
    ["Relive the moment", "Swipe through each photo full screen."],
    ["Keep the ones you love", "Tap the heart to save a favorite."],
    ["Make an album, share it", "Pick photos, give it a name, send it to family."],
    ["Private by design", "No account, no cloud. Your photos stay on your phone."],
  ],
  text: {
    hook: "What were you doing on this day?",
    reel: "Turn a day into a reel",
    reelSub: "Pick photos, get a short video to share.",
    cards: "Flip through the years",
    cardsSub: "One card for each year.",
    langs: "In 6 languages",
    end: "On iPhone and Android",
  },
  clips: {},
  music: false,
};

export const Root: React.FC = () => (
  <>
    <Composition id="ad-30" component={Social} width={1080} height={1920} fps={FPS}
      durationInFrames={cutFrames(CUTS.ad30)} defaultProps={{ ...defaults, cut: "ad30" as const }} />
    <Composition id="ad-15" component={Social} width={1080} height={1920} fps={FPS}
      durationInFrames={cutFrames(CUTS.ad15)} defaultProps={{ ...defaults, cut: "ad15" as const }} />
    <Composition id="preview" component={Preview} width={886} height={1920} fps={FPS}
      durationInFrames={cutFrames(CUTS.preview)} defaultProps={{ ...defaults, cut: "preview" as const }} />
  </>
);

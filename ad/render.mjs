// Renders the Flashback ad from the screens in public/shots/<lang>/.
//   node render.mjs                     every cut in every language that has screens
//   node render.mjs --langs en,ar       only these languages
//   node render.mjs --cuts ad-15        only this cut (ad-30, ad-15, preview)
// Videos land in out/. The Video ad workflow fills public/shots from the iPhone simulator.
import { bundle } from "@remotion/bundler";
import { renderMedia, selectComposition } from "@remotion/renderer";
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const here = path.dirname(new URL(import.meta.url).pathname);
const shots = path.join(here, "public", "shots");
const arg = (name, all) => {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1].split(",") : all;
};
const LOCALES = { en: "en-US", fr: "fr-FR", es: "es-ES", ar: "ar-SA", pt: "pt-BR", zh: "zh-Hans" };
const langs = arg("langs", Object.keys(LOCALES)).filter((l) => fs.existsSync(path.join(shots, l)));
const cuts = arg("cuts", ["ad-30", "ad-15", "preview"]);
const NAMES = { "ad-30": "flashback-ad-30s", "ad-15": "flashback-ad-15s", preview: "flashback-app-store-preview" };
const SCENES = ["today", "viewer", "favorites", "calendar", "select", "album", "reel", "welcome", "cards"];
const text = JSON.parse(fs.readFileSync(path.join(here, "text.json"), "utf8"));
const dateFile = path.join(shots, "date.txt");
const date = fs.existsSync(dateFile) ? fs.readFileSync(dateFile, "utf8").trim() : new Date().toISOString().slice(0, 10);

// The App Store shot captions: six blocks of a headline and a line under it.
function captions(lang) {
  const file = path.join(here, "..", "app-store", "listing", LOCALES[lang], "captions.txt");
  const blocks = fs.readFileSync(file, "utf8").trim().split(/\n\s*\n/).map((b) => b.split("\n").map((s) => s.trim()));
  if (blocks.length !== 6) throw new Error(`${file} needs 6 blocks, has ${blocks.length}`);
  return blocks.map(([h, s]) => [h, s ?? ""]);
}

// Keeps the last 4.5 seconds of each screen recording, once the scene has settled.
function tails(lang) {
  const clips = {};
  for (const s of SCENES) {
    const src = path.join(shots, lang, `${s}.mp4`);
    const out = path.join(shots, lang, `${s}-tail.mp4`);
    if (!fs.existsSync(src)) continue;
    if (!fs.existsSync(out)) {
      execFileSync("ffmpeg", ["-v", "error", "-y", "-sseof", "-4.5", "-i", src, "-an", "-vf", "fps=30,scale=1320:-2",
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18", out]);
    }
    clips[s] = true;
  }
  return clips;
}

if (!langs.length) {
  console.error("No screens in public/shots/<lang>/. Run the Video ad workflow, or copy screenshots there.");
  process.exit(1);
}
console.log(`Date ${date}, languages ${langs.join(" ")}, cuts ${cuts.join(" ")}`);
// Trim the clips first: the bundle copies public/ as it is now.
const music = fs.existsSync(path.join(here, "public", "music.mp3"));
const all = langs.map((lang) => {
  const other = lang === "ar" ? "en" : "ar";
  return {
    lang, other: fs.existsSync(path.join(shots, other)) ? other : lang, date,
    caps: captions(lang), text: text[lang], clips: tails(lang), music,
  };
});
const serveUrl = await bundle({ entryPoint: path.join(here, "src", "index.ts") });
// Set BROWSER to use a Chrome already on the machine instead of downloading one.
const browserExecutable = process.env.BROWSER || null;
for (const props of all) {
  for (const id of cuts) {
    const inputProps = { ...props, cut: id === "ad-15" ? "ad15" : "ad30" };
    const composition = await selectComposition({ serveUrl, id, inputProps, browserExecutable });
    const outputLocation = path.join(here, "out", props.lang, `${NAMES[id]}-${props.lang}.mp4`);
    await renderMedia({
      composition, serveUrl, codec: "h264", inputProps, outputLocation, crf: 18, browserExecutable,
      // App Store Connect wants a sound track, so silent cuts still get one.
      enforceAudioTrack: true,
    });
    console.log(`Made ${path.relative(here, outputLocation)}`);
  }
}

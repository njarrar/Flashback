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
const TAIL_SECONDS = 4.5;
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

function probeDuration(file) {
  try {
    const out = execFileSync("ffprobe", [
      "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file,
    ]).toString().trim();
    const d = Number(out);
    return Number.isFinite(d) && d > 0 ? d : 0;
  } catch {
    return 0;
  }
}

function frameCount(file) {
  try {
    return Number(execFileSync("ffprobe", ["-v", "error", "-select_streams", "v:0", "-count_frames",
      "-show_entries", "stream=nb_read_frames", "-of", "csv=p=0", file]).toString().trim()) || 0;
  } catch {
    return 0;
  }
}

// Keeps the last 4.5 seconds of real live motion from each screen recording.
// Only pads the exact deficit when a VFR simulator capture ended earlier than 4.5s
// so long recordings (such as reel.mp4 or Preview scenes) are never frozen into a still tail.
function tails(lang) {
  const clips = {};
  for (const s of SCENES) {
    const src = path.join(shots, lang, `${s}.mp4`);
    const out = path.join(shots, lang, `${s}-tail.mp4`);
    if (!fs.existsSync(src)) continue;
    if (!fs.existsSync(out)) {
      try {
        const dur = probeDuration(src);
        if (dur >= TAIL_SECONDS) {
          execFileSync("ffmpeg", [
            "-v", "error", "-y", "-sseof", `-${TAIL_SECONDS}`, "-i", src, "-an",
            "-vf", "fps=30,scale=1320:-2", "-t", String(TAIL_SECONDS),
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18", out,
          ]);
        } else if (dur > 0) {
          const pad = Math.max(0, TAIL_SECONDS + 0.1 - dur).toFixed(3);
          execFileSync("ffmpeg", [
            "-v", "error", "-y", "-i", src, "-an",
            "-vf", `fps=30,tpad=stop_mode=clone:stop_duration=${pad},scale=1320:-2`,
            "-t", String(TAIL_SECONDS),
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18", out,
          ]);
        }
      } catch (e) {
        console.warn(`${lang}/${s}.mp4: ${e.message}`);
      }
    }
    const n = fs.existsSync(out) ? frameCount(out) : 0;
    if (n >= 120) clips[s] = true;
    else {
      console.warn(`${lang}/${s}: recording gave ${n} frames, using the screenshot.`);
      fs.rmSync(out, { force: true });
    }
  }
  return clips;
}

function checkLoudness(file) {
  try {
    const res = execFileSync("ffmpeg", [
      "-hide_banner", "-i", file, "-af", "ebur128=peak=true", "-f", "null", "-",
    ], { stdio: ["ignore", "ignore", "pipe"] }).toString();
    const lufs = [...res.matchAll(/I:\s+([-\d.]+)\s+LUFS/g)].pop()?.[1];
    const tp = [...res.matchAll(/Peak:\s+([-\d.]+)\s+dBFS/g)].pop()?.[1];
    if (lufs && tp) {
      const i = Number(lufs), p = Number(tp);
      console.log(`  Audio ${path.basename(file)}: ${i.toFixed(1)} LUFS, ${p.toFixed(1)} dBTP`);
      if (i < -17.5 || i > -12.5 || p > -0.8) {
        console.warn(`  WARNING: loudness out of target (-16..-14 LUFS, <= -1.0 dBTP): ${i} LUFS / ${p} dBTP`);
      }
    }
  } catch {
    // Non-fatal if ffmpeg probe fails.
  }
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
    const cut = id === "ad-15" ? "ad15" : id === "preview" ? "preview" : "ad30";
    const inputProps = { ...props, cut };
    const composition = await selectComposition({ serveUrl, id, inputProps, browserExecutable });
    const outputLocation = path.join(here, "out", props.lang, `${NAMES[id]}-${props.lang}.mp4`);
    await renderMedia({
      composition, serveUrl, codec: "h264", inputProps, outputLocation, browserExecutable,
      // Apple asks app previews for about 10 Mbps video and 256 kbps stereo sound.
      ...(id === "preview" ? { videoBitrate: "10M", audioBitrate: "256k" } : { crf: 18 }),
      // App Store Connect wants a sound track, so silent cuts still get one.
      enforceAudioTrack: true,
    });
    console.log(`Made ${path.relative(here, outputLocation)}`);
    if (props.music) checkLoudness(outputLocation);
  }
}

import React from "react";
import {
  AbsoluteFill, Audio, Img, OffthreadVideo, Sequence, interpolate, spring,
  staticFile, useCurrentFrame, useVideoConfig,
} from "remotion";
import { loadFont } from "@remotion/fonts";

export const FPS = 30;

// The app's colors (Theme.swift / Theme.kt). Coral only for the heart, as in the app.
const BLUE = "#2E7BFF";
const NIGHT = "#0D0D0E";
const CORAL = "#FF6A52";
const WHITE = "#FFFFFF";

// The fonts ship in public/fonts, so a render needs no network.
const font = (family: string, file: string, weight: string) =>
  loadFont({ family, url: staticFile(`fonts/${file}.woff2`), weight });
font("Nunito", "Nunito-900-latin", "900");
font("Inter", "Inter-500-latin", "500");
font("Tajawal Arabic", "Tajawal-500-arabic", "500");
font("Tajawal Arabic", "Tajawal-800-arabic", "800");
font("Tajawal Latin", "Tajawal-500-latin", "500");
font("Tajawal Latin", "Tajawal-800-latin", "800");
const nunito = "Nunito, sans-serif";
const inter = "Inter, sans-serif";
const tajawal = "'Tajawal Arabic', 'Tajawal Latin', sans-serif";
// Chinese uses the system's Noto Sans CJK (the workflow installs fonts-noto-cjk).
const cjk = "'Noto Sans CJK SC', 'Noto Sans SC', sans-serif";

export type Text = {
  hook: string; reel: string; reelSub: string; cards: string; cardsSub: string; langs: string; end: string;
};
export type AdProps = {
  lang: string;
  other: string;
  date: string;
  // Headline and line under it for the six App Store shots, in order: today, calendar,
  // viewer, favorites, album, private. From app-store/listing/<locale>/captions.txt.
  caps: [string, string][];
  text: Text;
  // Scenes that have a recorded clip (shots/<lang>/<scene>-tail.mp4).
  clips: Record<string, boolean>;
  music: boolean;
  cut?: "ad30" | "ad15" | "preview";
};

type Shot = { k: string; d: number };
export const CUTS: Record<"ad30" | "ad15" | "preview", Shot[]> = {
  // Varied rhythmic pacing: brisk mid-section showcase, extra breath on Heart, Reel, Privacy, and End (30.0s total).
  ad30: [
    { k: "hook", d: 2.2 }, { k: "today", d: 2.8 }, { k: "viewer", d: 2.0 }, { k: "heart", d: 2.8 },
    { k: "calendar", d: 2.2 }, { k: "album", d: 2.6 }, { k: "reel", d: 3.6 }, { k: "cards", d: 2.2 },
    { k: "langs", d: 2.4 }, { k: "privacy", d: 3.4 }, { k: "end", d: 3.8 },
  ],
  ad15: [
    { k: "hook", d: 2.0 }, { k: "today", d: 2.6 }, { k: "heart", d: 2.6 }, { k: "reel", d: 2.8 },
    { k: "privacy", d: 2.2 }, { k: "end", d: 2.8 },
  ],
  // App Store preview: real app footage only, with captions. No phone frame, hook or badges.
  preview: [
    { k: "today", d: 4 }, { k: "viewer", d: 3.5 }, { k: "favorites", d: 3 }, { k: "calendar", d: 3.5 },
    { k: "album", d: 4 }, { k: "reel", d: 4 }, { k: "privacy", d: 3 },
  ],
};
export const cutFrames = (shots: Shot[]) => Math.round(shots.reduce((a, s) => a + s.d, 0) * FPS);

const fonts = (lang: string) =>
  lang === "ar" ? { display: tajawal, body: tajawal, weight: 800 }
  : lang === "zh" ? { display: cjk, body: cjk, weight: 700 }
  : { display: nunito, body: inter, weight: 900 };

const LOCALES: Record<string, string> = {
  en: "en-US", fr: "fr-FR", es: "es-ES", ar: "ar-SA-u-ca-gregory-nu-latn", pt: "pt-BR", zh: "zh-CN",
};

const shot = (lang: string, name: string) => staticFile(`shots/${lang}/${name}.png`);
const clip = (lang: string, name: string) => staticFile(`shots/${lang}/${name}-tail.mp4`);

// Sound effects made by sfx.py (public/sfx) and bed made by music.py (-14.5 LUFS master).
// Mixed for -14 to -16 LUFS social/App Store loudness, with automatic bed ducking under SFX.
const MUSIC = 0.88;
const DUCK_GAIN = 0.72; // ~ -2.85 dB duck under tactile SFX and closing chime
const SFX: Record<string, number> = { whoosh: 0.36, tap: 0.55, pop: 0.58, chime: 0.62 };
const Sfx: React.FC<{ name: string }> = ({ name }) =>
  <Audio src={staticFile(`sfx/${name}.wav`)} volume={SFX[name]} />;

// Only play whoosh on major act transitions (never before `end`, so it never overlaps `chime`).
const WHOOSH_BEFORE = new Set(["today", "calendar", "reel", "privacy"]);

// Heart tap fires after the Phone entrance spring settles.
const HEART_AT = 22;

// Plays every shot back to back.
const Shots: React.FC<{ shots: Shot[]; render: (k: string, frames: number) => React.ReactNode }> = ({ shots, render }) => {
  let from = 0;
  return (
    <>
      {shots.map((s, i) => {
        const frames = Math.round(s.d * FPS);
        const playWhoosh = i > 0 && WHOOSH_BEFORE.has(s.k);
        const el = (
          <React.Fragment key={i}>
            <Sequence from={from} durationInFrames={frames} name={s.k}>
              {render(s.k, frames)}
            </Sequence>
            {playWhoosh ? (
              <Sequence from={Math.max(0, from - 10)} durationInFrames={21} name="whoosh">
                <Sfx name="whoosh" />
              </Sequence>
            ) : null}
          </React.Fragment>
        );
        from += frames;
        return el;
      })}
    </>
  );
};

const useRise = (delay = 0, distance = 120) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const p = spring({ frame: frame - delay, fps, config: { damping: 18, stiffness: 120, mass: 0.8 } });
  return { opacity: Math.min(1, p * 1.4), transform: `translateY(${(1 - p) * distance}px)` };
};

const Caption: React.FC<{
  lang: string;
  h: string;
  s?: string;
  top?: number;
  size?: number;
  extra?: React.ReactNode;
}> = ({ lang, h, s, top = 185, size = 92, extra }) => {
  const f = fonts(lang);
  const head = useRise(0, 40);
  const sub = useRise(4, 40);
  const headSize = h.length > 22 ? Math.round(size * 0.85) : size;
  const subSize = Math.round(headSize * 0.44);
  return (
    <div style={{
      position: "absolute", top, left: 70, right: 70, maxWidth: 940, margin: "0 auto",
      textAlign: "center", color: WHITE, direction: lang === "ar" ? "rtl" : "ltr",
      display: "flex", flexDirection: "column", gap: 18,
    }}>
      <div style={{ ...head, fontFamily: f.display, fontWeight: f.weight, fontSize: headSize, lineHeight: 1.06, textWrap: "balance" }}>{h}</div>
      {s ? (
        <div style={{
          ...sub, fontFamily: f.body, fontWeight: 500, fontSize: subSize, lineHeight: 1.3,
          opacity: 0.92 * (sub.opacity as number), textWrap: "balance",
        }}>
          {s}
        </div>
      ) : null}
      {extra}
    </div>
  );
};

// A phone: black bezel around a real screen. Width sets the size; the screen keeps the
// simulator's shape (1320 x 2868).
const Phone: React.FC<{ x: number; y: number; w: number; children: React.ReactNode; delay?: number; rotate?: number }> = ({ x, y, w, children, delay = 0, rotate = 0 }) => {
  const rise = useRise(delay, 260);
  const bezel = w * 0.024;
  const screenW = w - bezel * 2;
  const screenH = screenW * (2868 / 1320);
  return (
    <div style={{ position: "absolute", left: x, top: y, width: w, height: screenH + bezel * 2, ...rise,
      transform: `${rise.transform} rotate(${rotate}deg)`, background: "#0A0A0B", borderRadius: w * 0.14,
      padding: bezel, boxShadow: "0 40px 90px rgba(0,0,0,0.32)" }}>
      <div style={{ width: screenW, height: screenH, borderRadius: w * 0.118, overflow: "hidden", position: "relative", background: "#F5F5F1" }}>
        {children}
      </div>
    </div>
  );
};

const Screen: React.FC<{ src: string }> = ({ src }) => {
  const frame = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();
  const scale = interpolate(frame, [0, durationInFrames], [1, 1.025]);
  return <Img src={src} style={{ width: "100%", height: "100%", objectFit: "cover", transform: `scale(${scale})`, transformOrigin: "50% 30%" }} />;
};

const Fade: React.FC<{ at: number; children: React.ReactNode }> = ({ at, children }) => {
  const frame = useCurrentFrame();
  const o = interpolate(frame, [at, at + 8], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return <AbsoluteFill style={{ opacity: o }}>{children}</AbsoluteFill>;
};

const HeartPath = "M12 21s-7.5-4.6-9.6-9.2C.9 8.4 3 4.5 6.8 4.5c2.1 0 3.5 1.1 5.2 3 1.7-1.9 3.1-3 5.2-3 3.8 0 5.9 3.9 4.4 7.3C19.5 16.4 12 21 12 21z";

// A finger tap, then the Favorite button fills coral, matching the app's real button size and center.
const HeartTap: React.FC<{ lang: string; at: number; phoneW: number }> = ({ lang, at, phoneW }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  // The Favorite button in the viewer, as a share of the screen (mirrored right to left in Arabic).
  const x = lang === "ar" ? 0.882 : 0.118;
  const y = 0.908;
  const screenW = phoneW * (1 - 0.048);
  const heartSize = Math.round(screenW * 0.058);
  const ringSize = Math.round(heartSize * 2.8);
  const ring = interpolate(frame, [at - 6, at, at + 10], [0, 1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const pop = spring({ frame: frame - at, fps, config: { damping: 9, stiffness: 180 } });
  return (
    <>
      <Sequence from={at - 2} layout="none"><Sfx name="tap" /></Sequence>
      <Sequence from={at} layout="none"><Sfx name="pop" /></Sequence>
      <div style={{
        position: "absolute", left: `${x * 100}%`, top: `${y * 100}%`,
        width: ringSize, height: ringSize, marginLeft: -ringSize / 2, marginTop: -ringSize / 2,
        borderRadius: "50%", background: "rgba(255,255,255,0.35)", border: "3px solid rgba(255,255,255,0.9)",
        opacity: ring, transform: `scale(${0.6 + ring * 0.4})`,
      }} />
      <svg viewBox="0 0 24 24" style={{
        position: "absolute", left: `${x * 100}%`, top: `${y * 100}%`,
        width: heartSize, height: heartSize, marginLeft: -heartSize / 2, marginTop: -heartSize / 2,
        transform: `scale(${pop})`, opacity: pop > 0.02 ? 1 : 0,
      }}>
        <path d={HeartPath} fill={CORAL} />
      </svg>
    </>
  );
};

const Hook: React.FC<{ p: AdProps }> = ({ p }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const f = fonts(p.lang);
  const day = new Date(`${p.date}T12:00:00Z`);
  const label = new Intl.DateTimeFormat(LOCALES[p.lang] ?? "en-US", { month: "long", day: "numeric", timeZone: "UTC" }).format(day);
  const year = day.getUTCFullYear();
  // The sample photos sit 1, 4, 7 and 11 years back (make_photos.py).
  const years = [1, 4, 7, 11].map((n) => year - n);
  const dateIn = spring({ frame: frame - 6, fps, config: { damping: 16 } });
  const dateSize = label.length > 12 ? 112 : 144;
  return (
    <AbsoluteFill style={{ background: NIGHT }}>
      <Caption lang={p.lang} h={p.text.hook} />
      <div style={{
        position: "absolute", top: 640, left: 0, right: 0, padding: "0 60px",
        display: "flex", flexDirection: "column", alignItems: "center", gap: 26,
        color: WHITE, textAlign: "center",
      }}>
        <div style={{
          fontFamily: f.display, fontWeight: f.weight, fontSize: dateSize, lineHeight: 1.06,
          textAlign: "center", textWrap: "balance", opacity: dateIn,
          transform: `scale(${0.85 + dateIn * 0.15})`, direction: p.lang === "ar" ? "rtl" : "ltr",
        }}>
          {label}
        </div>
        {years.map((y, i) => {
          const d = spring({ frame: frame - 16 - i * 5, fps, config: { damping: 14 } });
          return (
            <div key={y} style={{ fontFamily: nunito, fontWeight: 900, fontSize: 92, lineHeight: 1, opacity: d * (0.95 - i * 0.2), transform: `translateY(${(1 - d) * -50}px)` }}>{y}</div>
          );
        })}
      </div>
    </AbsoluteFill>
  );
};

const End: React.FC<{ p: AdProps; badges?: boolean }> = ({ p, badges = true }) => {
  const f = fonts(p.lang);
  const a = useRise(0, 80), b = useRise(5, 80), c = useRise(10, 80), d = useRise(15, 80);
  return (
    <AbsoluteFill style={{ background: BLUE, alignItems: "center", justifyContent: "center", color: WHITE, gap: 40 }}>
      <Sequence from={4} layout="none"><Sfx name="chime" /></Sequence>
      <Img src={staticFile("icon.png")} style={{ ...a, width: 300, height: 300, borderRadius: 68, boxShadow: "0 30px 70px rgba(0,0,0,0.28)" }} />
      <div style={{ ...b, fontFamily: nunito, fontWeight: 900, fontSize: 132, lineHeight: 1 }}>Flashback</div>
      <div style={{
        ...c, fontFamily: f.body, fontWeight: 500, fontSize: 46, padding: "0 70px",
        textAlign: "center", textWrap: "balance", direction: p.lang === "ar" ? "rtl" : "ltr",
      }}>
        {p.text.end}
      </div>
      {badges ? (
        <div style={{ ...d, display: "flex", gap: 24, marginTop: 20 }}>
          {["App Store", "Google Play"].map((s) => (
            <div key={s} style={{ fontFamily: inter, fontWeight: 500, fontSize: 38, padding: "16px 34px", borderRadius: 20, border: "3px solid rgba(255,255,255,0.85)" }}>{s}</div>
          ))}
        </div>
      ) : null}
    </AbsoluteFill>
  );
};

const LANG_LIST = ["English", "Français", "Español", "العربية", "Português", "中文"];

// One phone in the middle, cut off by the bottom of the frame, under a caption.
const PX = 130, PY = 600, PW = 820;

const SocialShot: React.FC<{ k: string; frames: number; p: AdProps }> = ({ k, frames, p }) => {
  const L = p.lang;
  const cap = (i: number, top?: number) => <Caption lang={L} h={p.caps[i][0]} s={p.caps[i][1]} top={top} />;
  const one = (src: string, extra?: React.ReactNode) => (
    <Phone x={PX} y={PY} w={PW}><Screen src={src} />{extra}</Phone>
  );
  switch (k) {
    case "hook": return <Hook p={p} />;
    case "end": return <End p={p} />;
    case "today": return <AbsoluteFill style={{ background: BLUE }}>{cap(0)}{one(shot(L, "today"))}</AbsoluteFill>;
    case "viewer": return <AbsoluteFill style={{ background: BLUE }}>{cap(2)}{one(shot(L, "viewer"))}</AbsoluteFill>;
    case "heart": {
      const swap = Math.max(HEART_AT + 24, Math.round(frames * 0.62));
      const heartPhoneW = 500;
      return (
        <AbsoluteFill style={{ background: BLUE }}>
          {cap(3, 165)}
          {/* Lifted into TikTok/Reels/Shorts safe zone (HeartTap lands at Y = 1421px <= 1450px). */}
          <Phone x={290} y={470} w={heartPhoneW}>
            <Screen src={shot(L, "viewer")} />
            <HeartTap lang={L} at={HEART_AT} phoneW={heartPhoneW} />
            <Fade at={swap}><Screen src={shot(L, "favorites")} /></Fade>
          </Phone>
        </AbsoluteFill>
      );
    }
    // Smaller, so the calendar sheet at the bottom of the screen shows cleanly below 2-line captions.
    case "calendar": return (
      <AbsoluteFill style={{ background: BLUE }}>
        {cap(1, 175)}
        <Phone x={260} y={515} w={560}><Screen src={shot(L, "calendar")} /></Phone>
      </AbsoluteFill>
    );
    case "album": return (
      <AbsoluteFill style={{ background: BLUE }}>
        {cap(4)}
        {one(shot(L, "select"), <Fade at={Math.round(frames * 0.45)}><Screen src={shot(L, "album")} /></Fade>)}
      </AbsoluteFill>
    );
    case "reel": return (
      <AbsoluteFill style={{ background: BLUE }}>
        <Caption lang={L} h={p.text.reel} s={p.text.reelSub} />
        <Phone x={PX} y={PY} w={PW}>
          {p.clips.reel
            ? <OffthreadVideo src={clip(L, "reel")} muted style={{ width: "100%", height: "100%", objectFit: "cover" }} />
            : <Screen src={shot(L, "reel")} />}
        </Phone>
      </AbsoluteFill>
    );
    case "cards": return (
      <AbsoluteFill style={{ background: BLUE }}>
        <Caption lang={L} h={p.text.cards} s={p.text.cardsSub} />
        {one(shot(L, "cards"))}
      </AbsoluteFill>
    );
    case "langs": return (
      <AbsoluteFill style={{ background: BLUE }}>
        <Caption
          lang={L}
          h={p.text.langs}
          extra={
            <div style={{
              color: WHITE, opacity: 0.92, fontFamily: `Inter, ${tajawal}, ${cjk}`,
              fontWeight: 500, fontSize: 36, lineHeight: 1.35, textAlign: "center",
            }}>
              {LANG_LIST.map((name, idx) => (
                <React.Fragment key={name}>
                  {idx > 0 ? " · " : null}
                  <bdi>{name}</bdi>
                </React.Fragment>
              ))}
            </div>
          }
        />
        {/* Keep right phone inside X <= 940px to stay clear of TikTok/Reels right action rail. */}
        <Phone x={60} y={550} w={420} rotate={-4}><Screen src={shot(L, "today")} /></Phone>
        <Phone x={480} y={620} w={420} rotate={4} delay={5}><Screen src={shot(p.other, "today")} /></Phone>
      </AbsoluteFill>
    );
    case "privacy": return <AbsoluteFill style={{ background: BLUE }}>{cap(5)}{one(shot(L, "welcome"))}</AbsoluteFill>;
    default: return null;
  }
};

type DuckWindow = [number, number];

function duckWindows(shots: Shot[]): DuckWindow[] {
  const wins: DuckWindow[] = [];
  let from = 0;
  for (const s of shots) {
    const frames = Math.round(s.d * FPS);
    if (s.k === "heart") {
      wins.push([from + HEART_AT - 4, from + HEART_AT + 16]);
    } else if (s.k === "end") {
      wins.push([from + 2, from + 44]);
    }
    from += frames;
  }
  return wins;
}

const Music: React.FC<{ on: boolean; frames: number; shots: Shot[] }> = ({ on, frames, shots }) => {
  if (!on) return null;
  const ducks = duckWindows(shots);
  return (
    <Audio
      src={staticFile("music.mp3")}
      volume={(f) => {
        const base = interpolate(f, [0, 18, frames - 75, frames], [0, MUSIC, MUSIC, 0], {
          extrapolateLeft: "clamp", extrapolateRight: "clamp",
        });
        let duck = 1;
        for (const [start, end] of ducks) {
          if (f >= start - 8 && f <= end + 8) {
            const env = interpolate(f, [start - 8, start, end, end + 8], [1, DUCK_GAIN, DUCK_GAIN, 1], {
              extrapolateLeft: "clamp", extrapolateRight: "clamp",
            });
            duck = Math.min(duck, env);
          }
        }
        return base * duck;
      }}
    />
  );
};

export const Social: React.FC<AdProps> = (p) => {
  const shots = CUTS[p.cut === "ad15" ? "ad15" : "ad30"];
  return (
    <AbsoluteFill style={{ background: BLUE }}>
      <Shots shots={shots} render={(k, frames) => <SocialShot k={k} frames={frames} p={p} />} />
      <Music on={p.music} frames={cutFrames(shots)} shots={shots} />
    </AbsoluteFill>
  );
};

// App Store preview: the real screen under a caption on the app's blue, with no phone
// frame, since Apple wants previews made of app footage.
const Full: React.FC<{ p: AdProps; scene: string }> = ({ p, scene }) =>
  p.clips[scene]
    ? <OffthreadVideo src={clip(p.lang, scene)} muted style={{ width: "100%", height: "100%", objectFit: "cover" }} />
    : <Img src={shot(p.lang, scene)} style={{ width: "100%", height: "100%", objectFit: "cover" }} />;

const SCREEN_TOP = 330;

const Band: React.FC<{ lang: string; h: string; s?: string }> = ({ lang, h, s }) => {
  const f = fonts(lang);
  const a = useRise(0, 30), b = useRise(4, 30);
  return (
    <div style={{ position: "absolute", left: 40, right: 40, top: 0, height: SCREEN_TOP, textAlign: "center", color: WHITE,
      direction: lang === "ar" ? "rtl" : "ltr", display: "flex", flexDirection: "column", justifyContent: "center", gap: 14 }}>
      <div style={{ ...a, fontFamily: f.display, fontWeight: f.weight, fontSize: 70, lineHeight: 1.08, textWrap: "balance" }}>{h}</div>
      {s ? (
        <div style={{
          ...b, fontFamily: f.body, fontWeight: 500, fontSize: 32, lineHeight: 1.35,
          opacity: 0.92 * (b.opacity as number), textWrap: "balance",
        }}>
          {s}
        </div>
      ) : null}
    </div>
  );
};

const PreviewShot: React.FC<{ k: string; frames: number; p: AdProps }> = ({ k, frames, p }) => {
  const c = p.caps;
  const map: Record<string, { scene: string; h: string; s?: string; then?: string }> = {
    today: { scene: "today", h: c[0][0], s: c[0][1] },
    viewer: { scene: "viewer", h: c[2][0], s: c[2][1] },
    favorites: { scene: "favorites", h: c[3][0], s: c[3][1] },
    calendar: { scene: "calendar", h: c[1][0], s: c[1][1] },
    album: { scene: "select", h: c[4][0], s: c[4][1], then: "album" },
    reel: { scene: "reel", h: p.text.reel, s: p.text.reelSub },
    privacy: { scene: "welcome", h: c[5][0], s: c[5][1] },
  };
  const m = map[k];
  return (
    <AbsoluteFill style={{ background: BLUE }}>
      <Band lang={p.lang} h={m.h} s={m.s} />
      {/* The screen keeps its shape and fills the rest of the height. */}
      <div style={{ position: "absolute", top: SCREEN_TOP, height: 1920 - SCREEN_TOP, width: (1920 - SCREEN_TOP) * (1320 / 2868),
        left: (886 - (1920 - SCREEN_TOP) * (1320 / 2868)) / 2, borderRadius: "44px 44px 0 0", overflow: "hidden" }}>
        <Full p={p} scene={m.scene} />
        {m.then ? <Fade at={Math.round(frames * 0.45)}><Full p={p} scene={m.then} /></Fade> : null}
      </div>
    </AbsoluteFill>
  );
};

export const Preview: React.FC<AdProps> = (p) => (
  <AbsoluteFill style={{ background: BLUE }}>
    <Shots shots={CUTS.preview} render={(k, frames) => <PreviewShot k={k} frames={frames} p={p} />} />
    <Music on={p.music} frames={cutFrames(CUTS.preview)} shots={CUTS.preview} />
  </AbsoluteFill>
);

# Back Then

An iPhone and Android app that shows the photos you took on today's date in past
years, grouped by year. Built from the "On This Day" design in [`design/`](design/)
(open `design/On This Day.dc.html` for the clickable prototype).

## What it does

- **Today**: every past year with photos on this date, newest first, as a big year
  number and a photo mosaic. Pull down to re-read the library.
- **Calendar**: pick any month and day (not year, since every year shows at once).
  Dots mark days with photos. Empty days offer the nearest days that have some.
- **Viewer**: swipe through a day's photos; heart, share, or add to an album.
- **Favorites**: every hearted photo, from any day.
- **Albums**: tap Select on Today, pick photos, tap Album. Albums can be shared
  through the system share sheet or saved as a real album in Photos / Gallery.
- **Daily reminder**: one notification a day, only on days with photos from past years.
- iPhone and Android chrome follow each platform, as in the design: floating tab bar
  and sheets on iPhone, navigation bar and centered date dialog on Android.

## Photo sources

**Phone library** (Apple Photos on iPhone, the gallery on Android) is read with
`expo-media-library`. On Android this already includes Google Photos pictures that
are stored on the phone.

**Google Photos** works differently. Since March 31, 2025, Google no longer lets
third-party apps read a user's whole Google Photos library
([Google's notice](https://developers.google.com/photos/support/updates)). The
supported way in is the **Google Photos Picker API**: the user picks photos in a
Google screen and the app gets only those. Back Then saves a copy of each picked
photo on the phone and folds it into the same day-by-day view, skipping shots that
are already in the phone library. Users can pick more at any time from the "..." menu.

## Running it

This app uses native modules, so it needs a development build, not Expo Go.

```bash
npm install
npx expo run:ios       # needs Xcode
npx expo run:android   # needs Android Studio
# or build in the cloud:
npx eas-cli@latest build --profile development --platform all
```

Checks:

```bash
npm run typecheck
npm run lint
npm test
```

## Turning on Google Photos import

1. In Google Cloud Console, create a project and enable the **Photos Picker API**.
2. Set up the OAuth consent screen and add the scope
   `https://www.googleapis.com/auth/photospicker.mediaitems.readonly`.
3. Create OAuth client IDs: one **Web**, one **iOS** (bundle ID
   `com.njarrar.backthen`), one **Android** (package `com.njarrar.backthen` plus your
   signing key's SHA-1).
4. Copy `.env.example` to `.env.local` and fill in the web client ID, the iOS client
   ID, and the iOS URL scheme (the iOS client ID reversed, e.g.
   `com.googleusercontent.apps.1234-abcd`). Rebuild the app.

Without these the app still runs with the phone library, and the Google screen says
the build has no client ID.

## Not built yet

- **Share as a link.** The design shows `backthen.app/a/...` album links. That needs
  a server to host the photos, so for now albums share as files through the share sheet.
- **Hidden dates** and the year-by-year "strip" layout from the design's tweaks.

## Layout

- `src/app/` screens (Expo Router): onboarding, Today, Favorites, Albums.
- `src/components/` sheets, viewer, photo grids, shared UI.
- `src/lib/` photo library access, day index, Google import, reminders, app state.

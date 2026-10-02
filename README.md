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

**Phone library**: PhotoKit on iPhone, MediaStore on Android. On Android this
already includes Google Photos pictures stored on the phone.

**Google Photos** works differently. Since March 31, 2025, Google no longer lets
other apps read a user's whole Google Photos library
([Google's notice](https://developers.google.com/photos/support/updates)). The
supported way in is the **Google Photos Picker API**: the user picks photos in a
Google screen and the app gets only those. Back Then keeps a copy of each picked
photo on the phone and folds it into the same day-by-day view, skipping shots
already in the phone library. Users can pick more at any time from the "..." menu.

## Layout

Two native apps that share no code but follow the same logic (day index, nearby
days, duplicate check, mosaic patterns):

- `ios/` Swift and SwiftUI, iOS 17+. Open `ios/BackThen.xcodeproj` in Xcode 16.
  - `BackThen/Core/` dates, day index, mosaic layout
  - `BackThen/Data/` PhotoKit, Google sign-in and picker, reminders, app state
  - `BackThen/Views/` Today, calendar, viewer, Favorites, Albums, onboarding
- `android/` Kotlin and Jetpack Compose, Android 8+. Open the `android/` folder in
  Android Studio.
  - `app/src/main/java/com/njarrar/backthen/core/` the same shared logic
  - `.../data/` MediaStore, Google picker, reminders, saved state
  - `.../ui/` the Compose screens
- `design/` the source design and the Nunito font license.

## Running it

- **iPhone**: open `ios/BackThen.xcodeproj`, pick your team under Signing, and run.
- **Android**: open `android/` in Android Studio and run, or
  `cd android && ./gradlew installDebug`. Unit tests: `./gradlew testDebugUnitTest`.

GitHub Actions builds both apps on every push (`.github/workflows/`).

## Turning on Google Photos import

1. In Google Cloud Console, create a project and enable the **Photos Picker API**.
2. Set up the OAuth consent screen and add the scope
   `https://www.googleapis.com/auth/photospicker.mediaitems.readonly`.
3. **iPhone**: create an **iOS** OAuth client (bundle ID `com.njarrar.backthen`)
   and paste its client ID into `ios/BackThen/GoogleConfig.swift`.
4. **Android**: create an **Android** OAuth client (package `com.njarrar.backthen`
   plus your signing key's SHA-1). No code change is needed; Google matches the app
   by package and key.

Without this the apps still run with the phone library.

## Not built yet

- **Share as a link.** The design shows `backthen.app/a/...` album links. That needs
  a server to host the photos, so for now albums share as files through the share sheet.
- **Hidden dates** and the year-by-year "strip" layout from the design's tweaks.

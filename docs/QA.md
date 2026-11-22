# Release QA

Every update passes six gates before and after it reaches people. A build moves on only
when the gate before it passes. Gates 1 to 3 are machine checks, and the store upload
jobs do not run when one fails.

The rule: **no build goes to a store unless the gates pass, and every bug that reaches
people adds a test that would have caught it.**

## Why

Version 1.24 on Android showed raw text keys such as `reminder_title_years` on every
screen while all tests were green. One line in the language loader ran fine on the
desktop Java that runs unit tests and failed on a phone. Four gaps let it through:

1. Tests ran on a desktop, not on Android.
2. Nothing read the words on the screen. The emulator check only looked for a crash.
3. Nobody looked at the store screenshots, which showed the keys too.
4. The build went straight to every user with no check on Google's real phones first.

## The gates

All gates run by machine, on emulators, simulators and Google's real phones. No one needs
a test phone. A failed gate stops the store upload job.

| Gate | When | What it checks |
| --- | --- | --- |
| 1. Quick checks | Every push | Unit tests on both apps. Versions match on both apps and the README. Store text in every language, within store limits. Android asks for no new permission. Every word the code asks for exists in `lang/en.xml`. |
| 2. Phone tests | Every pull request | Tests that run inside Android and the iPhone simulator in all six languages: language files load with the phone's own reader, reminder text, plural forms. |
| 3. Full emulator sweep | Store workflows, before upload | A "QA" build (release with code shrinking, plus the hooks that open each screen) on the oldest and newest systems, small and large phones. Every screen in six languages, dark mode and largest text in English and Arabic. No raw keys, empty labels, cut text, blank screens, crashes or freezes. Main flows, the reminder, the widget, denied access, an update over the last shipped build, awkward photos and dates. Then a launch test of the true store build. |
| 4. Google's real phones | Each Play upload | Internal test track first. Firebase Test Lab runs the phone tests on real Android phones. Google's pre-launch report crawls it. The same bundle then moves to production the same day. |
| 5. QA report | Before production | Machine checks decide pass or fail. A side-by-side of every screen against the last release is read for changes nobody meant; any such change fails the build. The report goes to the owner. |
| 6. Release and watch | Release day and two days after | 100% at once on both stores. New reviews and Play crash numbers every 2 hours on day one, then daily. |

Built so far: gate 1, gate 2, and the screen part of gate 3 on both apps (Android 8 and 16,
iOS 18 and 26; iOS 17 is supported but GitHub's Macs can't run it). The rest lands step by step, and
this page changes with each step.

## App-specific checks

The standard gates fit any app. These catch bugs that come from what Flashback does, and
came from asking a second model to read the code (the release QA skill does this for every
app, and again for each new feature). Most likely and most harmful first.

| # | Check | Bug it catches | Status |
| --- | --- | --- | --- |
| 1 | Right photos on the right day | A photo from 23:59 or 00:00, or with no date, lands on the wrong day; a year shows with no photos | Planned |
| 2 | Time zone change and midnight | After travel or past midnight, Today shows the old day or old photos | Planned |
| 3 | Hidden days stay hidden | A hidden day still shows in the widget, the reminder, the calendar or a swipe | Planned |
| 4 | This year and future days left out | Photos from earlier today count as a memory; a future day can be picked | Planned |
| 5 | 29 February and year end | Crash or wrong day on 29 February, 28 February to 1 March, 31 December to 1 January | Planned |
| 6 | Gregorian dates on Hijri and Buddhist phones | Titles, widget, reminder or reel show Hijri or Buddhist years | Planned |
| 7 | Reminder fires once, on the right day | Two reminders or none after a clock or zone change; a tap opens the wrong day | Partly (on-phone reminder test) |
| 8 | Widget with huge or broken photos | A 100 MP, empty or rotated photo leaves the widget blank or sideways | Planned |
| 9 | City list rows never move | A city added mid-list makes saved places name the wrong city | **Built** (quick checks) |
| 10 | Places card end to end | The card shows at home, during a trip, or with location off | Planned |
| 11 | Reel export everywhere | Video fails or is black on Android 8 or with Arabic stamps | Partly (English, Android 14) |
| 12 | Saved albums don't come back as memories | Album copies show up as extra photos of the day; saving twice makes doubles | Planned |
| 13 | Deleted photos and old saves | A photo deleted from the library breaks Favorites or the viewer; an old save loses favorites | Planned |
| 14 | Swipes in Arabic | Day swipe, viewer paging or drag select go the wrong way right to left | Planned |

## Stop rule (gate 6)

Act when either happens:

- a crash or freeze shows up that the last version did not have
- one review or report says a main screen is broken (Today, the viewer, Favorites,
  albums, the reminder)

Then halt the Play release, fix it, add the test that would have caught it, run every
gate again, and ask Apple for an expedited review.

## Before you ship: the short list

1. Bump the version on both apps and add "What's new" to the README.
2. Write release notes in every language for both stores.
3. Open the pull request and wait for every check to pass.
4. Merge, then run the `Play Store` and `App Store` workflows. They run gates 3 and 4.
5. Read the QA report.
6. Send to production.
7. Watch reviews and crashes for two days.

## Run the quick checks on your computer

```
python3 .github/qa/release_check.py
```

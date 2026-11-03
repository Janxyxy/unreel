<p align="center">
  <img src="logo/unreel-logo.png" width="120" alt="Unreel">
</p>

# Unreel

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)

Instagram without Reels. Android app that kicks you out of Reels when you open them.

It runs as an accessibility service, only watches Instagram and doesn't store anything except a block counter.

## What it does

- Blocks the Reels tab (sends you back to Home)
- Blocks reels opened from feed, Explore, profiles and search
- Optionally lets a reel someone sent you in DMs play, but not swiping on to the next one
- Pause for 5 minutes when you actually need it

## Build

```
./gradlew assembleDebug
```

APK ends up in `app/build/outputs/apk/debug/`. Install it, open Unreel and enable the service in accessibility settings.

On Android 13+ sideloaded apps can't get accessibility access straight away. Go to App info → ⋮ → Allow restricted settings first.

## When it stops working

Instagram renames its view IDs every now and then. They're all at the bottom of `ReelBlockerService.kt`. To see what's going on:

```
adb logcat -s Unreel
```

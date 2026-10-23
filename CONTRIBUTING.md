# Contributing to Tapeman

Thanks for wanting to help. Bug reports, ideas and pull requests are all welcome.

## Reporting a bug

Open an [issue](https://github.com/artywah/Tapeman/issues/new/choose) using the bug template. The most useful details are:
- your phone model and Android version;
- the Tapeman version (Android Settings → Apps → Tapeman);
- which source you were using (Plex Server, Spotify, Local folder, YT Music, or Apple Music);
- the posture (folded, unfolded, half-open/tabletop);
- a screenshot or screen recording.

If Tapeman crashed, a log helps a lot:

```bash
adb logcat -d -b crash > crash.txt
```

## Building

```bash
./gradlew :app:assembleDebug
```

JDK 17+ and the Android SDK (compileSdk 37) are required. Local files, Plex Server, and Spotify work fully without extra setup. MusicKit for Apple Music is optional.

## Pull requests

- Keep each change focused, and describe what you tested and on which device or emulator.
- Match the surrounding code: Kotlin and Jetpack Compose, comments that explain *why*, and no new dependencies without a reason.
- Never commit secrets or private keys. `apple-music.properties`, `signing.properties`, `*.p8`, `*.jks`, and `app/libs/*.aar` are git-ignored; keep them that way.
- UI changes: include before and after screenshots.
- The launcher icon is generated: edit `art/icon.svg` and run `python3 tools/svg_to_icon.py`.

By contributing, you agree your work is released under the project's [Apache License 2.0](LICENSE).

Put Apple's MusicKit for Android AARs here (download them from https://developer.apple.com/musickit/
with your Apple Developer account):

- `musickitauth-release-*.aar`: Apple Music sign-in, which unlocks browsing your playlists in Tapeman.
- `mediaplayback-release-*.aar`: in-app streaming. Without it, picking a playlist opens it in the
  Apple Music app and Tapeman's deck drives that app instead.

Then rebuild. Nothing else to change: Tapeman finds the SDK at runtime.

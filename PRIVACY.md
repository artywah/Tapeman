# Privacy and permissions

Tapeman has no accounts, analytics, ads, tracking, or remote telemetry servers of its own. Everything it knows stays strictly on your device or communicates directly with the music services you configure.

## Permissions

| Permission | Why Tapeman asks |
| --- | --- |
| **Notification access** (Settings → Notification access) | Android requires notification access for apps to observe other players' *MediaSessions* (e.g. YT Music, Spotify, or Apple Music) for transport control and live track metadata. Tapeman's listener only inspects media sessions; it never reads, logs, or dismisses personal notifications. |
| **Folder access** (System Storage Access Framework) | Read access to the local music directory you select. |
| **Notifications** | Displays standard playback controls and album art on the system lock screen and notification drawer. |
| **Foreground service (media playback)** | Maintains uninterrupted playback when Tapeman is backgrounded or the screen is off. |
| **Internet** | Connects to your configured Plex Server, Spotify Web API, Apple MusicKit, and fetches remote album art. |
| **Wake lock & Network state** | Prevents Wi-Fi and playback dropouts while streaming audio. |

## What's stored on your device

Tapeman retains configuration settings solely within sandboxed private app storage:
- Current active audio source.
- Local music folder URI.
- Tactile feel options (click sounds and haptic switches).
- Plex server address and `X-Plex-Token` (communicates directly with your Plex server only).
- Spotify OAuth PKCE tokens (communicates directly with Spotify's authentication servers only).
- Apple Music user and developer tokens.

Tapeman explicitly opts out of Android automatic cloud backups and device-to-device transfers to ensure authentication credentials remain isolated to your physical device. Uninstalling Tapeman permanently deletes all local configuration.

## Network Communications

- **Plex Media Server:** Queries your designated server directly for music metadata and streams audio directly through Media3.
- **Spotify:** Direct calls to `accounts.spotify.com` and `api.spotify.com` using PKCE token exchange.
- **Apple Music:** Calls to `api.music.apple.com` and streaming endpoints when MusicKit is configured.
- **Album Art:** Fetches album artwork thumbnails directly from the active music provider to render on the cassette label.

Local files are read and processed entirely on-device and never leave your phone.

## Revoking & Signing out

- **Plex:** Clear server settings in the Plex configuration sheet.
- **Spotify:** Tap *Sign Out* in the Spotify browser panel to clear OAuth tokens.
- **Apple Music:** Tap *Sign out* in the Apple Music panel.
- **Media Access:** Toggle off Tapeman's notification access in Android Settings.

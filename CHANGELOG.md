# Changelog

All notable changes to Tapeman. Versions follow [semantic versioning](https://semver.org); downloads are on the [releases page](https://github.com/artywah/Tapeman/releases).

## [0.2.2] — 2026-10-24

### Added
- **Plex Artists Tab & Discography View:**
  - Dedicated **ARTISTS** section tab in the Plex library browser with circular artist avatars.
  - A–Z index jump rail and live search specifically targeting artists.
  - Full artist discography drill-down: tapping any artist displays all of their albums sorted chronologically with cover art, release year, and direct tape playback or tracklist view.
  - Infinite scroll / pagination (`loadMore`) for seamless browsing through large artist catalogs and letter filters.

### Fixed
- **Search Text Entry & Cursor Position:**
  - Fixed an issue where Compose `BasicTextField` state was asynchronously overwritten by background ticks, causing the cursor to reset to index 0 after the first keystroke (e.g., typing `"blondie"` resulting in `"londieb"`).
  - Maintained synchronous `TextFieldValue` tracking with automatic upstream synchronization.
  - Added smooth 250ms debouncing to live search queries to avoid redundant network cancellations and UI jank while typing.
- **Plex Artist Metadata Filtering:**
  - Fixed an issue where artists containing soft-deleted or trash metadata markers in Plex (such as Blondie) were mistakenly excluded from artist listings and search queries.

## [0.2.1] — 2026-10-23

Major evolution and redesign to **Tapeman**.

### Added
- **Sony Walkman WM-RX707 Redesign:**
  - Curved hammer-tone gunmetal control pod with spun-chrome feather-touch logic controls (REW, FF, STOP, FWD/REV direction toggle).
  - Upper brushed aluminium panel featuring the OPEN cassette bay hatch, AMS skip buttons, and EX DBB toggle switch.
  - Lower brushed aluminium panel with mechanical 3-digit tape counter, 000 reset push-button, AVLS limiter switch, master dual-channel stereo volume faders, and REMOTE/PHONES socket.
  - Transparent Type IV Metal cassette shell (PX 90 / FM 60) with visible tape packs, moving reels, and dynamic album artwork label printing.
- **Plex Media Server Integration:**
  - Direct connection to personal Plex servers via base URL and `X-Plex-Token`.
  - Comprehensive library browsing across Albums, Recent additions, Popular releases, and Playlists.
  - Live search and A–Z index jump rails.
  - Direct native Media3 streaming with real-time audio analysis, continuous 12× REVIEW/CUE winding across tracks, and persistent session state.
- **Spotify Integration:**
  - In-app Spotify library browsing with OAuth 2.0 PKCE authentication.
  - Direct access to Spotify Playlists and Saved Albums in the Tape Shelf and Library band.
  - Native media-session transport control out of the box.
- **Real-Time Audio DSP:**
  - **EX DBB (Dynamic Bass Boost):** Dual-mode frequency equalization providing warm, punchy analog low-end.
  - **AVLS (Automatic Volume Limiter System):** Dynamic range limiter preventing peak fatigue while preserving punch.
- **Tabletop (Flex Mode) Refinement:**
  - Re-anchored upper deck view when half-opened on a flat surface, with responsive lower-screen library and track queue navigation.

### Changed
- App rebranded to **Tapeman**.
- Updated documentation and screenshots across all postures.

---

## [0.1.2] — 2026-10-21

### Changed
- **Settings no longer go into Android backups or device transfers.** They include your Apple Music user token, which now stays on the phone. When you move to a new phone, sign in to Apple Music again there.

## [0.1.1] — 2026-10-21

### Fixed
- **Apple Music showed the next song instead of the one playing.** MusicKit lists only the songs *after* the current one in its queue, and the title, artist, cassette label and cover now come from the song that's actually playing.
- **"Track 0 of N"** for Apple Music now shows the right position and count, including songs already played.

### Changed
- Apple Music playlists always play in order: explicitly turns shuffle off when it loads one.

## [0.1.0] — 2026-10-21

The first release.

### Added
- **Three postures.** On the cover screen, an edge-to-edge portable player. Unfolded, the same player laid down as a deck between library and now-playing panels. Half-open on a table (Flex mode), deck on top half and library on bottom.
- **The mechanism.** Latching transport keys, reels turning at tape speed ÷ radius with inertia, mechanical counter, synthesized clicks, and haptic feedback.
- **Local folders.** Media3 playback in a background service with media controls.
- **Apple Music via MusicKit** (optional AARs). Sign-in, library playlist browsing through the Apple Music API, and in-app streaming in an isolated process.
- **YT Music and Apple Music via media sessions.** Transport, artwork and queue, using notification access.

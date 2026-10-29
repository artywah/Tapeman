# Changelog

All notable changes to Tapeman. Versions follow [semantic versioning](https://semver.org); downloads are on the [releases page](https://github.com/artywah/Tapeman/releases).

## [0.3.0] — 2026-10-29

### Added
- **Android Home Screen Widgets:**
  - **4×2 Walkman Studio Deck Widget:** High-fidelity reproduction of the Sony Walkman WM-RX707 deck for Android home screens. Features live vector/bitmap cassette rendering of the loaded shell, full playback controls (Play/Pause, Rewind, Fast-Forward, AMS Skip, Stop), mechanical tape counter, EX DBB indicator, REC status LEDs, and track metadata.
  - **2×1 Compact Cassette Widget:** Compact horizontal cassette widget rendering the active tape shell with embedded album art and one-tap playback control.
  - **Real-Time RemoteViews Bitmap Renderer (`CassetteBitmapRenderer`):** Custom canvas graphics pipeline rendering real-time cassette shells, hubs, tape packs, and album art directly into Android system widgets.
- **Expanded Vintage Cassette Archive:**
  - Added an authentic 7-tape historic cassette collection across Type I, Type II, and Type IV magnetic formulations:
    - **TDK SA-90 (1986):** Iconic Type II High Bias tape in textured charcoal shell with gold silkscreen typography.
    - **TDK MA-XG 90 (1986):** Legendary Type IV Metal tape with rigid 3-piece die-cast metal and ceramic frame and dual glass composite reel rings.
    - **Maxell XLII-S 90 (1988):** High Bias 70µs tape featuring Pure Epitaxial formulation, dual-surface molded shell, and high-resonance damping mechanism.
    - **Maxell UD II 90 (1989):** High Bias tape with iconic wide oval viewing window and gold silkscreen lettering.
    - **Sony UX-Pro 90 (1986):** Audiophile Type II master tape featuring an integrated high-density ceramic tape guide block.
    - **Sony HF 90 (1985):** Definitive Type I Normal Bias vintage cassette with crystal-clear wide window and red accent racing stripes.
    - **TEAC Sound 52 (1984):** Legendary open-reel cassette equipped with dual spun-aluminum 3-window flanges, red leader clamp, and authentic reel typography.
- **Interactive Cassette Shell Inspector:**
  - Dedicated specifications and archive modal launched via long-press on the `REC · SHELL` button or by tapping the cassette in Tabletop (Flex mode).
  - Uncropped full 100:64 aspect ratio preview displaying the complete cassette shell, screws, tabs, and tape bridge with real-time spinning reel physics.
  - Archival notes, magnetic formulation specs, bias EQ ratings, and one-tap **"Load This Cassette Into Deck"** action.

### Fixed
- **Cassette Typography & Spindle Hole Clearance:**
  - Adjusted label typography, logos, and album art placement across all 7 cassette shells to strictly respect spindle drive holes ($HUB_L = 28.75\text{ mm}$, $HUB_R = 71.25\text{ mm}$, $r = 5.8\text{ mm}$) and molded washer collars.
  - Relocated Sony UX-Pro track title to the dark top header strip, Maxell XLII-S and UD II logos outside the 8.2mm collars, and TDK MA-XG badges to the bottom center bay and outer corners.
- **Album Cover Art & Label Orientation:**
  - Fixed an issue where the square album art image and label text appeared upside down on inverted cassette shells in the Walkman chamber.
  - Automatically applies a 180° rotation transform to album art, Side A/B badges, and track metadata when loaded in the Walkman deck so all elements face right-side up.
- **TEAC Sound 52 Reel Typography Alignment:**
  - Unified the radial coordinate system between the 3 cutout slots and the 3 solid aluminum sectors.
  - Silkscreen typography (`TEAC`, `SOUND 52`, `Bias/NORMAL EQ/120µs`) is now centered symmetrically at $0^\circ$, $120^\circ$, and $240^\circ$ inside the broad silver sectors, maintaining generous margins from the adjacent cutout windows.

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

# Changelog

All notable changes to Tapeman. Versions follow [semantic versioning](https://semver.org); downloads are on the [releases page](https://github.com/artywah/Tapeman/releases).

## [0.4.0] — 2026-11-06

### Added
- **Personalized "Home" Discovery Experience (Mobile & Android Auto):**
  - **Dynamic Plex Server Hubs Integration (`/hubs/sections/`):** Connects to Plex Media Server's recommendation engine to build automated, personalized shelves (*"More by [Recent Artist]"*, *"More in [Recent Genre]"*, *"Most Played in [Month]"*, and *"More from [Record Label]"*).
  - **"Fans Also Like / Similar Artists" Engine (`/library/metadata/{id}/similar`):** Dynamically discovers similar artists from your Plex library based on recent listening history, each complete with photo avatars and 1-tap **"📻 Radio"** launch buttons.
  - **Mobile "Home" Tab & Crate Digger Feed:**
    - Permanent **`HOME`** pill pinned as the first tab in the Plex Library panel on foldables and tablets.
    - **The DJ Booth (Hero Deck):** Live interactive **Sonic DJ** toggle card (cyan audio waveforms, on/off state) and quick access to **Loved Tracks**.
    - **"Jump Back In" Shelf:** Horizontal cassette tape rack of recently played albums (`lastViewedAt:desc`) with instant 1-tap load into the Walkman deck.
    - **Acoustic Similar Artists Shelf:** Circular artist avatars with instant radio launch.
    - **Plex Hub Shelves:** Horizontal scrolling album shelves.
  - **Android Auto "Home" Tab & 4-Across Layout:**
    - Added **`Home`** as the premier tab in the in-car navigation bar (`Home`, `Playlists`, `Albums`, `More`).
    - Organizes content into glanceable shelves using Content Style headers (`CONTENT_STYLE_GROUP_TITLE_HINT`), suppressing the A–Z scrubber to automatically expand into a **4-cards-across grid**:
      - **"DJ & Favorites":** `🎧 Sonic DJ: ON/OFF` toggle with live state, plus `★ Loved Tracks`.
      - **"Recommended Stations":** 1-tap `📻 [Recent Artist] Radio` and `📻 [Similar Artist] Radio`.
      - **"Jump Back In":** 4 most recently played albums.
      - **Dynamic Hubs:** Personalized shelves directly from Plex (*"More in Electronic"*, *"Most Played"*, etc.).
    - Added playlist & album fallback vector artwork (`bg_tile_favorites`) to eliminate Android Auto warning triangle icons on items lacking cover art.
- **Android Auto Smart Sub-Ranges for Massive Libraries:**
  - **Automatic Sub-Range Bucketing:** Automatically partitions letters with large catalogs (> 40 items) into balanced ~35-item sub-ranges (e.g. `E-40 → Edith Frost`, `Ellen Allien → Elton John`). Eliminates Android Auto's ~50-item list cutoff and allows complete, uninterrupted browsing across massive libraries (7,600+ artists, 38,000+ albums).
  - **IPC Binder Payload Optimization:** Replaced multi-page Wikipedia/biography text in car subtitles with clean `"Artist"` tags, shrinking the Binder transaction payload from 930 KB to 25 KB (97% reduction) and preventing silent IPC parcel truncation.
  - **Letters with $\le 40$ Artists:** Display all artists directly in a clean list without extra sub-levels.
  - **Full Catalog Continuous List:** Provided `★ All '[Letter]' Artists` continuous browse option at the bottom of each letter.
  - **Smart Sub-Ranges for Albums:** Applied the same 35-item sub-range bucketing to the Albums tab for letters containing thousands of albums.
- **In-Car Voice & Text Search (`onSearch`):**
  - Implemented `MediaLibrarySession.Callback.onSearch` and `onGetSearchResult` for Android Auto. Drivers can now search for any artist or album using Google Assistant voice commands or the in-car search keyboard.
- **Android Auto In-Car Media Support:**
  - Upgraded background audio engine to AndroidX `MediaLibraryService` with standard automotive descriptor and intent filters.
  * **Automotive Browse Hierarchy:**
    * **Playlists & Mixes** *(Full-Bleed Square Grid)*: Displays a visual card grid with **"🎧 Sonic DJ: ON/OFF"** as the permanent first tile, followed by **"★ Loved Tracks"** and all user playlists.
    * **Albums** *(Full-Bleed Square Grid)*: Discography view with square artwork cards and alphabetical sub-ranges.
    * **Recent Additions** *(Full-Bleed Square Grid)*: Latest music added to your library rendered as square cover cards.
    * **Artists** *(A–Z List)*: Clean vertical A–Z artist roster, smart sub-ranges, and discographies.
    * **Local Audio** *(List)*: Direct browsing of the device's local audio folder.
  * **Custom In-Car Transport Actions & First-Tile Controls:**
    * **Sonic DJ Tile (`🎧`):** Always the first tile on the Playlists screen—tap directly from the car console to engage continuous DJ mode without leaving the playlist view.
    * **Sonic DJ Transport Action (`🎧`):** Transport button on the Now Playing screen to toggle automated DJ Freeze mode.
    * **Sonic Radio (`📻`):** Retro transistor radio action button on the Now Playing screen to instantly spin up a station based on the current track.
    * **Favorite (`★`):** Toggles 5-star / "Loved" status in Plex for the currently playing track.
  * **Sideloading Compatibility:** Fully compatible with [UnknownAuto](https://github.com/GitMag/UnknownAuto) (Shizuku-driven package origin attribution) and Android Auto's "Unknown sources" developer mode.
- **Plex Favorites & Loved Tracks:**
  - Dedicated **FAVORITES** section tab in the Plex library browser displaying a pinned `★ Loved Tracks` tape and all loved albums.
  - Interactive **`★` / `☆`** favorite toggle on individual track rows and on the unfolded Now Playing liner-notes card with instant bidirectional sync to Plex Media Server (`/:/rate`).
- **Dynamic Radio Stations:**
  - **Artist Radio (`Artist Radio 📻`):** Dedicated button on every Artist discography page.
  - **Track Radio (`📻`):** Dedicated radio button on the unfolded Now Playing bar to start a continuous station from the active song.
  - **Multi-tier Station Resolution:** Queries Plex official Station endpoints, falls back to Plex Super Sonic acoustic neural-network nearest-neighbor vectors (`/nearest`), and gracefully falls back to shuffled artist tracks if sonic analysis is unavailable.
- **Sonic DJ (DJ Freeze Mode):**
  - Algorithmic playback monitor in `PlaybackService` that continuously monitors queue length and injects sonically matching tracks from your Plex library before playback ends.

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

# Lume for Android & Android TV

> **Next-generation native IPTV client for Google TV, NVIDIA Shield TV, Fire TV, tablets, and phones.**  
> Crafted with Jetpack Compose for TV, Material 3, Media3 ExoPlayer, and Room SQLite. Built for complete feature and design parity with Apple TV Lume.

---

## Overview

Lume is a sleek, modern, and high-performance IPTV streaming client engineered specifically for 10-foot television experiences as well as mobile handheld devices. 

It connects natively with **Xtream Codes API** and **M3U/M3U8 playlists**, offering zero-latency channel navigation, an EPG guide, high-definition VOD movies and series catalogs, a real-time live sports hub with auto-matched IPTV streams, multi-view streaming (up to 4 feeds simultaneously), and local network device management.

---

## Key Features

### 1. 10-Foot Leanback Experience (Android TV / Google TV / Fire TV)
- **Cinematic Top Navigation Bar**: Glassmorphic, floating header with search, tabs (Home, Movies, Series, Live TV, Sports, Settings), and smooth DPAD spatial focus transfer.
- **Dynamic Hero Banner**: Auto-scrolling carousel showcasing trending movies and series with dynamic ambient backdrop illumination.
- **Fluid Horizontal Rails**: Infinite scrollable rails for "Continue Watching", "Favorites", "Trending Movies", "Popular Series", and "On Air Now".
- **Bidirectional Traversal**: Unbroken DPAD navigation ensuring smooth up/down/left/right traversal between rails, hero action pills, and the top navigation bar.

### 2. Live TV & EPG Guide
- **Multi-Category Sidebar**: Quick filtering across live channels (Sports, News, Entertainment, International, PPV, etc.).
- **Live Channel Navigator**: High-performance, zero-latency circular channel switching with remote DPAD Up/Down controls.
- **Now & Next Guide**: Live EPG progress indicators, program descriptions, and start/end time slots.
- **Apple TV Overlay Parity**: Full-screen playback renders as an in-place modal overlay (`activePlaybackMedia`), preserving underlying tab selection, category, and scroll position when dismissed.

### 3. VOD Movies & Series Catalog
- **Category Drawers**: Filter VOD content by genre, language, and release year.
- **Detail Sheets**: Modal detail panes featuring backdrop art, plot summaries, ratings, cast, and episode season pickers.
- **Resume Playback**: Automatic watch-progress tracking with "Continue Watching" integration.

### 4. Sports Hub
- **Live Scores & Fixtures**: Real-time scores and upcoming schedules powered by ESPN APIs (Premier League, Champions League, La Liga, NBA, NFL, MLB).
- **Auto-Matched IPTV Streams**: Intelligent fuzzy matching connecting scheduled sporting events to available live channels in your IPTV lineup.

### 5. Multi-View Streaming
- **Concurrent Grid Layout**: Watch up to 4 live streams concurrently in 2x2 grid or 1+3 picture-and-picture layouts.
- **Independent Focus & Audio**: Switch active audio stream instantly using the directional remote.

### 6. Settings & Sync Suite
- **Playlist Management**: Manage Xtream Codes credentials, M3U URLs, sync intervals, and multi-playlist switching.
- **Player Options**: Configure ExoPlayer buffer profiles, hardware decoders, audio passthrough, and subtitle rendering.
- **Local Network Pairing**: Built-in HTTP server and QR code pairing for rapid setup from any smartphone or browser on the same Wi-Fi network.

---

## Architecture & Tech Stack

| Layer | Technology |
|---|---|
| **UI Framework** | Jetpack Compose for TV (`androidx.tv.material3`) & Compose Mobile |
| **Language** | Kotlin 2.0+ (100% Kotlin Coroutines & Flow) |
| **Media Player** | AndroidX Media3 ExoPlayer 1.5.0 (HLS, TS, MP4, Adaptive Bitrate) |
| **Database** | Room SQLite 2.6.1 with reactive Flow queries |
| **Networking** | OkHttp 4.12, Kotlinx Serialization, XML Pull Parser |
| **Image Loading** | Coil 3.0 (Compose TV async image caching) |
| **Design System** | Custom ambient gradient backdrop & Apple TV glassmorphism |

---

## Directory Structure

```
app/src/main/
├── AndroidManifest.xml
├── java/org/getlume/android/
│   ├── LumeApp.kt                         # Application entry point & service initialization
│   ├── MainActivity.kt                    # Root activity orchestrating TV vs Mobile layouts
│   ├── core/
│   │   ├── data/                          # Room database, entities, and DAOs
│   │   ├── designsystem/                  # Compose TV theme, cards, and ambient backgrounds
│   │   ├── network/                       # Xtream Codes, M3U, XMLTV, ESPN, LAN setup server
│   │   ├── player/                        # ExoPlayer manager, multi-view, channel navigation
│   │   ├── settings/                      # Preferences and sync configuration repository
│   │   ├── sports/                        # Sports scores and stream auto-matching repository
│   │   └── sync/                          # Automated background playlist/EPG sync manager
│   └── ui/
│       ├── mobile/                        # Touch-optimized mobile & tablet screens
│       ├── navigation/                    # Navigation routes and argument types
│       └── tv/                            # Android TV screens (Home, Live TV, Movies, Series, Sports, Settings, Player, MultiView)
└── res/                                   # Drawables, banners, color tokens, and vector assets
```

---

## Building & Installation

### Requirements
- Android SDK 35 (compileSdk 35, minSdk 26)
- JDK 17 or JDK 21
- Gradle 8.11+

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Install directly to Android TV / Shield TV via ADB
```bash
adb connect <DEVICE_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.getlume.android.debug/org.getlume.android.MainActivity
```

---

## License

All rights reserved. Proprietary and confidential.

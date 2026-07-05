# Msuci - Client-Side Music Streaming Application

Msuci is a production-grade, power-user music streaming and management application built entirely in Kotlin for the Android platform. It provides the seamless convenience of massive online streaming catalogs paired with the high-fidelity audio handling, localized metadata persistence, and precision time-synced lyrics engines typically found only in premium local audio players.

Designed with a fully client-side architecture, the application eliminates server overhead by executing all metadata enrichment, asset sourcing, search optimization, and background processing pipelines directly on the user's device.

---

## 🚀 Key Features

*   **"Insta-Playlist" (Text-to-Library Automation):** Features a robust text-to-library porting mechanism. Users can paste raw text playlists (one track per line), which are automatically processed by a custom background worker (`InstaPlaylistWorker`). The engine executes automated multi-source queries, scores metadata matches, and includes an automated "Retry Pass" system for fault-tolerant cross-platform library migration.
*   **High-Precision Synced Lyrics Engine:** Implements a multi-source lyrics strategy that prioritizes LRCLIB for `.lrc` time-synced formats and seamlessly falls back to Genius for high-quality static text. Features a custom duration-matching scoring algorithm to reconcile video vs. studio track lengths, applies a 200ms UI-lag compensation for perfect timestamp alignment, and integrates specialized cleaning logic to handle non-Latin scripts (Tamil, Hindi, etc.).
*   **Background Data & Download Architecture:** Utilizes Android Jetpack `WorkManager` to orchestrate a reliable background download pipeline. Tracks within large playlists are chained sequentially to maximize download stability and prevent network thrashing. The architecture is fully compliant with Android 14 foreground service restrictions, leveraging `DATA_SYNC` types for seamless automated background operations.
*   **Multi-Source Metadata Enrichment:** Dynamically queries iTunes, Last.fm, and Genius APIs concurrently via a race-condition fetching system to retrieve high-resolution (1200x1200px) cover art and artist profile media. Enriched metadata is instantly cached locally to optimize performance.
*   **Modern Interactive UI & Android Integration:** Built around a dynamic, context-aware interface that adapts background accent blending to matching album artwork. Supports home screen widgets with real-time queue previews and granular interactive touch gestures for playback navigation.

---

## 🛠️ Technical Stack & Architecture

The codebase follows modern Android development best practices, emphasizing clean separation of concerns and structured layer isolation:

*   **Language:** Kotlin
*   **Asynchronous & Background Processing:** Android Jetpack `WorkManager` (sequential queue chaining), Kotlin Coroutines
*   **Data Persistence:** Room Database (local metadata caching and state persistence)
*   **Core Audio Engine:** Android Jetpack `Media3` / ExoPlayer customization
*   **Networking:** REST API Integration (iTunes, Last.fm, Genius, LRCLIB)

### Directory Structure Blueprint
The project is strictly organized into clean architectural layers to support modular scaling and maintainability:
*   `data/` - Handles data routing, network API calls, and local Room database transactions.
*   `di/` - Dependency injection configuration managing component lifecycles.
*   `domain.usecase/` - Houses the core isolated business logic and functional rules of the application.
*   `playback/` / `service/` - Custom audio routing extensions managing active media playback states.
*   `ui/` / `widget/` - Composes the interactive design components, stateful screens, and home widgets.
*   `worker/` - Contains background task logic and execution profiles (including `InstaPlaylistWorker`).

---

## 📦 Local Setup & Compilation

### Prerequisites
*   Android Studio (Ladybug or newer recommended)
*   Android SDK 34+ (Android 14 Ready)
*   Gradle 8.x+

### Setup Pipeline
1. Clone the repository to your environment:
   git clone [https://github.com/BroknSkul/msuci.git](https://github.com/BroknSkul/msuci.git)

2.Open the project folder inside Android Studio.

3.Configure your API endpoint keys inside your local workspace configuration (e.g., local.properties or environment paths):

GENIUS_ACCESS_TOKEN=your_genius_token_here
   LAST_FM_API_KEY=your_lastfm_key_here
   YOUTUBE_API_KEY=your_youtube_key_here

Sync the project with Gradle Files.

    Compile and deploy the application to an Android Virtual Device (AVD) or a physical deployment target.

📝 Disclaimer & Project Scope

This application was engineered as an architectural exploration into high-performance client-side mobile systems, data pipeline automation, and multi-source API orchestration. Development workflows were accelerated using modern AI pair-programming utilities to systematically translate feature blueprints, dependency models, and operational parameters into a production-ready codebase.

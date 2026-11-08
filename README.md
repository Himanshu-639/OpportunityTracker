# Opportunity Tracker

Opportunity Tracker is an Android utility designed to extract, organize, and track opportunity deadlines from text snippets, web links, and poster images.

### Key Features
- **AI Deadline Extraction**: Uses Groq API to parse titles, organizations, deadlines, and application links from plain text or URLs.
- **Poster OCR**: On-device text recognition with ML Kit extracts details directly from images and screenshots via the camera button.
- **Google Calendar Sync**: Native bidirectional integration via CalendarContract pushes tasks and automatically imports scheduled events.
- **Unmissable Alarms**: Exact alarms via AlarmManager trigger full-screen notifications and audio alerts before deadlines.
- **Compose Dashboard**: Material 3 interface with status filters (All, Due Soon, Upcoming, Done), theme palettes, and custom alarm timings.

### Project Structure
- `app/`: Android application module containing Kotlin sources, Room database, alarm services, and Jetpack Compose UI.
  - `app/src/`: Application source code, resources, and manifest.
- `gradle/`: Gradle wrapper binaries and version catalog (`libs.versions.toml`).

### Ignored Directories
The following environment-specific and build folders are excluded via `.gitignore`:
- `.gradle/`: Local Gradle build cache.
- `.idea/`: Android Studio project configurations.
- `.kotlin/`: Kotlin incremental compilation cache.
- `build/` and `app/build/`: Compilation output and generated APKs.
- `local.properties`: Machine-specific Android SDK location.

### Setup
1. Open the project in Android Studio.
2. Build and install on an Android device running API 26 or higher.
3. Add your free Groq API key in Settings to enable extraction.

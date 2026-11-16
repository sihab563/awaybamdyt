# NEXUS — Gaming Community Platform (Android)

NEXUS is a Discord-inspired, dark neon gaming community Android app built with **Kotlin**, **Jetpack Compose (Material 3)**, **Firebase Authentication (Google Sign-In via Jetpack Credential Manager)**, and **Cloud Firestore** with real-time listeners and zero-trust security rules.

> **Platform Note:** This is a native Android application (built with Gradle & Kotlin), not a browser-based React/Next.js web app. Its dependency catalog lives in `gradle/libs.versions.toml` and `app/build.gradle.kts` (while `package.json` at the root supports running the local Firebase Firestore security rules test suite).

---

## Features

- **User System**: Google Sign-In (`CredentialManager`), custom GamerTag & Avatar onboarding, profile photo picker (`PickVisualMedia`), badges, and real-time `Online`, `In-Game`, `Idle`, and `Offline` status synced across servers.
- **Server System**: Create, edit, and delete servers; Public & Private server visibility; shareable invite codes (`NX-XXXXXX`); Owner, Admin, Moderator, and Member role hierarchy; join via invite code or discovery; leave servers cleanly.
- **Channel System**: Categorized Text (`#`), Voice (`🔊`), and Announcement (`📢`) channels with CRUD management and `adminOnlyPost` permission gating.
- **Real-Time Chat & Media**: Instant Firestore messaging, inline image attachments (`PickVisualMedia`), document attachments (`OpenDocument`) with local export (`CreateDocument`), emoji reactions, message reply quotes, edit/delete actions, and live online members drawer.
- **Real-Time Voice Rooms**: Join/leave voice channels, live mic Mute & Deafen controls, hardware `AudioRecord` RMS voice-activity detection, and Push-to-Talk WAV voice clips (`AudioTrack`).
- **Friends, DMs & Blocking**: Send/accept/decline/cancel friend requests, 1-on-1 real-time Direct Messages, and rule-enforced user blocking.
- **Server Moderation**: Role assignment (`ADMIN` / `MOD` / `MEMBER`), kick, ban/unban, and moderator message deletion.
- **Discovery, Notifications & Themes**: Public server browser & search, real-time notifications feed, custom dark/light theme switcher, and adaptive layouts for phones, tablets, and foldables.

---

## Project Structure

```text
├── app/
│   ├── build.gradle.kts                      # Android module build config & dependencies
│   ├── google-services.json                  # Public Firebase client identifiers (no admin secrets)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml           # Permissions (RECORD_AUDIO, INTERNET) & launcher config
│       │   ├── res/                          # Adaptive icons, strings, Firebase config, fonts/drawables
│       │   └── java/com/example/
│       │       ├── MainActivity.kt           # Edge-to-edge Compose host & auth initialization
│       │       ├── auth/AuthManager.kt       # Jetpack CredentialManager Google Sign-In
│       │       ├── data/
│       │       │   ├── model/NexusModels.kt  # Firestore DTOs, enums, & serialization helpers
│       │       │   ├── preferences/          # DataStore theme & sound preferences
│       │       │   ├── repository/           # NexusServerRepository & NexusSocialRepository
│       │       │   └── util/                 # Attachment encoding & formatting helpers
│       │       └── ui/
│       │           ├── audio/                # Hardware VoiceEngine (AudioRecord/AudioTrack) & SFX
│       │           ├── components/           # Shared Discord-inspired M3 UI components
│       │           ├── screens/              # Auth, ServerRail, Channels, DMs, Discovery, Profile
│       │           ├── theme/                # Black/purple/blue dark & light Material 3 theme
│       │           └── viewmodel/            # NexusViewModel & unidirectional UI state
│       └── test/java/com/example/            # Robolectric unit & Firestore emulator rule tests
├── gradle/libs.versions.toml                 # Centralized Kotlin & Jetpack dependency catalog
├── firestore.rules                           # Zero-trust Cloud Firestore security rules
├── firestore.test.js                         # Tier 1 Firebase emulator security rule tests
├── firebase-blueprint.json                   # Data model schema blueprint
├── firebase.json                             # Named Firestore DB & local emulator configuration
├── package.json                              # Node script runner for Firestore rules tests
└── .env.example                              # Template for optional secrets (never commit .env)
```

---

## Local Setup & Running

### Prerequisites
- **Android Studio** (Ladybug or newer) with Android SDK 36 & JDK 17/21
- **Firebase CLI** (`npm install -g firebase-tools`) & **Node.js 20+** (for local emulator tests)

### 1. Configure Firebase & Credentials
1. Create or connect a Firebase project in the [Firebase Console](https://console.firebase.google.com/).
2. Enable **Authentication → Google Sign-In** and **Cloud Firestore**.
3. Replace `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml` with your own Firebase project's client config if deploying to a separate Firebase project.
4. Copy `.env.example` to `.env` locally (never commit `.env` or keystores).
5. **Security Note**: No private admin keys, passwords, or release keystores are stored in source control. Release signing credentials (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) are read from environment variables at build time.

### 2. Run on Android Device or Emulator
Open the root folder in Android Studio, wait for Gradle sync, and press **Run** (`app`), or build from the command line:
```bash
gradle :app:assembleDebug
```

### 3. Run Local Security Rules & Unit Tests
Start the local Firebase emulators and execute both the JavaScript rule tests and Kotlin Robolectric suite:
```bash
# Terminal 1: Start local Firestore & Auth emulators
firebase emulators:start --only firestore,auth --project demo-no-project

# Terminal 2: Run Tier 1 JS rules tests & Tier 2 Android Robolectric emulator tests
npm install
npm run test:rules
gradle :app:testDebugUnitTest
```

### 4. Deploy Firestore Security Rules
```bash
firebase deploy --only firestore:rules
```

---

## Distribution & Deployment Notes

Because NEXUS is a **native Android Kotlin/Compose application** (producing `.apk` / `.aab` binaries rather than static HTML/JS bundles), platforms like **Netlify** or **Vercel** cannot directly execute Android APKs—those platforms only host web applications or serverless Node APIs.

To publish or share NEXUS for free:
1. **GitHub Releases**: Build a release or debug APK (`gradle :app:assembleRelease` / `:app:assembleDebug`) and attach the `.apk` to a GitHub Release for direct Android installation.
2. **Firebase App Distribution**: Upload test APKs/AABs for free tester distribution alongside your hosted `firestore.rules`.
3. **Google Play Store**: Generate a signed App Bundle (`gradle :app:bundleRelease`) with your private upload keystore (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) and publish on Google Play Console.

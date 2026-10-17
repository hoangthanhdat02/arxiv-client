# 📱 arXiv Android Client (Native Java) - Topic 28

[![Platform](https://img.shields.io/badge/Platform-Android%20Native-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Language-Java%2017-ED8B00.svg?logo=openjdk&logoColor=white)](https://www.java.com/)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-blue.svg)]()
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-lightgrey.svg)]()
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2F%20Clean%20UI-orange.svg)]()

> **Native Android Mobile Application for arXiv Academic Paper Discovery & Management**  
> **Course:** Mobile Application Development  
> **Evaluation Mode:** 100% Live Demonstration executed directly from source code on Android Virtual Device (AVD).

---

## 📖 1. Project Overview

**arXiv Android Client** is a high-performance native Android application written in Java. Engineered specifically for academic researchers, graduate students, and engineers, the application provides an intuitive mobile interface to track, explore, search, and read open-access research papers from [arXiv.org](https://arxiv.org/) across core computer science disciplines:
* **`cs.AI`** (Artificial Intelligence)
* **`cs.LG`** (Machine Learning)

The mobile application is paired with a dedicated ExpressJS Gateway Service via the **`10.0.2.2`** emulator loopback interface, establishing a robust **Offline-First** architecture capable of seamless offline reading even with active **Airplane Mode**.

---

## 👥 2. Team Member Responsibilities & Code Architecture

100% of team members directly author core codebase modules according to project specification:

```
app/src/main/java/com/arxiv/client/
├── MainActivity.java                    [TV1: Đạt_con (Leader)]
├── data/
│   ├── api/
│   │   ├── ApiClient.java               [TV1: Đạt_con]
│   │   └── ApiService.java              [TV1: Đạt_con]
│   ├── db/
│   │   └── ArxivDbHelper.java           [TV7: Hiếu Bigh]
│   ├── download/
│   │   └── DownloadService.java         [TV6: Member 6]
│   └── model/
│       ├── PaperItem.java               [TV1: Base Model]
│       ├── PaperResponse.java           [TV1: Base Model]
│       └── BibtexResponse.java          [TV7: Citation Model]
└── ui/
    ├── adapters/
    │   └── PaperAdapter.java            [TV3: Member 3]
    ├── base/
    │   └── DebounceTextWatcher.java     [TV4: Đức]
    ├── dialogs/
    │   └── FilterBottomSheetDialog.java [TV4: Đức]
    ├── home/
    │   └── HomeFragment.java            [TV3: Member 3]
    ├── library/
    │   └── LibraryFragment.java         [TV7: Hiếu Bigh]
    ├── reader/
    │   └── PdfReaderActivity.java       [TV5: Ngo]
    └── settings/
        └── SettingsFragment.java        [TV6: Member 6]
```

### Module Breakdown:
* **🧑‍💻 TV1: Team Leader & Base Architecture (Đạt_con)**
  * Android Java project configuration, Gradle build scripts, ViewBinding, and Git structure.
  * `MainActivity` with 4-tab `BottomNavigationView` container (*Explore, Search, Library, Settings*).
  * System-wide Dynamic Theme engine (Light/Dark mode via `AppCompatDelegate`).
  * Centralized Retrofit 2 + Gson network client (`ApiClient`).
* **🧑‍💻 TV3: Home Feed & Custom RecyclerView (Member 3)**
  * Custom `item_paper_card.xml` CardView layout with category badges and action buttons.
  * High-performance `PaperAdapter` with view caching and click-to-read triggers.
  * `HomeFragment` feed supporting category switching (`cs.AI` / `cs.LG`), `SwipeRefreshLayout` pull-to-refresh, Facebook Shimmer skeleton loading, and infinite scrolling pagination.
* **🧑‍💻 TV4: Search & Multi-Criteria Filtering (Đức)**
  * `SearchFragment` with asynchronous search query execution.
  * `DebounceTextWatcher` with 500ms debounce interval to eliminate keystroke server flooding.
  * `FilterBottomSheetDialog` for category filtering and sorting by date or relevance.
  * Soft keyboard optimization (`adjustResize`) to maintain layout stability on virtual devices.
* **🧑‍💻 TV5: In-App Native PDF Reader (Ngo)**
  * Dedicated `PdfReaderActivity` rendering full scientific papers directly within the app.
  * Multi-touch zoom controls (Pinch-to-zoom) and smooth page scrolling.
  * Navigation bar with real-time page indicator (`Page X/Y`) and quick-jump `SeekBar`.
  * Last-read page persistence via SQLite/SharedPreferences for automatic reading resume.
* **🧑‍💻 TV6: Download Manager & Cache Settings (Member 6)**
  * Asynchronous background PDF downloader (`DownloadService`) streaming files to private app storage.
  * Real-time percentage download progress indicator and automatic offline state switching.
  * `SettingsFragment` displaying active disk cache usage and one-tap cache purge.
* **🧑‍💻 TV7: SQLite Database & Academic Utilities (Hiếu Bigh)**
  * `ArxivDbHelper` managing `bookmarks` and `read_history` tables in `arxiv_client.db`.
  * `LibraryFragment` with complete **Offline-First** capability during **Airplane Mode**.
  * Single-tap BibTeX citation export to system `ClipboardManager` and native `ShareCompat` Android intents.

---

## 🛠️ 3. Technology Stack & Key Libraries

| Component | Library / Framework | Purpose |
| :--- | :--- | :--- |
| **Language** | Java 17 | Core object-oriented mobile logic. |
| **Networking** | Retrofit 2.9.0 + OkHttp 4.12.0 | Asynchronous RESTful communication with Express Gateway. |
| **JSON Parser** | Google Gson 2.10.1 | Serialization and deserialization of POJO data models. |
| **Design System** | Google Material Design 3 | Cards, BottomNavigationView, Chips, Dialogs, Switches. |
| **Animation** | Facebook Shimmer 0.5.0 | Skeleton placeholder animation during asynchronous loading. |
| **Pull-to-Refresh**| AndroidX SwipeRefreshLayout 1.1.0 | Gesture-driven data reloading. |
| **Local Database**| Android Native SQLite (`SQLiteOpenHelper`)| Zero-dependency local persistence for offline papers. |

---

## 📱 4. Android Studio Setup & Emulator Instructions

### Step 1: Start Backend Gateway Service
Before launching the Android application, start the backend server:
```bash
cd arxiv-backend
npm start
```
*(Confirms running at `http://localhost:3001` and accessible to emulator via `http://10.0.2.2:3001`).*

### Step 2: Open Project in Android Studio
1. Launch **Android Studio** (Hedgehog / Iguana / Jellyfish or newer).
2. Click **Open** and select the project directory: `arxiv-client`.
3. Allow Gradle to sync dependencies automatically (**Gradle Sync**).

### Step 3: Run on Android Virtual Device (AVD)
1. In the device selection dropdown, select an emulator:
   * **Recommended Device:** Google Pixel 6 or Pixel 7.
   * **System Image:** Android 12 or 13 (API 31 - 33), `x86_64` architecture, Hardware Acceleration enabled.
2. Click the green **Run (▶)** button or press `Shift + F10`.
3. The application will compile, install, and launch immediately on the emulator.

---

## 🎬 5. Six-Step Defense Live Demonstration Script

During final evaluation before the course instructors, follow this structured 6-minute live workflow:

1. **Step 1: Application Launch & Explore Feed (TV1 & TV3 - 1 min)**
   * Launch the app from the AVD home screen.
   * Observe the Shimmer skeleton loading effect, followed by the latest `cs.AI` papers loaded dynamically from the backend.
   * Drag down on the list to demonstrate gesture pull-to-refresh (`SwipeRefreshLayout`).
   * Navigate to Settings to switch to **Dark Mode**; observe seamless theme transition.
2. **Step 2: Debounced Search & Filtering (TV4 - 1 min)**
   * Navigate to the **Search** tab. Type `"transformer"` from the host PC keyboard; note the 500ms debounce prevent server flooding.
   * Open the **Filter Bottom Sheet Dialog**, select category `cs.LG`, and sort by date.
   * Demonstrate responsive layout behavior when the virtual keyboard appears (`adjustResize`).
3. **Step 3: Native In-App PDF Reading (TV5 - 1 min)**
   * Select a paper card and tap **Read PDF**.
   * Demonstrate pinch-to-zoom on complex mathematical equations and smooth page scrolling.
   * Drag the bottom `SeekBar` slider to jump directly across pages; observe the page counter (`Page X/Y`).
4. **Step 4: File Download & Storage Management (TV6 - 30 sec)**
   * Tap the **Download** button; observe the real-time download percentage progress.
   * Navigate to **Settings**; demonstrate active disk storage calculation and verify the cache clearing action.
5. **Step 5: Airplane Mode Offline Verification (TV7 - Core Technical Highlight)**
   * Pull down the emulator's system notification tray and **Enable Airplane Mode** (severing all network access).
   * Navigate to the **Library** tab; demonstrate that all bookmarked papers and downloaded PDFs open seamlessly from the local SQLite database (`arxiv_client.db`).
6. **Step 6: Academic Utilities (TV2 & TV7 - 30 sec)**
   * Tap **BibTeX** on any paper card to trigger clipboard copy.
   * Open the AVD Notes application and paste the standard academic `@misc` block.

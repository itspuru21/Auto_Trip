# 📍 AutoTrip — Your Trip Story, Auto-Written

An automated, smartphone-based travel behaviour data collection system developed for transportation research in collaboration with **NATPAC** (*National Transportation Planning and Research Centre*). 

AutoTrip replaces expensive, error-prone manual household travel diaries with passive, high-precision GPS tracking and a streamlined **Sense-Confirm-Sync** pipeline.

----------------------------------------------------------------------------------------------------------------------------------------------

## 📌 Features & Highlights

- **Passive GPS Foreground Tracking:** Captures real-time trip paths, origin/destination coordinates, timestamps, and route breadcrumbs using Android's `FusedLocationProviderClient` with a persistent foreground service.
- **Accurate Distance & Metric Computation:** Calculates incremental and total journey distances using the **Haversine formula**.
- **Sense-Confirm-Sync Workflow:** Automatically logs journeys and prompts users through a lightweight confirmation interface to fill in key details (*travel mode, purpose, companion count, and travel cost*).
- **Tailored for Regional / Indian Transit Modes:** Full support for local transportation options, including **Auto-Rickshaw**, **Two-Wheeler**, **Bus**, **Car**, **Walk**, and **Bicycle**.
- **NATPAC Profile & Demographics Module:** 3-tab profile capturing demographic data, household vehicle ownership, primary commute habits, and residence types.
- **Granular Privacy & Permission Controls:** In-app toggles for anonymous data sharing, background detection, and location permissions.
- **Real-Time Data Sync:** Instant UI state synchronisation and cloud storage powered by **Firebase Firestore Snapshot Listeners**.
- **Built-in Developer GPS Simulator:** Road-following GPS trip simulation running at 10x real-world speed via the **OSRM (Open Source Routing Machine) API** to test without physical movement.
- **Research & Admin Dashboard:** Web portal displaying trip maps, aggregated analytics, modal shares, and data export features for planners.

----------------------------------------------------------------------------------------------------------------------------------------------

## 🛠️ Architecture & Tech Stack

### **Android Application (Client)**
- **Language:** Kotlin `2.0.21`
- **UI Toolkit:** Jetpack Compose with Material Design 3
- **Architecture:** MVVM (Model-View-ViewModel) + Repository Pattern + Kotlin Coroutines & StateFlow
- **Location API:** Google Play Services `FusedLocationProviderClient` (3s interval, 5m displacement threshold)
- **Map & Routing:** OSMDroid `6.1.20` & OSRM API
- **Target Platform:** Android (Min SDK: `API 31 / Android 12`, Target SDK: `API 36`)
- **Build System:** Gradle `8.13` with Kotlin DSL

### **Backend & Cloud Infrastructure**
- **Authentication:** Firebase Authentication (Email/Password)
- **Database:** Google Cloud Firestore (NoSQL hierarchical structure)
- **Data Model:** `users/{uid}` and `users/{uid}/trips/{tripId}`

----------------------------------------------------------------------------------------------------------------------------------------------

## 📂 Project Structure

```text
Auto_Trip/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/.../autotrip/
│   │   │   │   ├── data/
│   │   │   │   │   ├── model/          # User & Trip data classes
│   │   │   │   │   └── repository/     # Firebase & Location repositories
│   │   │   │   ├── service/            # TrackingService (Foreground GPS Service)
│   │   │   │   ├── ui/
│   │   │   │   │   ├── auth/           # Login & Registration screens
│   │   │   │   │   ├── tracking/       # Map picker & active tracking
│   │   │   │   │   ├── tripdetails/    # Trip confirmation & edit sheets
│   │   │   │   │   ├── mytrips/        # Weekly calendar & past trips
│   │   │   │   │   ├── profile/        # 3-Tab Profile & Privacy settings
│   │   │   │   │   └── devtools/       # OSRM GPS Simulation screen
│   │   │   │   └── viewmodel/          # StateFlow-driven ViewModels
│   │   │   └── AndroidManifest.xml
│   │   └── res/
│   └── build.gradle.kts
├── gradle/
└── README.md

----------------------------------------------------------------------------------------------------------------------------------------------

🚀 Getting Started
Prerequisites
1. Android Studio Ladybug (or later)
2. JDK 17+
3. Android device or emulator running Android 12 (API level 31) or higher
4. A configured Firebase Project with Authentication and Firestore enabled

Installation & Setup

1. Clone the repository:
```
git clone [https://github.com/itspuru21/Auto_Trip.git](https://github.com/itspuru21/Auto_Trip.git)
cd Auto_Trip
```
2.

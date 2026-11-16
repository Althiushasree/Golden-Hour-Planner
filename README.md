# ☀️ Golden Hour Planner

A Kotlin-based Android application designed to help photographers plan photoshoots using accurate sunrise, sunset, and golden-hour timings based on their current location and selected date.

## 📱 About the Project

Golden Hour Planner helps photographers identify the best natural-light periods for photography. It retrieves real solar timings using the Sunrise-Sunset API and calculates morning and evening golden hours.

Users can also save, edit, and manage their photography plans locally.

## ✨ Features

- 🔐 Google Sign-In using Firebase Authentication.
- 📍 Current location detection using Fused Location Provider.
- 🌅 Real-time sunrise and sunset information.
- ☀️ Solar noon and day-length details.
- 🌇 Morning and evening golden-hour calculations.
- 🌄 Civil twilight start and end timings.
- 📅 Select any date using MaterialDatePicker.
- ⏳ Countdown to the next sunrise or sunset.
- 📸 Add, view, edit, and delete photo-shoot plans.
- 💾 Local data persistence using Room Database.
- 🕒 Automatic UTC-to-local-time conversion.
- ⚠️ Location and network error handling.

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| Kotlin | Android development |
| XML | User interface |
| ViewBinding | View interaction |
| Firebase Authentication | Google Sign-In |
| Fused Location Provider | Location detection |
| Retrofit | API integration |
| Gson | JSON parsing |
| Room Database | Local storage |
| RecyclerView | Display photo-shoot plans |
| Material Components | UI components and date picker |

## 🌐 API Integration

**Sunrise-Sunset API**

`https://api.sunrise-sunset.org/json`

The application retrieves solar information using:
- Latitude
- Longitude
- Selected date

No API key is required.

## 📐 Golden Hour Calculation

**Morning Golden Hour**

- Start: Sunrise
- End: Sunrise + 1 hour

**Evening Golden Hour**

- Start: Sunset - 1 hour
- End: Sunset

All solar timestamps are converted from UTC to the device's local time zone.

## 📲 Application Workflow

1. User signs in using Google.
2. Application detects the current location.
3. Latitude and longitude are sent to the Sunrise-Sunset API.
4. API returns solar information.
5. Application converts UTC timestamps into local time.
6. Golden-hour timings and countdown are calculated.
7. User can select another date and refresh the information.
8. User can save and manage photo-shoot plans using Room Database.

## 🏗️ Project Structure

```text
Golden-Hour-Planner/
│
├── app/
│   └── src/main/
│       ├── java/
│       │   └── auth/
│       │   └── api/
│       │   └── location/
│       │   └── database/
│       │   └── model/
│       │   └── repository/
│       │   └── viewmodel/
│       │   ├── ui/
│       │   └── utils/
│       │
│       ├── res/
│       │   ├── layout/
│       │   ├── drawable/
│       │   ├── mipmap/
│       │   └── values/
│       │
│       └── AndroidManifest.xml
│
└── README.md
```

## ⚙️ Installation and Setup

1. Clone this repository:

```bash
git clone https://github.com/YOUR-USERNAME/Golden-Hour-Planner.git
```

2. Open the project in Android Studio.

3. Configure Firebase:
   - Create or select a Firebase project.
   - Register the Android application using the correct application ID.
   - Enable Google Authentication.
   - Add your debug SHA-1 and SHA-256 fingerprints.
   - Download `google-services.json` and place it inside the `app/` directory.

4. Sync Gradle dependencies.

5. Connect an Android device or start an emulator.

6. Run the application.

## 🔒 Permissions

The application requires:

- `INTERNET`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`

Location permissions are requested at runtime.

## 🧪 Testing

The project includes tests for:
- UTC-to-local-time conversion.
- Golden-hour calculations.
- Countdown calculations.
- Polar sunrise/sunset handling.
- Room database CRUD operations.

## 🎯 Project Objective

To provide photographers with a simple and convenient tool for identifying golden-hour photography opportunities and organizing photoshoot schedules based on accurate solar information.

## 👩‍💻 Author

**ALTHI USHA SREE**

B.Tech – Computer Science and Engineering

## 📄 License

This project is developed for educational and practical application purposes.

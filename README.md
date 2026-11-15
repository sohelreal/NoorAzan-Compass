# NoorAzan — Prayer Times, Azan, Qibla, Digital Compass, GPS & Map

Combined app: NoorAzan + Digital Compass (GPS, Map & Qibla). A clean, lightweight, battery-efficient native Android application built in **Kotlin** using **Material Design 3**, **AlarmManager**, **Foreground Services**, and **Jetpack Navigation**.

## Features
- **Automatic GPS Prayer Time Calculation:** Computes Fajr, Sunrise, Dhuhr, Asr, Maghrib, and Isha locally offline using high-precision astronomical solar equations (`PrayTimeCalculator`)—zero third-party API dependency.
- **Reliable Background Azan Playback:** Uses `AlarmManager.setAlarmClock()` paired with a `MediaPlayback` Foreground Service and wake-lock so the full Azan plays reliably even when the screen is locked or the app is swiped away.
- **Custom MP3 Support (`assets/azan/`):** Plays `fajr.mp3` for Fajr and `azan.mp3` for Dhuhr, Asr, Maghrib, and Isha (with automatic fallback to the device's default alarm tone if MP3 files have not been added yet).
- **Real-Time Qibla Compass with Magnetic Declination Correction:** Uses device accelerometer + magnetometer sensors (`SensorManager`) combined with Android's `GeomagneticField` to adjust magnetic north to true north for pinpoint Kaaba direction accuracy (`21.4225° N, 39.8262° E`).
- **Fast tilt-compensated compass (new):** `CompassEngine` reads Android's fused rotation-vector sensor at ~50 Hz and
  computes the heading from the full device orientation, so it is correct when the phone is flat, tilted or upright.
  Falls back to accelerometer + magnetometer on devices without the fused sensor.
- **Qibla + Compass modes (new):** the Qibla tab has a Qibla / Compass switch, a True / Magnetic north toggle,
  heading accuracy, magnetic-field strength, interference and calibration warnings, and a vibration when you face the Qibla.
- **Map & GPS tab (new):** live GPS (latitude, longitude, altitude, accuracy, speed, distance to Kaaba), an OpenStreetMap map
  (osmdroid) with a great-circle line to the Kaaba, copy-coordinates and open-in-maps buttons. Map tiles need internet.
- **Five-Tab Material 3 Interface:**
  1. **Home:** Next prayer live countdown, current location, next Azan status, and quick preview/stop controls.
  2. **Prayer Times:** Full daily schedule with active prayer highlighting and one-tap Azan mute/unmute toggles.
  3. **Qibla:** Qibla finder and digital compass (see above).
  4. **Map:** GPS details and map (see above).
  5. **Settings:** Calculation method selector (MWL, ISNA, Umm al-Qura, Egyptian, Karachi, Tehran), Asr juristic school (Standard/Shafi'i vs. Hanafi), per-prayer minute offsets (`-60` to `+60` min), individual prayer Azan toggles, and battery optimization helper.

---

## Adding Your Two Azan MP3 Files

1. Navigate to:
   ```
   app/src/main/assets/azan/
   ```
2. Place your two MP3 files inside that folder with these **exact lowercase filenames**:
   - `fajr.mp3` — played at **Fajr** prayer time.
   - `azan.mp3` — played at **Dhuhr, Asr, Maghrib, and Isha** prayer times.


---

## Build the APK on GitHub
1. Upload the contents of this folder (including the hidden `.github` folder) to a GitHub repository's `main` branch.
2. Open **Actions -> Build Android APK**, wait for it to finish, then download the `NoorAzan-debug-apk` artifact.

## Notes
- Compass accuracy depends on the phone's magnetometer. Keep it away from metal and magnets; if you see the calibration
  warning, move the phone in a figure-8.
- To rename the app, edit `app_name` in `app/src/main/res/values/strings.xml` (and the title text in `fragment_home.xml`).

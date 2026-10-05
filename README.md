# MapAlert

A location-based alarm Android app — set a destination, and MapAlert rings an alarm automatically once you arrive, even if the app is closed.

## How it works
Enter a destination address, tap **Set Alarm**. The app geocodes the address into coordinates and starts tracking your live location in the background. Once you're within 100 meters of the target, an alarm rings and a notification updates to let you know you've arrived. Tap **Stop Alarm** to cancel.

## Android concepts demonstrated
- **Foreground Service** — location tracking and the alarm trigger run independently of the app's UI, continuing even after the app is closed, with a persistent notification as required by Android
- **Runtime permissions** — handles location and notification permission requests, including the "permanently denied" edge case (guiding the user to app Settings when Android stops showing the permission prompt)
- **Geocoder API** — converts a typed address into latitude/longitude
- **FusedLocationProviderClient** — live GPS location updates via `requestLocationUpdates`
- **SharedPreferences** — persists the active alarm's target across app restarts
- **Custom algorithm** — distance between two GPS coordinates calculated using the Haversine formula (accounts for Earth's curvature)
- **NotificationManager / NotificationChannel** — foreground service notification, updated dynamically as distance changes

## Tech
- Java, Android SDK (Views-based UI, no Jetpack Compose)
- Google Play Services (Location)

## How to run
1. Clone the repo, open in Android Studio
2. Run on an emulator or physical device (Android 7.0+ / API 24+)
3. Grant location (and notification, on Android 13+) permissions when prompted

## Known limitations / what I'd add next
- Only one target location can be active at a time — no saved locations list (e.g. "Home", "Office")
- Target must be entered as a typed address — no tap-to-select on a map yet (planned, needs a Google Maps API key)
- Fixed 100m trigger radius — not currently user-adjustable
- Alarm sound is the device's default; no custom sound picker
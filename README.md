# Mock Location

A standard Android mock-location app for testing GPS-dependent apps and projects.
It uses Android's official test provider API (no root).

## Build

1. Open this folder in Android Studio (Koala or newer) and let Gradle sync.
   If it asks about a missing Gradle wrapper, accept the prompt to create or use one.
2. Plug in your phone with USB debugging on and press Run.

## Phone setup (one time)

1. Settings > About phone > tap Build number 7 times to unlock Developer options.
2. Developer options > Select mock location app > Mock Location.
3. Open the app and allow the location and notification permissions.

## Use

- The bar at the top shows the state: green ACTIVE with the coordinates being sent, or gray OFF.
- Tap the map, or type coordinates and press Go, to pick a point.
- The big button is START when off and STOP when on.
- To move while active, pick a new point, press STOP, then START.
- The notification also has a STOP button, so you can end it without opening the app.
- Save this spot stores the current point; Saved spots lists them to load or delete.
- If the app is not selected as the mock location app, START shows a prompt that opens Developer options.

## Notes

- Apps can see that a location is mocked (Android flags it), and some reject it.
- Map tiles come from OpenStreetMap and need an internet connection.
- minSdk 26 (Android 8), targetSdk 34.

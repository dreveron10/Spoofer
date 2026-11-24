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

- Tap the map, or type coordinates and press Go, to pick a point.
- Start begins feeding that point to the GPS and network providers once per second.
- Press Start again after picking a new point to move.
- Stop removes the test providers and returns the phone to real location.
- Save stores the current point; Spots lists saved points to load or delete.

## Notes

- Apps can see that a location is mocked (Android flags it), and some reject it.
- Map tiles come from OpenStreetMap and need an internet connection.
- minSdk 26 (Android 8), targetSdk 34.

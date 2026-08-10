# EV FastRoute for Android

Native Kotlin/Jetpack Compose EV trip planning with MapLibre/OpenFreeMap maps, Photon plus the
device geocoder for place search, openrouteservice road routing, and live Open Charge Map stations.
The planner uses editable public vehicle specifications, real-world range assumptions, a globally
optimized charging sequence, multi-stop visits, traffic-free ETA/SOC timelines, and external
Google Maps/Waze/default-map handoff.

The iOS and Android clients use the same openrouteservice request, OCM corridor query, deterministic
charging optimizer, range inputs, and EV catalog. For identical selected coordinates/settings and
the same provider snapshot, route geometry, charging stops, SOC targets, and objective winners are
expected to match. Native place providers can expose different candidates for the same typed query;
see `PARITY.md` for that explicit production-backend boundary.

The app-owned Android experience mirrors the iOS product shell: branded onboarding, the dark glass
visual system, full-screen place search, and Plan, Route, Garage, and Settings tabs. Platform-owned
permission dialogs, the MapLibre renderer, and external navigation apps remain native to Android.
The app-owned flow includes absolute scheduled departure, editable re-planning after results,
catalog or manual vehicles with an editable model year, tappable charging-station details, and a
maneuver map with follow/manual/overview camera and full EV reroute.

## Modules

- `:core` — deterministic routing, energy, range, search ranking, catalog, charger parsing/scoring,
  navigation URL/session logic, and JVM tests.
- `:app` — Compose UI, MapLibre's stable OpenGL ES map backend for broad device compatibility,
  Android location/geocoder, network clients, persistence, and
  distribution configuration.

## Verify

JDK 17 and Android SDK 36 are required:

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Provider keys are intentionally absent from the repository. See `DISTRIBUTION.md` for protected
configuration/signing, `PLAY_SUBMISSION.md` for release blockers, `PRIVACY.md` for data handling,
and `PARITY.md` for the iOS/Android feature comparison.

Route/range/station output is planning assistance, not a guarantee. Drivers must verify stations
and follow the external navigator, road signs, vehicle warnings, and safe driving judgment.

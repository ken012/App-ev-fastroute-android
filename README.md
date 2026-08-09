# EV FastRoute for Android

Native Kotlin/Jetpack Compose EV trip planning with MapLibre/OpenFreeMap maps, Photon plus the
device geocoder for place search, openrouteservice road routing, and live Open Charge Map stations.
The planner uses editable public vehicle specifications, real-world range assumptions, a globally
optimized charging sequence, multi-stop visits, traffic-free ETA/SOC timelines, and external
Google Maps/Waze/default-map handoff.

## Modules

- `:core` — deterministic routing, energy, range, search ranking, catalog, charger parsing/scoring,
  navigation URL/session logic, and JVM tests.
- `:app` — Compose UI, MapLibre map, Android location/geocoder, network clients, persistence, and
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

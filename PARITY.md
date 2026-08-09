# EV FastRoute — iOS/Android parity

iOS is SwiftUI/MapKit; Android is native Kotlin/Compose. The platforms share product behavior and
ported/tested planning logic, but use different map/search/routing providers where OS APIs differ.

## Current Android stack

| Concern | iOS | Android | Production note |
|---|---|---|---|
| Map | MapKit | MapLibre Native + OpenFreeMap | Android keeps on-map attribution enabled |
| Routing | MKDirections | openrouteservice | Traffic-free; production capacity/proxy required at scale |
| Search | MKLocalSearch | Android device geocoder + Photon + shared ranker | Photon public demo has no SLA |
| Chargers | Open Charge Map | Open Charge Map | `compact=false`, `opendata=true`, provider attribution shown |
| Storage | UserDefaults | app-private SharedPreferences | Android cloud/device-transfer backup disabled |
| Navigation | Apple/Google/Waze handoff | Google/Waze/default-app handoff | Waze/default are sequential stop-by-stop |

## Shared/ported behavior

| Capability | Android implementation | Verification |
|---|---|---|
| Charge planning and SOC feasibility | `ChargePlanner`, `EnergyModel`, `RoutePlanner` | JVM tests |
| Objective-aware global charger sequence | `ChargerSequenceSelector` beam search | JVM tests |
| Fastest, confidence, known-cost, Fewest stops | `RouteObjective` + optimizer/dedup | JVM tests |
| Fewest stops may charge beyond 80% (up to 95%) | route builders calculate next-leg requirement | JVM tests |
| Multi-stop visits with globally inserted charging | `planThrough` + interleaved route builder | JVM tests |
| Conservative range | vehicle/battery health/weather/load/style/route speed + uncertainty | JVM tests |
| 789-car editable vehicle catalog | bundled OpenEV asset + Kia supplement | real-asset tests |
| Charger compatibility/power/status/cost/confidence | strict OCM mapping + filters | JVM tests |
| Search relevance/proximity/typos/dedup/broadening | `PlaceRanker` | JVM tests |
| Route geometry/corridor segmentation | strict ORS parsing + route covering boxes | JVM tests |
| Google/Waze/default deep links | UTF-8-safe coordinate-first URLs | JVM tests |
| Guided external-navigation progress | expiring `NavigationSession` + foreground arrival prompt | JVM tests + UI smoke |
| Saved trips, vehicle overrides, filters, settings | `SettingsStore` | serialization/runtime smoke |

## Android app capabilities

- Start/destination search, current location, ordered user waypoints, reorder/remove.
- Vehicle picker, per-vehicle editable battery/consumption/DC power/connectors/battery health.
- Weather loss, passenger/cargo, driving-style range assumptions.
- Minimum charging speed, preferred/avoided networks, optional low-confidence exclusion.
- Live charging-station corridor retrieval in bounded overlapping boxes with success-only caches.
- Route option cards, deduplicated-objective badges, map line/pins, arrival battery/timeline, known
  single-currency cost only, provider-specific OCM licensing, and visible safety disclosure.
- Manual and five-minute-on-resume route refresh (suppressed during an active guided trip).
- Full Google trip where the documented waypoint cap permits; safe sequential sessions otherwise.
- Adaptive launcher icon, edge-to-edge/dark theme, API 26 minimum, API 36 target, R8 release build,
  16 KB native-library CI check, unit/lint/build gate, and emulator launch smoke test.

## Deliberate differences / remaining product work

- Android free-stack ETAs do not include live traffic and the map has no traffic overlay. The UI
  labels them traffic-free; do not market traffic parity with iOS.
- Foreground-only location is deliberate. Background navigation remains in Google/Waze/default maps.
- The vehicle catalog is decoded synchronously once at process startup (about 404 KB/789 records),
  with a built-in fallback if it fails. Measure cold start on release devices before expanding it.
- Android launches in English only. Localization is a separate product milestone before marketing
  to non-English audiences.
- The bounded beam search reports “fewest charging sessions found among verified candidates,” not a
  mathematical proof over every station on earth. Every minimum-count candidate retained by the
  bounded search is road-verified before a longer candidate can receive the label.
- Production provider capacity, a secure service proxy, Play listing/policy setup, permanent signing,
  and physical-device/road smoke tests are operational release blockers documented in
  `PLAY_SUBMISSION.md`; they cannot be completed solely in source code.

## Verification standard

A feature is “code-complete” only after deterministic tests and a compiled Android module. It is
“release-verified” only after the signed artifact passes lint/R8/16 KB checks, emulator launch, and
the physical test matrix in `PLAY_SUBMISSION.md` on the same commit.

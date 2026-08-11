# EV FastRoute — iOS/Android parity

iOS is SwiftUI/MapKit; Android is native Kotlin/Compose/MapLibre. Both clients now use the same
openrouteservice driving contract, Open Charge Map query/mapping rules, EV catalog, range model,
charger-sequence search, and final objective selection. Given the same selected coordinates,
vehicle/settings, and provider snapshot, they are expected to return the same road geometry,
charging-stop sequence, SOC targets, ETA math, and route objectives.

The mandatory delivery process for new user-facing work is documented in
[`FEATURE_PARITY.md`](FEATURE_PARITY.md). A feature is not complete until both native clients are
implemented and verified, unless an explicit platform exception is approved and recorded.

## Current Android stack

| Concern | iOS | Android | Production note |
|---|---|---|---|
| Map | MapKit | MapLibre Native OpenGL ES + OpenFreeMap | Widest-compatible stable backend; on-map attribution remains enabled |
| Routing | openrouteservice | openrouteservice | Same `driving-car/geojson` request; traffic-independent; proxy/capacity required at scale |
| Search | MKLocalSearch/CLGeocoder + shared ranker | Android geocoder + Photon + shared ranker | Ranking rules match; provider inventories do not, so typed-query results cannot be guaranteed identical |
| Chargers | Open Charge Map | Open Charge Map | Route planning uses the same `minpowerkw=25`/200-result boxes; browse maps merge all-power and 25+ kW/300-result requests so Level-2 cannot crowd out DC |
| Storage | UserDefaults | app-private SharedPreferences | Android cloud/device-transfer backup disabled |
| Product shell | SwiftUI branded shell | Compose branded shell | Same onboarding and Plan/Route/Garage/Settings information architecture |
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
| Safe NACS→CCS1 adapter handling | explicit per-vehicle confirmation; unconfirmed legacy profiles fail closed | JVM/persistence tests |
| Region/destination charging map | dual OCM fetch, Level-2/J1772, status markers, station details, retry/partial success | JVM tests; device map smoke pending |
| Search relevance/proximity/typos/dedup/broadening plus ten on-device recent places | `PlaceRanker` + `SettingsStore` | JVM/persistence tests |
| Route geometry/corridor segmentation | strict ORS parsing + route covering boxes | JVM tests |
| Google/Waze/default deep links | UTF-8-safe coordinate-first URLs | JVM tests |
| Guided navigation progress | maneuvers, follow/manual/overview camera, full EV reroute, expiring `NavigationSession`, foreground arrival prompt | JVM tests; emulator/device smoke pending on this commit |
| Onboarding, scheduled departure, editable replanning, Garage/custom vehicles (including editable model year), saved trips, filters, settings/reset | `SettingsStore` + Compose shell | JVM/Compose tests; device smoke pending |

## Android app capabilities

- iOS-matched dark glass visual system, first-run onboarding, and four primary tabs: Plan, Route,
  Garage, and Settings. Native permission dialogs and the platform map renderer remain Android-native.
- Full-screen start/destination/stop search, current location, persistent recent places, ordered user waypoints, swap,
  reorder, and remove.
- Persistent multi-vehicle Garage, searchable vehicle picker, manual/custom profiles, and editable
  identity/battery/consumption/DC power/connectors/battery health/default arrival reserve.
- Weather loss, passenger/cargo, driving-style range assumptions.
- Minimum charging speed, preferred/avoided networks, optional low-confidence exclusion.
- Live charging-station corridor retrieval in bounded overlapping boxes with success-only caches.
- Trip-independent **Charging map** and destination-centered charger browse, including Level-2,
  J1772, reported/offline stations, honest unknown-power display, station details, and retry.
- NACS vehicles never gain CCS1 routing from catalog metadata alone. Garage requires explicit
  confirmation of vehicle support and adapter availability; older ambiguous profiles fail closed.
- Route option cards, deduplicated-objective badges, map line/pins, arrival battery/timeline, known
  single-currency cost only, tappable station-detail pages, provider-specific OCM licensing, and
  visible safety disclosure.
- Absolute scheduled departure with frozen per-stop arrival clock times, plus manual and
  five-minute-on-resume refresh (suppressed during an active guided trip).
- Full-map guided-trip screen with ORS maneuvers, live foreground follow, pan-to-manual camera,
  Recenter/Overview/Reroute controls, conservative off-route detection, complete charger/SOC
  recalculation, next-stop progress, arrival confirmation, and external-navigation handoff.
- Results can always return to the populated planner, edit any field, and calculate again.
- Full Google trip where the documented waypoint cap permits; safe sequential sessions otherwise.
- Adaptive launcher icon, edge-to-edge iOS-matched dark theme, API 26 minimum, API 36 target, R8 release build,
  16 KB native-library CI check, unit/lint/build gate, and an emulator launch-smoke workflow.

## Deliberate differences / remaining product work

- Both apps deliberately show traffic-independent openrouteservice ETAs. Scheduled departure sets
  itinerary clock times; it does not claim traffic prediction. Neither app should market live traffic.
- Search ranking, typo handling, proximity, deduplication, and broadening are behavior-matched, but
  Apple and Android/Photon can return different underlying places for the same typed words. Exact
  cross-platform typed-search parity requires a shared production geocoder behind the planned
  backend. Once a coordinate is selected, the route pipeline is shared.
- Foreground-only location is deliberate. Background navigation remains in Google/Waze/default maps.
- The vehicle catalog is decoded synchronously once at process startup (about 404 KB/789 records),
  with a built-in fallback if it fails. Measure cold start on release devices before expanding it.
- Android launches in English only. Localization is a separate product milestone before marketing
  to non-English audiences.
- Pixel-for-pixel equality is not claimed for OS-owned surfaces: Android permission dialogs,
  keyboard, map labels/gestures, and external navigation apps retain their native behavior.
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

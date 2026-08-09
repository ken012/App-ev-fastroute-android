# EV FastRoute — iOS ⇄ Android parity

Goal: **both apps at the same standard.** iOS (SwiftUI/MapKit, repo `ken012/App-ev-fastroute`)
is the canonical spec. Android is native Kotlin/Compose. The *intelligence* (routing, charging,
objectives) is ported **test-for-test** so behavior is provably identical; the UI is
platform-native but must match feature-for-feature.

## Free stack (Android)
| Concern | iOS | Android | Notes |
|---|---|---|---|
| Map render | MapKit | **MapLibre GL** | free, no key |
| Tiles/style | (MapKit) | **OpenFreeMap** | free, no key |
| Routing | MKDirections | **OpenRouteService** | free key (~2k/day); paid/self-host at scale |
| Address search | MKLocalSearch | **Photon** (OSM) | free, no key |
| Chargers | Open Charge Map | Open Charge Map | same API + key |
| Storage | UserDefaults | DataStore | |

## Ported logic (pure Kotlin `:core`, JVM-testable on free CI)
| iOS source | Android | Tests ported | Status |
|---|---|---|---|
| `ChargePlan.swift` (ChargePlanner) | `ChargePlanner.kt` | `ChargePlannerTest` | ✅ done |
| haversine (NavigationTracker) | `Geometry.kt` | `GeometryTest` | ✅ done |
| `RouteOptimizationService` energyPlan | `EnergyModel.kt` | `EnergyModelTest` | ✅ done |
| `RouteObjective.swift` | `RouteObjective.kt` | `RouteObjectiveTest` | ✅ done |
| Charger projection / corridor | `Corridor.kt` | `CorridorTest` | ✅ done |
| Planner models (Charger/Vehicle/ConnectorType/ProjectedCharger) | `Models.kt` | `ChargerScoringTest` | ✅ done |
| Charger scoring (speedScore/networkMatches/sequenceKey) | `ChargerScoring.kt` | `ChargerScoringTest` | ✅ done |
| Beam-search sequence selection (objective-aware) | `ChargerSequenceSelector.kt` | `ChargerSequenceSelectorTest` | ✅ done |
| Build route from sequence+leg data (SOC walk → RouteOption) | `RoutePlanner.buildRoute` | `RoutePlannerTest` | ✅ done |
| Optimize: best-per-objective + dedup-merge + comparator | `RoutePlanner.optimize` | `RoutePlannerTest` | ✅ done |
| Live planning glue (direct→project→beam-search→build→optimize) | `:app` TripPlanner | (net-bound) | ✅ |
| `RangeEstimator.swift` | `RangeEstimator.kt` | `RangeEstimatorTest` | ✅ done |
| `PlaceSearch.swift` (ranker) | `PlaceRanker.kt` | `PlaceRankerTest` | ✅ done |
| `EVCatalog.swift` (presets/search/region connectors/makeVehicle) | `EvCatalog.kt` | `EvCatalogTest` | ✅ done |
| Bundled 789-car OpenEV catalog (parse/promote + built-in fallback) | `EvCatalog.loadBundledCatalog` + `assets/ev_catalog.json` + `EvApp` | `CatalogDocumentTest` | ✅ done |

## App/services (Android `:app`, added after core CI is green)
| Feature (iOS) | Android | Status |
|---|---|---|
| OCM response parsing → Charger (+ connector/reliability) | `OpenChargeMap.kt` (:core) | `OpenChargeMapTest` ✅ |
| OCM HTTP fetch | `:app` OpenChargeMapClient | ⬜ |
| ORS directions parsing → RouteLeg | `OpenRouteService.kt` (:core) | `OpenRouteServiceTest` ✅ |
| ORS HTTP fetch | `:app` net/OrsClient | ✅ |
| Photon geocoding parsing → PlaceCandidate | `Photon.kt` (:core) | `PhotonTest` ✅ |
| Photon HTTP fetch | `:app` net/PhotonClient | ✅ |
| Map + charger pins + route line | MapLibre RouteMap (OpenFreeMap) | ✅ v1 |
| Planner (start/dest search + battery/buffer + Find Route) | Compose PlannerApp | ✅ v1 |
| Route options list (title/ETA/stops/cost/itinerary) | Compose RouteCard | ✅ v1 |
| Vehicle picker + catalog (search, pick, region connectors) | Compose VehiclePicker + `EvCatalog` | ✅ v1 |
| Nav handoff URL builders (Google/Waze/geo, waypoint cap, notes) | `Navigation.kt` (:core) | `NavigationLinksTest` ✅ |
| Nav handoff launch (ACTION_VIEW intent) + Directions buttons | `:app` NavLauncher + DirectionsRow | ✅ v1 |
| Multi-stop through-waypoints SOC walk (global charging) | `RoutePlanner.buildRouteThroughWaypoints` | `MultiStopRouteTest` ✅ |
| Multi-stop planning glue (corridor/progress/beam over full trip) | `:app` TripPlanner.planThrough | ✅ |
| Multi-stop UI (add/edit/reorder/remove stops + map pins) | Compose WaypointRow + RouteMap | ✅ v1 |
| Arrival timeline (per-stop clock time + battery, depart→arrive) | Compose ArrivalTimeline | ✅ v1 |
| Scheduled departure (shifts arrival-timeline clock) + free-flow ETA disclosure | Compose ArrivalTimeline | ✅ v1 |
| Live traffic overlay / traffic-aware ETAs | — | ⛔ not feasible on the free stack (no free MapKit-equivalent traffic source; documented, not faked) |
| Sequential-handoff session state machine (progress + arrival prompt) | `NavigationSession` (:core) | `NavigationSessionTest` | ✅ |
| Sequential nav handoff (persistent session, per-stop advance) | Compose GuidedTripBanner + `SettingsStore` | ✅ v1 (manual confirm; GPS auto-prompt is the remaining sliver) |
| Region + units model (currency/connectors/imperial/detect) | `Region.kt` + `Units` (:core) | `RegionTest` ✅ |
| Settings (units, region, preferred nav) + persistence | Compose SettingsScreen + `SettingsStore` | ✅ v1 |
| Tester distribution (installable APK + Firebase App Distribution) | CI artifact + guarded `distribute` job | ✅ v1 (see DISTRIBUTION.md) |

## Verification
- `:core` builds + tests on free GitHub Actions (Linux) — proves logic parity.
- `:app` builds an APK on CI; distributed to testers via **Firebase App Distribution** (free).
- Keep this checklist current: a feature isn't "done" until it exists on **both** platforms.

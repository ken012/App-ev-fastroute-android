# EV FastRoute Privacy Policy

Effective date: August 9, 2026

EV FastRoute helps drivers plan electric-vehicle trips. This policy describes the Android app in
this repository. The app does not require an EV FastRoute account, does not contain advertising,
and does not sell personal information.

## Data processed

When you search or plan a trip, the app sends the information needed to answer your request to its
configured service providers:

- typed place-search text and an approximate search anchor to Photon and, when available, the
  geocoding service supplied by the Android device manufacturer;
- selected start, destination, intermediate-stop, and charging-station coordinates to
  openrouteservice or the configured routing service;
- route-area bounding boxes to Open Charge Map to retrieve charging stations;
- map tile requests to OpenFreeMap and its underlying map-data services.

Those providers receive ordinary network information such as your IP address and may process data
under their own terms and privacy policies. Production deployments may route these requests through
an EV FastRoute-operated proxy; the same request data is still needed to provide the feature.

## Device location

Location access is optional and foreground-only. If allowed, it is used to set the trip's starting
point and to offer an arrival-confirmation prompt during a guided external-navigation session. EV
FastRoute does not request background location and does not automatically advance the itinerary.
Location samples are not retained after the active in-app operation, except that a trip you
explicitly save contains the coordinates you selected.

## Data stored on your device

The app stores preferences, selected vehicle specifications, saved trips, and an active guided-trip
session in app-private storage. A guided session expires after 24 hours. Android cloud backup is
disabled for the app because these records may contain precise trip coordinates. You can remove
saved trips in the app or erase all local data by uninstalling the app or clearing its storage in
Android Settings.

## Retention and sharing

EV FastRoute does not operate an account database in this version. It does not intentionally send
trip data to analytics, advertising, or data-broker services. Network providers may retain request
logs according to their own policies. Data is disclosed when required by law or necessary to
protect users and the service.

## Children

EV FastRoute is a driving utility and is not directed to children under 13.

## Security and accuracy

Requests use HTTPS. No mobile application can guarantee absolute security. Route, range, station,
price, status, and availability information may be incomplete or stale; drivers should verify a
station with its operator and use safe judgment before travel.

## Changes and contact

Material changes will be published in this file with a new effective date. Questions or privacy
requests can be submitted through the publisher's support tracker at
<https://github.com/ken012/App-ev-fastroute-android/issues>. Do not include precise trip coordinates,
API credentials, or other sensitive information in a public issue. The publisher must keep a
hosted copy of this policy at the URL entered in Google Play Console.

package com.evfastroute.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Locks the ported navigation-handoff URL builders so deep links match iOS byte-for-byte. */
class NavigationLinksTest {

    private fun point(lat: Double, lon: Double, name: String, kind: NavigationPoint.Kind = NavigationPoint.Kind.VISIT) =
        NavigationPoint(lat, lon, name, kind)

    private val dest = point(43.6532, -79.3832, "Toronto", NavigationPoint.Kind.DESTINATION)

    @Test
    fun googleSingleDestinationEncodesCoordinatesAndDrivingIntent() {
        val url = NavigationLinks.googleDirectionsUrl(origin = null, stops = emptyList(), destination = dest)
        assertNotNull(url)
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?"))
        assertTrue(url.contains("api=1"))
        // comma is escaped to %2C (unreserved set excludes it), just like iOS.
        assertTrue(url.contains("destination=43.6532%2C-79.3832"))
        assertTrue(url.contains("travelmode=driving"))
        assertTrue(url.contains("dir_action=navigate"))
        assertTrue(!url.contains("waypoints="))
    }

    @Test
    fun googleWithWaypointsJoinsWithEncodedPipe() {
        val stops = listOf(point(44.0, -78.0, "A", NavigationPoint.Kind.CHARGING), point(44.5, -78.5, "B", NavigationPoint.Kind.CHARGING))
        val url = NavigationLinks.googleDirectionsUrl(origin = point(45.0, -75.0, "Home"), stops = stops, destination = dest)
        assertNotNull(url)
        assertTrue(url.contains("origin=45.0%2C-75.0"))
        // pipe between waypoints is escaped to %7C.
        assertTrue(url.contains("waypoints=44.0%2C-78.0%7C44.5%2C-78.5"))
    }

    @Test
    fun googleReturnsNullPastWaypointCap() {
        val stops = (1..4).map { point(44.0 + it, -78.0, "S$it", NavigationPoint.Kind.CHARGING) }
        assertNull(NavigationLinks.googleDirectionsUrl(null, stops, dest))
    }

    @Test
    fun wazeUrlCarriesCoordinateNameAndNavigateFlag() {
        val url = NavigationLinks.wazeDirectionsUrl(point(45.42, -75.69, "Rideau Centre"))
        assertNotNull(url)
        assertTrue(url.startsWith("https://waze.com/ul?"))
        assertTrue(url.contains("ll=45.42%2C-75.69"))
        assertTrue(url.contains("q=Rideau%20Centre"))
        assertTrue(url.contains("navigate=yes"))
    }

    @Test
    fun geoUrlEmbedsCoordinateAndLabel() {
        val url = NavigationLinks.geoUrl(point(45.42, -75.69, "Rideau Centre"))
        assertTrue(url.startsWith("geo:45.42,-75.69?q="))
        assertTrue(url.contains("45.42,-75.69(Rideau%20Centre)"))
    }

    @Test
    fun handoffGoogleWithinCapGivesFullTripNoNote() {
        val stops = listOf(point(44.0, -78.0, "Charger", NavigationPoint.Kind.CHARGING))
        val plan = NavigationLinks.handoff(NavigationApp.GOOGLE_MAPS, null, stops, dest)
        assertNotNull(plan)
        assertNull(plan.note)
        assertTrue(plan.url.contains("waypoints=44.0%2C-78.0"))
    }

    @Test
    fun handoffGoogleOverCapRoutesToNextStopWithNote() {
        val stops = (1..4).map { point(44.0 + it, -78.0, "Charger $it", NavigationPoint.Kind.CHARGING) }
        val plan = NavigationLinks.handoff(NavigationApp.GOOGLE_MAPS, null, stops, dest)
        assertNotNull(plan)
        assertNotNull(plan.note)
        assertTrue(plan.note!!.contains("Charger 1"))
        // routed to the single next stop, not the destination, and no waypoints packed in.
        assertTrue(plan.url.contains("destination=45.0%2C-78.0"))
        assertTrue(!plan.url.contains("waypoints="))
    }

    @Test
    fun handoffWazeRoutesToFirstStopWhenMultiStop() {
        val stops = listOf(point(44.0, -78.0, "First Charger", NavigationPoint.Kind.CHARGING))
        val plan = NavigationLinks.handoff(NavigationApp.WAZE, null, stops, dest)
        assertNotNull(plan)
        assertTrue(plan.url.contains("ll=44.0%2C-78.0"))
        assertNotNull(plan.note)
    }

    @Test
    fun handoffWazeDirectToDestinationHasNoNote() {
        val plan = NavigationLinks.handoff(NavigationApp.WAZE, null, emptyList(), dest)
        assertNotNull(plan)
        assertTrue(plan.url.contains("ll=43.6532%2C-79.3832"))
        assertNull(plan.note)
    }

    @Test
    fun actionLabelReflectsCapability() {
        assertEquals("Full trip in Google Maps", NavigationLinks.actionLabel(NavigationApp.GOOGLE_MAPS, 2))
        assertEquals("Next stop in Google Maps", NavigationLinks.actionLabel(NavigationApp.GOOGLE_MAPS, 5))
        assertEquals("Next stop in Waze", NavigationLinks.actionLabel(NavigationApp.WAZE, 1))
        assertEquals("Navigate in Waze", NavigationLinks.actionLabel(NavigationApp.WAZE, 0))
    }

    @Test
    fun encodesUrlReservedCharactersInNames() {
        // A name with & # space must be percent-encoded so it can't split the query or become a
        // fragment: & → %26, space → %20, # → %23.
        val waze = NavigationLinks.wazeDirectionsUrl(point(45.0, -75.0, "A&W #3"))
        assertNotNull(waze)
        assertTrue(waze.contains("q=A%26W%20%233"), "waze q not encoded: $waze")
        assertTrue(!waze.contains("q=A&W"))
        val geo = NavigationLinks.geoUrl(point(45.0, -75.0, "A&W #3"))
        assertTrue(geo.contains("(A%26W%20%233)"), "geo label not encoded: $geo")
    }

    @Test
    fun percentEncodingHandlesUnicodeCodePointsWithoutReplacementBytes() {
        val waze = NavigationLinks.wazeDirectionsUrl(point(45.0, -75.0, "Café 🚗"))!!
        assertTrue(waze.contains("q=Caf%C3%A9%20%F0%9F%9A%97"), waze)
        assertTrue(!waze.contains("%EF%BF%BD"), waze)
    }

    @Test
    fun fromSerializedFallsBackToGoogle() {
        assertEquals(NavigationApp.WAZE, NavigationApp.fromSerialized("waze"))
        assertEquals(NavigationApp.GOOGLE_MAPS, NavigationApp.fromSerialized("nonsense"))
        assertEquals(NavigationApp.GOOGLE_MAPS, NavigationApp.fromSerialized(null))
    }
}

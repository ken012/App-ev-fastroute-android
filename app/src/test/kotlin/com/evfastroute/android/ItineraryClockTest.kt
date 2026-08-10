package com.evfastroute.android

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ItineraryClockTest {
    private val zone = ZoneId.of("America/Toronto")
    private val locale = Locale.US

    @Test
    fun sameDayArrivalShowsClockOnly() {
        val departure = LocalDateTime.of(2026, 8, 10, 22, 45).atZone(zone).toInstant().toEpochMilli()
        assertEquals("11:45 PM", clockLabel(departure, 60, zone, locale))
    }

    @Test
    fun overnightArrivalIncludesAbbreviatedDate() {
        val departure = LocalDateTime.of(2026, 8, 10, 23, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Aug 11, 12:30 AM", clockLabel(departure, 60, zone, locale))
    }
}

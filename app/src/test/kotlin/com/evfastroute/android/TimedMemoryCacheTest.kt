package com.evfastroute.android

import com.evfastroute.android.net.TimedMemoryCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimedMemoryCacheTest {
    @Test
    fun entriesExpireAndLeastRecentlyUsedEntryIsEvicted() {
        var now = 1_000L
        val cache = TimedMemoryCache<String, Int>(maxEntries = 2, ttlMillis = 100L) { now }
        cache.put("a", 1)
        cache.put("b", 2)
        assertEquals(1, cache.get("a")) // a is now the most recently used
        cache.put("c", 3)
        assertNull(cache.get("b"))
        assertEquals(1, cache.get("a"))
        now += 101L
        assertNull(cache.get("a"))
        assertNull(cache.get("c"))
    }
}

package com.evfastroute.android.net

/** Small process-local LRU cache for successful provider responses. It reduces duplicate public
 * API traffic while keeping all trip/search data ephemeral (nothing is written to disk). */
internal class TimedMemoryCache<K, V>(
    private val maxEntries: Int,
    private val ttlMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry<V>(val value: V, val storedAtMillis: Long)

    private val values = object : LinkedHashMap<K, Entry<V>>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(key: K): V? {
        val entry = values[key] ?: return null
        val now = clock()
        if (now - entry.storedAtMillis > ttlMillis || now < entry.storedAtMillis) {
            values.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        values[key] = Entry(value, clock())
    }
}

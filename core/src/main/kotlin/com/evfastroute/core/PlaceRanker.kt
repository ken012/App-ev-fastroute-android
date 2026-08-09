package com.evfastroute.core

import java.text.Normalizer
import java.util.Locale

// Relevance-first place-search ranking. Faithful port of iOS PlaceSearch.swift (PlaceSearchRanker):
// keeps relevance and proximity separate so an exact city/place/address match always beats a
// nearby but loosely-related business. Diacritic/width/case-insensitive, typo-tolerant (bounded
// Levenshtein), with de-duplication and a broaden-on-weak-results hint.

/** The fields the ranker needs from a search result (mirrors iOS LocationSuggestion). */
data class PlaceCandidate(
    val placeName: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double? = null,
)

object PlaceRanker {

    private data class Scored(val item: PlaceCandidate, val relevance: Int, val providerIndex: Int)

    fun rank(items: List<PlaceCandidate>, query: String, haveAnchor: Boolean): List<PlaceCandidate> {
        val unique = deduplicated(items)
        val scored = unique.mapIndexed { index, item ->
            Scored(item, relevanceScore(item.placeName, item.fullAddress, query), index)
        }
        val comparator = Comparator<Scored> { a, b ->
            if (a.relevance != b.relevance) return@Comparator b.relevance.compareTo(a.relevance) // higher first
            if (haveAnchor) {
                val left = a.item.distanceKm ?: Double.MAX_VALUE
                val right = b.item.distanceKm ?: Double.MAX_VALUE
                if (left != right) return@Comparator left.compareTo(right)
            }
            a.providerIndex.compareTo(b.providerIndex)
        }
        return scored.sortedWith(comparator).map { it.item }
    }

    fun relevanceScore(placeName: String, fullAddress: String, query: String): Int {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return 0

        val normalizedName = normalize(placeName)
        val normalizedAddress = normalize(fullAddress)
        val allText = "$normalizedName $normalizedAddress"
        val queryTokens = tokens(normalizedQuery)
        val nameTokens = tokens(normalizedName)
        val addressTokens = tokens(normalizedAddress)

        var score = 0
        if (normalizedName == normalizedQuery) {
            score += 12_000
        } else if (normalizedName.startsWith(normalizedQuery)) {
            score += 9_000
        } else if (normalizedName.contains(normalizedQuery)) {
            score += 7_000
        } else if (allText.contains(normalizedQuery)) {
            score += 5_000
        }

        var matchedAnywhere = 0
        var matchedInName = 0
        for (queryToken in queryTokens) {
            val nameQuality = bestMatchQuality(queryToken, nameTokens)
            val addressQuality = bestMatchQuality(queryToken, addressTokens)
            if (nameQuality > 0) {
                matchedAnywhere++
                matchedInName++
                score += nameQuality * 6
            } else if (addressQuality > 0) {
                matchedAnywhere++
                score += addressQuality * 4
            }
        }

        if (queryTokens.isNotEmpty() && matchedAnywhere == queryTokens.size) score += 3_000
        if (queryTokens.isNotEmpty() && matchedInName == queryTokens.size) score += 2_000

        // A typed street number is strong intent: reward the exact number, demote a mismatch.
        val allTextTokens = tokens(allText)
        val numericTokens = queryTokens.filter { token -> token.isNotEmpty() && token.all { it.isDigit() } }
        for (number in numericTokens) {
            if (allTextTokens.contains(number)) score += 2_500 else score -= 2_500
        }

        return score
    }

    /** A weak or very small local result set deserves a second, geographically broad request. */
    fun shouldBroaden(items: List<PlaceCandidate>, query: String): Boolean {
        if (items.isEmpty()) return true
        val best = items.maxOf { relevanceScore(it.placeName, it.fullAddress, query) }
        return items.size < 5 || best < 2_000
    }

    fun deduplicated(items: List<PlaceCandidate>): List<PlaceCandidate> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<PlaceCandidate>()
        for (item in items) {
            // ~11 m precision collapses duplicate provider records without merging separate
            // storefronts in the same plaza. Locale.US keeps the decimal point stable.
            val latitude = String.format(Locale.US, "%.4f", item.latitude)
            val longitude = String.format(Locale.US, "%.4f", item.longitude)
            val key = "${normalize(item.placeName)}|$latitude|$longitude"
            if (seen.add(key)) result.add(item)
        }
        return result
    }

    /** Case/diacritic/width-insensitive alphanumeric normalization (matches iOS `normalize`). */
    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
        val builder = StringBuilder()
        for (ch in decomposed) {
            if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue // strip diacritics
            builder.append(if (ch.isLetterOrDigit()) ch.lowercaseChar() else ' ')
        }
        return builder.toString().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun tokens(normalizedValue: String): List<String> =
        normalizedValue.split(" ").filter { it.isNotEmpty() }

    /** 100 = exact token, 80 = prefix, 60/45 = small typo. Port of iOS bestMatchQuality. */
    private fun bestMatchQuality(queryToken: String, candidateTokens: List<String>): Int {
        var best = 0
        for (candidate in candidateTokens) {
            if (candidate == queryToken) return 100
            if (queryToken.length >= 2 && (candidate.startsWith(queryToken) || queryToken.startsWith(candidate))) {
                best = maxOf(best, 80)
                continue
            }
            if (queryToken.length < 4 || candidate.length < 4) continue
            val allowedDistance = if (maxOf(queryToken.length, candidate.length) >= 8) 2 else 1
            val distance = editDistance(queryToken, candidate, allowedDistance)
            if (distance <= allowedDistance) {
                best = maxOf(best, if (allowedDistance == 1) 60 else 45)
            }
        }
        return best
    }

    /** Bounded Levenshtein distance. Returns limit+1 early for clearly-unrelated words. */
    private fun editDistance(lhs: String, rhs: String, limit: Int): Int {
        val left = lhs.toCharArray()
        val right = rhs.toCharArray()
        if (kotlin.math.abs(left.size - right.size) > limit) return limit + 1
        if (left.contentEquals(right)) return 0

        var previous = IntArray(right.size + 1) { it }
        for (leftIndex in left.indices) {
            val current = IntArray(right.size + 1)
            current[0] = leftIndex + 1
            var rowMinimum = current[0]
            for (rightIndex in right.indices) {
                val substitution = previous[rightIndex] + if (left[leftIndex] == right[rightIndex]) 0 else 1
                current[rightIndex + 1] = minOf(minOf(previous[rightIndex + 1] + 1, current[rightIndex] + 1), substitution)
                rowMinimum = minOf(rowMinimum, current[rightIndex + 1])
            }
            if (rowMinimum > limit) return limit + 1
            previous = current
        }
        return previous[right.size]
    }
}

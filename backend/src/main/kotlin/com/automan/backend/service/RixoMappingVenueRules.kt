package com.automan.backend.service

/**
 * Pure venue uniqueness helpers for [RixoMappingService] / controller validation.
 * One supplier ([auctionName]) should have at most one distinct non-blank venue going forward.
 */
object RixoMappingVenueRules {
    data class VenueConflict(
        val auctionName: String,
        val venues: List<String>,
        val blankVenueRowCount: Int,
    )

    fun normalizeVenue(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }

    fun distinctVenues(rawVenues: Collection<String?>): List<String> =
        rawVenues
            .mapNotNull { normalizeVenue(it) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    /** Returns the venue only when exactly one distinct non-blank value exists. */
    fun resolveUnique(rawVenues: Collection<String?>): String? {
        val distinct = distinctVenues(rawVenues)
        return if (distinct.size == 1) distinct[0] else null
    }

    /**
     * Reject adding a *new* distinct venue when the supplier already has a different one.
     * Same venue (case-insensitive) is allowed. Blank requested venue → no check.
     */
    fun rejectSecondVenue(existingVenues: Collection<String?>, requestedVenue: String?): String? {
        val requested = normalizeVenue(requestedVenue) ?: return null
        val existing = distinctVenues(existingVenues)
        if (existing.isEmpty()) return null
        if (existing.any { it.equals(requested, ignoreCase = true) }) return null
        val shown = existing.joinToString(", ")
        return "This supplier already has venue $shown. Only one venue per supplier is allowed."
    }

    /**
     * @param rows pairs of (auctionName, venueId) — blank auction names ignored
     */
    fun conflictSuppliers(rows: Collection<Pair<String?, String?>>): List<VenueConflict> {
        data class Acc(val displayName: String, val venues: MutableSet<String>, var blankCount: Int)
        val byKey = linkedMapOf<String, Acc>()
        for ((auctionRaw, venueRaw) in rows) {
            val auction = auctionRaw?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val key = auction.lowercase()
            val acc = byKey.getOrPut(key) { Acc(displayName = auction, venues = linkedSetOf(), blankCount = 0) }
            val venue = normalizeVenue(venueRaw)
            if (venue == null) acc.blankCount++
            else if (acc.venues.none { it.equals(venue, ignoreCase = true) }) acc.venues.add(venue)
        }
        return byKey.values
            .filter { it.venues.size > 1 }
            .map {
                VenueConflict(
                    auctionName = it.displayName,
                    venues = it.venues.sortedBy { v -> v.lowercase() },
                    blankVenueRowCount = it.blankCount,
                )
            }
            .sortedBy { it.auctionName.lowercase() }
    }
}

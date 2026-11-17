package com.iset.fastcolloc

import com.iset.fastcolloc.data.ListingDraft
import org.junit.Assert.*
import org.junit.Test

class ListingDraftTest {
    private fun fields() =
        mapOf(
            "title" to "Une chambre en colocation",
            "description" to "Une vraie description assez longue pour une annonce.",
            "street" to "Rue test",
            "latitude" to "36.8",
            "longitude" to "10.18",
            "rent" to "350,125",
            "capacity" to "2",
            "propertyType" to "Appartement",
            "roommateType" to "Étudiant",
            "equipment" to "WiFi, Cuisine",
            "availableFrom" to "2026-11-16",
            "availableUntil" to "2026-12-31",
            "publicationDays" to "30",
        )

    @Test
    fun validOfferAndRequestKeepThreeDecimalRentAndVersion() {
        for (kind in listOf("OFFER", "REQUEST")) {
            val row = ListingDraft.parse(fields(), kind, 1, 8)
            assertEquals(350.125, row.rent, 0.0)
            assertEquals(listOf("WiFi", "Cuisine"), row.equipment)
            assertEquals(8L, row.version)
            assertEquals(kind, row.kind)
        }
    }

    @Test
    fun invalidDatesNumbersAndLocationAreRejected() {
        for (change in
            listOf(
                "availableFrom" to "2026-02-30",
                "availableUntil" to "2026-01-01",
                "rent" to "350.1234",
                "latitude" to "NaN",
                "longitude" to "20",
                "capacity" to "0",
                "publicationDays" to "366",
                "description" to "short",
            )) {
            assertThrows(IllegalArgumentException::class.java) {
                ListingDraft.parse(fields() + change, "OFFER", 1, null)
            }
        }
    }

    @Test
    fun missingLocalityAndInventedRoleKindsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ListingDraft.parse(fields(), "OWNER", 1, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ListingDraft.parse(fields(), "OFFER", 0, null)
        }
    }
}

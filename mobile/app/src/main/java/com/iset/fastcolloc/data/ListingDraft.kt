package com.iset.fastcolloc.data

import com.iset.fastcolloc.data.remote.ListingWriteDto
import java.text.SimpleDateFormat
import java.util.Locale

/** Presentation validation, independent of Android Views and testable on the JVM. */
object ListingDraft {
    fun parse(
        fields: Map<String, String>,
        kind: String,
        locality: Long,
        version: Long?,
    ): ListingWriteDto {
        fun value(key: String) = fields[key].orEmpty().trim()
        fun number(key: String) =
            value(key).replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: throw IllegalArgumentException("Valeur numérique invalide : $key")
        fun date(key: String): String {
            val text = value(key)
            require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(text)) {
                "Date au format AAAA-MM-JJ requise."
            }
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
            require(runCatching { parser.parse(text) }.getOrNull() != null) { "Date invalide." }
            return text
        }
        require(kind in listOf("OFFER", "REQUEST")) { "Choisis une offre ou une demande." }
        require(locality > 0) { "Choisis une localité." }
        val title = value("title")
        require(title.length in 5..160) { "Le titre doit contenir 5 à 160 caractères." }
        val description = value("description")
        require(description.length in 20..10000) {
            "La description doit contenir au moins 20 caractères."
        }
        require(kind == "REQUEST" || value("street").isNotBlank()) { "L’adresse ou le secteur est obligatoire." }
        require(value("propertyType").isNotBlank()) { "Le type de logement est obligatoire." }
        val lat = if (value("latitude").isBlank() && kind == "REQUEST") null else number("latitude")
        val lng = if (value("longitude").isBlank() && kind == "REQUEST") null else number("longitude")
        require((lat == null) == (lng == null) && (lat == null || lat in 30.0..38.0) && (lng == null || lng in 7.0..12.0)) { "Position hors de la Tunisie." }
        val rent = number("rent")
        require(
            rent in 0.0..999999999.999 &&
                Regex("\\d+(\\.\\d{1,3})?").matches(value("rent").replace(',', '.'))
        ) {
            "Loyer positif, au maximum trois décimales."
        }
        val capacity = value("capacity").toIntOrNull() ?: 0
        require(capacity in 1..50) { "Capacité entre 1 et 50 personnes." }
        val days = value("publicationDays").toIntOrNull() ?: 0
        require(days in 1..365) { "Durée entre 1 et 365 jours." }
        val from = date("availableFrom")
        val until = value("availableUntil").ifBlank { null }?.let { date("availableUntil") }
        require(until == null || until >= from) { "La fin doit suivre le début de disponibilité." }
        val equipment = value("equipment").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        require(equipment.size <= 30 && equipment.all { it.length <= 80 }) {
            "Au maximum 30 équipements de 80 caractères."
        }
        return ListingWriteDto(
            kind,
            title,
            description,
            locality,
            value("street"),
            lat,
            lng,
            rent,
            capacity,
            value("propertyType"),
            value("roommateType"),
            equipment,
            from,
            until,
            days,
            version,
        )
    }
}

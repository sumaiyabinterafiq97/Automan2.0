package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.sql.Types

/**
 * Safe merge of Excel All RIXO master into [rixo_mapping] for production.
 *
 * - INSERT rows that do not already match (normalized business key)
 * - UPDATE only blank / unusable fields on matches (never overwrite non-blank prices/venues)
 * - NEVER DELETE existing rows (client-added mappings are preserved)
 * - Skips Excel ASK / blank prices (those rows are absent from the CSV resource)
 *
 * Source: classpath resource `db/migration/data/V74_rixo_all_rixo.csv`
 * (generated from "RIXO PRICES for NEW AUTOMAN.xlsx" → All RIXO).
 */
@Suppress("unused", "ClassName")
class V74__RixoMappingExcelSafeMerge : BaseJavaMigration() {

    private data class ExcelRow(
        val company: String,
        val auction: String,
        val stock: String,
        val venueId: String,
        val pol: String,
        val vtype: String,
        val priceNum: Int,
    )

    private data class DbRow(
        val id: Long,
        val company: String,
        val auction: String,
        val stock: String,
        val venueId: String,
        val pol: String,
        val vtype: String,
        val priceRaw: String?,
    )

    override fun migrate(context: Context) {
        val conn = context.connection
        if (!tableExists(conn, "rixo_mapping")) return

        val excelRows = loadExcelCsv()
        if (excelRows.isEmpty()) return

        val existing = mutableListOf<DbRow>()
        conn.createStatement().use { st ->
            st.executeQuery(
                """
                SELECT id, rixo_company, auction_name, stock_location, venue_id, pol,
                       supported_vehicle_type, rixo_price
                FROM rixo_mapping
                """.trimIndent(),
            ).use { rs ->
                while (rs.next()) {
                    existing.add(
                        DbRow(
                            id = rs.getLong("id"),
                            company = rs.getString("rixo_company").orEmpty(),
                            auction = rs.getString("auction_name").orEmpty(),
                            stock = rs.getString("stock_location").orEmpty(),
                            venueId = rs.getString("venue_id").orEmpty(),
                            pol = rs.getString("pol").orEmpty(),
                            vtype = rs.getString("supported_vehicle_type").orEmpty(),
                            priceRaw = rs.getString("rixo_price"),
                        ),
                    )
                }
            }
        }

        val byFull = existing
            .groupBy { softKey(it.auction, it.stock, it.company, it.pol, it.venueId, it.vtype, true) }
            .mapValues { it.value.toMutableList() }
            .toMutableMap()
        val byNoVenue = existing
            .groupBy { softKey(it.auction, it.stock, it.company, it.pol, "", it.vtype, false) }
            .mapValues { it.value.toMutableList() }
            .toMutableMap()

        conn.prepareStatement(
            """
            INSERT INTO rixo_mapping
                (rixo_company, auction_name, stock_location, venue_id, pol,
                 supported_vehicle_type, rixo_price, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(),
        ).use { insertPs ->
            conn.prepareStatement(
                """
                UPDATE rixo_mapping
                SET rixo_company = ?,
                    auction_name = ?,
                    stock_location = ?,
                    venue_id = ?,
                    pol = ?,
                    rixo_price = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { updatePs ->
                for (e in excelRows) {
                    val match = findMatch(e, byFull, byNoVenue)
                    if (match == null) {
                        insertPs.setString(1, e.company)
                        insertPs.setString(2, e.auction)
                        insertPs.setString(3, e.stock)
                        setNullable(insertPs, 4, e.venueId.ifBlank { null })
                        setNullable(insertPs, 5, e.pol.ifBlank { null })
                        setNullable(insertPs, 6, e.vtype.ifBlank { null })
                        insertPs.setString(7, formatPrice(e.priceNum))
                        insertPs.executeUpdate()
                        // Keep indexes coherent for later excel rows in same migration
                        val inserted = DbRow(
                            id = -1L,
                            company = e.company,
                            auction = e.auction,
                            stock = e.stock,
                            venueId = e.venueId,
                            pol = e.pol,
                            vtype = e.vtype,
                            priceRaw = formatPrice(e.priceNum),
                        )
                        val fk = softKey(e.auction, e.stock, e.company, e.pol, e.venueId, e.vtype, true)
                        val nk = softKey(e.auction, e.stock, e.company, e.pol, "", e.vtype, false)
                        byFull.getOrPut(fk) { mutableListOf() }.add(inserted)
                        byNoVenue.getOrPut(nk) { mutableListOf() }.add(inserted)
                        continue
                    }

                    val sets = blankFieldUpdates(match, e) ?: continue
                    updatePs.setString(1, sets.company)
                    updatePs.setString(2, sets.auction)
                    updatePs.setString(3, sets.stock)
                    setNullable(updatePs, 4, sets.venueId.ifBlank { null })
                    setNullable(updatePs, 5, sets.pol.ifBlank { null })
                    setNullable(updatePs, 6, sets.priceRaw)
                    updatePs.setLong(7, match.id)
                    updatePs.executeUpdate()
                }
            }
        }
    }

    private fun findMatch(
        e: ExcelRow,
        byFull: Map<String, MutableList<DbRow>>,
        byNoVenue: Map<String, MutableList<DbRow>>,
    ): DbRow? {
        val full = softKey(e.auction, e.stock, e.company, e.pol, e.venueId, e.vtype, true)
        byFull[full]?.firstOrNull()?.let { return it }

        val noVenue = softKey(e.auction, e.stock, e.company, e.pol, "", e.vtype, false)
        val cands = byNoVenue[noVenue].orEmpty()
        if (cands.isEmpty()) return null
        if (e.venueId.isBlank()) return cands.first()
        return cands.firstOrNull { it.venueId.isBlank() || it.venueId == e.venueId } ?: cands.first()
    }

    private data class UpdateFields(
        val company: String,
        val auction: String,
        val stock: String,
        val venueId: String,
        val pol: String,
        val priceRaw: String?,
    )

    /** Returns null when nothing blank needs filling. Never overwrites usable non-blank price/venue. */
    private fun blankFieldUpdates(db: DbRow, e: ExcelRow): UpdateFields? {
        var company = db.company
        var auction = db.auction
        var stock = db.stock
        var venueId = db.venueId
        var pol = db.pol
        var priceRaw = db.priceRaw
        var changed = false

        if (isBlankCompany(db.company)) {
            company = e.company
            changed = true
        }
        if (db.auction.isBlank() && e.auction.isNotBlank()) {
            auction = e.auction
            changed = true
        }
        if (db.stock.isBlank() && e.stock.isNotBlank()) {
            stock = e.stock
            changed = true
        }
        if (db.pol.isBlank() && e.pol.isNotBlank()) {
            pol = e.pol
            changed = true
        }
        if (db.venueId.isBlank() && e.venueId.isNotBlank()) {
            venueId = e.venueId
            changed = true
        }
        if (parsePrice(db.priceRaw) == null) {
            priceRaw = formatPrice(e.priceNum)
            changed = true
        }

        return if (changed) {
            UpdateFields(company, auction, stock, venueId, pol, priceRaw)
        } else {
            null
        }
    }

    private fun loadExcelCsv(): List<ExcelRow> {
        val stream = javaClass.classLoader.getResourceAsStream("db/migration/data/V74_rixo_all_rixo.csv")
            ?: error("Missing classpath resource db/migration/data/V74_rixo_all_rixo.csv")
        return stream.use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                val header = reader.readLine() ?: return emptyList()
                require(header.startsWith("rixo_company")) { "Unexpected CSV header: $header" }
                val out = mutableListOf<ExcelRow>()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val cols = parseCsvLine(line!!)
                    if (cols.size < 7) continue
                    val priceNum = cols[6].trim().toIntOrNull() ?: continue
                    out.add(
                        ExcelRow(
                            company = cols[0].trim(),
                            auction = cols[1].trim(),
                            stock = cols[2].trim(),
                            venueId = cols[3].trim(),
                            pol = cols[4].trim(),
                            vtype = cols[5].trim(),
                            priceNum = priceNum,
                        ),
                    )
                }
                out
            }
        }
    }

    /** Minimal CSV parser for our simple generated file (no embedded newlines). */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    cur.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(cur.toString())
                    cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        result.add(cur.toString())
        return result
    }

    private fun softKey(
        auction: String,
        stock: String,
        company: String,
        pol: String,
        venue: String,
        vtype: String,
        includeVenue: Boolean,
    ): String {
        val parts = mutableListOf(
            auction.trim().uppercase(),
            stock.trim().uppercase(),
            normCompany(company),
            polFamily(pol),
            vtypeKey(vtype),
        )
        if (includeVenue) parts.add(venue.trim())
        return parts.joinToString("\u0001")
    }

    private fun normCompany(c: String): String {
        val u = c.trim().uppercase().replace('\u2019', '\'').replace('\u2018', '\'')
        return when (u) {
            "STYLISH", "STYLISH AUTO" -> "STYLISH"
            "Y'S", "YS", "Y`S" -> "Y'S"
            else -> u
        }
    }

    private fun polFamily(p: String): String {
        val u = p.trim().uppercase()
        return if (u == "SENBOKU (OSAKA)" || u == "SENBOKU" || u == "OSAKA") "SENBOKU_OSAKA" else u
    }

    private fun vtypeKey(v: String): String {
        val t = v.trim().uppercase()
        return if (t.isEmpty() || t == "-" || t == "—" || t == "–") "" else t
    }

    private fun isBlankCompany(c: String): Boolean {
        val u = c.trim()
        return u.isEmpty() || u == "-" || u == "—" || u == "–"
    }

    private fun parsePrice(p: String?): Int? {
        if (p.isNullOrBlank()) return null
        val s = p.replace("\uFFFD", "")
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace(" ", "")
            .trim()
        if (s.isEmpty() || s == "-" || s.equals("ASK", ignoreCase = true)) return null
        return s.toDoubleOrNull()?.let { Math.round(it).toInt() }
    }

    private fun formatPrice(n: Int): String = "¥%,d".format(n)

    private fun setNullable(ps: java.sql.PreparedStatement, index: Int, value: String?) {
        if (value.isNullOrBlank()) ps.setNull(index, Types.VARCHAR) else ps.setString(index, value)
    }

    private fun tableExists(conn: java.sql.Connection, table: String): Boolean {
        conn.metaData.getTables(null, null, table, arrayOf("TABLE")).use { rs ->
            return rs.next()
        }
    }
}

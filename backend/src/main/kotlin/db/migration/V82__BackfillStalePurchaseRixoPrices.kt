package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * Align stored purchase RIXO_PRICE cost lines with current [rixo_mapping] prices
 * when supplier path matches (auction / stock / rixo company / venue / pol).
 * Also applies the same delta to [purchases.total_price].
 */
@Suppress("unused", "ClassName")
class V82__BackfillStalePurchaseRixoPrices : BaseJavaMigration() {

    private data class MappingRow(
        val auction: String,
        val stock: String,
        val company: String,
        val venueId: String,
        val pol: String,
        val vtype: String,
        val amount: BigDecimal,
    )

    private data class PurchaseRow(
        val id: Long,
        val auction: String,
        val stock: String,
        val company: String,
        val pol: String,
        val venueId: String,
        val shipmentSize: String,
        val totalPrice: String?,
        val rixoAmount: BigDecimal?,
    )

    override fun migrate(context: Context) {
        val conn = context.connection
        val mappings = mutableListOf<MappingRow>()
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT auction_name, stock_location, rixo_company, venue_id, pol, supported_vehicle_type, rixo_price FROM rixo_mapping",
            ).use { rs ->
                while (rs.next()) {
                    val amount = parseMoney(rs.getString("rixo_price")) ?: continue
                    mappings.add(
                        MappingRow(
                            auction = rs.getString("auction_name").orEmpty().trim(),
                            stock = rs.getString("stock_location").orEmpty().trim(),
                            company = rs.getString("rixo_company").orEmpty().trim(),
                            venueId = rs.getString("venue_id").orEmpty().trim(),
                            pol = rs.getString("pol").orEmpty().trim(),
                            vtype = rs.getString("supported_vehicle_type").orEmpty().trim(),
                            amount = amount,
                        ),
                    )
                }
            }
        }
        if (mappings.isEmpty()) return

        val venueByPurchase = mutableMapOf<Long, String>()
        val shipmentByPurchase = mutableMapOf<Long, String>()
        runCatching {
            conn.createStatement().use { st ->
                st.executeQuery("SELECT purchase_id, overrides_json FROM purchase_vehicle_overrides").use { rs ->
                    while (rs.next()) {
                        val pid = rs.getLong(1)
                        shipmentByPurchase[pid] = jsonStringField(rs.getString(2), "shipmentSize")
                    }
                }
            }
        }

        val purchases = mutableListOf<PurchaseRow>()
        conn.createStatement().use { st ->
            st.executeQuery(
                """
                SELECT p.id, p.auction_house, p.stock_location, p.rixo_company, p.pol, p.total_price,
                       p.extended_attributes, pcl.amount
                FROM purchases p
                LEFT JOIN purchase_cost_lines pcl ON pcl.purchase_id = p.id AND pcl.cost_code = 'RIXO_PRICE'
                """.trimIndent(),
            ).use { rs ->
                while (rs.next()) {
                    val id = rs.getLong("id")
                    val json = rs.getString("extended_attributes")
                    val rixoAmt = rs.getBigDecimal("amount")
                    purchases.add(
                        PurchaseRow(
                            id = id,
                            auction = rs.getString("auction_house").orEmpty().trim(),
                            stock = rs.getString("stock_location").orEmpty().trim(),
                            company = rs.getString("rixo_company").orEmpty().trim(),
                            pol = rs.getString("pol").orEmpty().trim(),
                            venueId = jsonStringField(json, "venueId").ifBlank { venueByPurchase[id].orEmpty() },
                            shipmentSize = shipmentByPurchase[id].orEmpty(),
                            totalPrice = rs.getString("total_price"),
                            rixoAmount = rixoAmt,
                        ),
                    )
                }
            }
        }

        val now = Timestamp.valueOf(LocalDateTime.now())
        conn.prepareStatement(
            "UPDATE purchase_cost_lines SET amount = ?, updated_at = ? WHERE purchase_id = ? AND cost_code = 'RIXO_PRICE'",
        ).use { updateLine ->
            conn.prepareStatement(
                "INSERT INTO purchase_cost_lines (purchase_id, cost_code, amount, sort_order, created_at, updated_at) VALUES (?, 'RIXO_PRICE', ?, 14, ?, ?)",
            ).use { insertLine ->
                conn.prepareStatement("UPDATE purchases SET total_price = ? WHERE id = ?").use { updateTotal ->
                    for (p in purchases) {
                        val mapping = mappings.firstOrNull { matches(p, it) } ?: continue
                        val oldAmt = p.rixoAmount
                        if (oldAmt != null && oldAmt.compareTo(mapping.amount) == 0) continue
                        if (oldAmt != null) {
                            updateLine.setBigDecimal(1, mapping.amount)
                            updateLine.setTimestamp(2, now)
                            updateLine.setLong(3, p.id)
                            updateLine.executeUpdate()
                        } else {
                            insertLine.setLong(1, p.id)
                            insertLine.setBigDecimal(2, mapping.amount)
                            insertLine.setTimestamp(3, now)
                            insertLine.setTimestamp(4, now)
                            insertLine.executeUpdate()
                        }
                        val oldForDelta = oldAmt ?: BigDecimal.ZERO
                        val total = parseMoney(p.totalPrice)
                        if (total != null) {
                            val newTotal = total.add(mapping.amount.subtract(oldForDelta))
                            updateTotal.setString(1, newTotal.stripTrailingZeros().toPlainString())
                            updateTotal.setLong(2, p.id)
                            updateTotal.executeUpdate()
                        }
                    }
                }
            }
        }
    }

    private fun matches(p: PurchaseRow, m: MappingRow): Boolean {
        if (!tokenMatch(p.auction, m.auction)) return false
        if (!tokenMatch(p.stock, m.stock)) return false
        if (!tokenMatch(p.company, m.company)) return false
        if (m.venueId.isNotEmpty() && !tokenMatch(p.venueId, m.venueId)) return false
        if (m.pol.isNotEmpty() && !tokenMatch(p.pol, m.pol)) return false
        if (m.vtype.isNotEmpty() && m.vtype != "-" && !tokenMatch(p.shipmentSize, m.vtype)) return false
        return true
    }

    private fun tokenMatch(rowVal: String, selVal: String): Boolean {
        val sel = selVal.trim()
        if (sel.isEmpty()) return true
        val tokens = rowVal.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { if (rowVal.trim().isEmpty()) emptyList() else listOf(rowVal.trim()) }
        val want = sel.lowercase()
        return tokens.any { it.lowercase() == want }
    }

    private fun parseMoney(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace(",", "").replace("¥", "").replace("Â¥", "").trim()
        if (cleaned.isEmpty()) return null
        return try {
            BigDecimal(cleaned)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun jsonStringField(json: String?, key: String): String {
        if (json.isNullOrBlank()) return ""
        val m = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]*)\"").find(json)
        return m?.groupValues?.get(1)?.trim().orEmpty()
    }
}

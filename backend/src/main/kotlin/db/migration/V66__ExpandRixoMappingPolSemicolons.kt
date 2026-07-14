package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Types

/**
 * Surgical expand: `rixo_mapping.pol` values containing `;` become one row per token.
 * Does **not** truncate the table. Single-POL rows (including client-added) are untouched.
 */
@Suppress("unused", "ClassName")
class V66__ExpandRixoMappingPolSemicolons : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val conn = context.connection
        if (!tableExists(conn, "rixo_mapping")) return

        data class Row(
            val id: Long,
            val rixoCompany: String,
            val auctionName: String?,
            val stockLocation: String,
            val venueId: String?,
            val pol: String?,
            val supportedVehicleType: String?,
            val rixoPrice: String?,
        )

        val all = mutableListOf<Row>()
        conn.createStatement().use { st ->
            st.executeQuery(
                """
                SELECT id, rixo_company, auction_name, stock_location, venue_id, pol,
                       supported_vehicle_type, rixo_price
                FROM rixo_mapping
                ORDER BY id ASC
                """.trimIndent(),
            ).use { rs ->
                while (rs.next()) {
                    all.add(
                        Row(
                            id = rs.getLong("id"),
                            rixoCompany = rs.getString("rixo_company").orEmpty(),
                            auctionName = rs.getString("auction_name"),
                            stockLocation = rs.getString("stock_location").orEmpty(),
                            venueId = rs.getString("venue_id"),
                            pol = rs.getString("pol"),
                            supportedVehicleType = rs.getString("supported_vehicle_type"),
                            rixoPrice = rs.getString("rixo_price"),
                        ),
                    )
                }
            }
        }

        fun keyOf(
            company: String,
            auction: String?,
            stock: String,
            venue: String?,
            pol: String?,
            vtype: String?,
            price: String?,
        ): String {
            fun n(s: String?) = s?.trim()?.lowercase().orEmpty()
            return listOf(n(company), n(auction), n(stock), n(venue), n(pol), n(vtype), n(price))
                .joinToString("\u0001")
        }

        val keySet = all.map {
            keyOf(it.rixoCompany, it.auctionName, it.stockLocation, it.venueId, it.pol, it.supportedVehicleType, it.rixoPrice)
        }.toMutableSet()

        val multi = all.filter { splitTokens(it.pol).size >= 2 }
        if (multi.isEmpty()) return

        conn.prepareStatement(
            """
            INSERT INTO rixo_mapping
                (rixo_company, auction_name, stock_location, venue_id, pol,
                 supported_vehicle_type, rixo_price, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(),
        ).use { insertPs ->
            conn.prepareStatement("DELETE FROM rixo_mapping WHERE id = ?").use { deletePs ->
                for (source in multi) {
                    val tokens = splitTokens(source.pol)
                    var ensured = false
                    for (token in tokens) {
                        val key = keyOf(
                            source.rixoCompany,
                            source.auctionName,
                            source.stockLocation,
                            source.venueId,
                            token,
                            source.supportedVehicleType,
                            source.rixoPrice,
                        )
                        if (keySet.contains(key)) {
                            ensured = true
                            continue
                        }
                        insertPs.setString(1, source.rixoCompany)
                        setNullable(insertPs, 2, source.auctionName)
                        insertPs.setString(3, source.stockLocation)
                        setNullable(insertPs, 4, source.venueId)
                        insertPs.setString(5, token)
                        setNullable(insertPs, 6, source.supportedVehicleType)
                        setNullable(insertPs, 7, source.rixoPrice)
                        insertPs.executeUpdate()
                        keySet.add(key)
                        ensured = true
                    }
                    if (ensured) {
                        deletePs.setLong(1, source.id)
                        deletePs.executeUpdate()
                        keySet.remove(
                            keyOf(
                                source.rixoCompany,
                                source.auctionName,
                                source.stockLocation,
                                source.venueId,
                                source.pol,
                                source.supportedVehicleType,
                                source.rixoPrice,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun splitTokens(raw: String?): List<String> {
        if (raw == null) return emptyList()
        val n = raw.replace('\uFF1B', ';').replace('\uFE55', ';')
        return n.split(';').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun setNullable(ps: java.sql.PreparedStatement, index: Int, value: String?) {
        if (value.isNullOrBlank()) ps.setNull(index, Types.VARCHAR) else ps.setString(index, value)
    }

    private fun tableExists(conn: java.sql.Connection, table: String): Boolean {
        conn.metaData.getTables(null, null, table, arrayOf("TABLE")).use { rs ->
            return rs.next()
        }
    }
}

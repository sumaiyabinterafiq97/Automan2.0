package db.migration

import com.automan.backend.util.RixoPolFromStockLocation
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Types

/**
 * Expands [rixo_prices] semicolon rows into normalized [rixo_mapping] rows, backfills venue/POL,
 * then drops [rixo_prices].
 */
@Suppress("unused", "ClassName")
class V63__MergeRixoPricesIntoRixoMapping : BaseJavaMigration() {

    private data class PriceBranch(
        val auction: String,
        val stock: String,
        val rixo: String,
        val venue: String?,
        val pol: String?,
    )

    override fun migrate(context: Context) {
        val conn = context.connection
        if (!tableExists(conn, "rixo_prices")) return

        val priceRows = mutableListOf<Quintuple>()
        conn.createStatement().use { st ->
            st.executeQuery(
                """
                SELECT auction_name, stock_location, rixo_company, venue_id, pol
                FROM rixo_prices
                ORDER BY id ASC
                """.trimIndent(),
            ).use { rs ->
                while (rs.next()) {
                    priceRows.add(
                        Quintuple(
                            auction = rs.getString("auction_name").orEmpty(),
                            stock = rs.getString("stock_location").orEmpty(),
                            rixo = rs.getString("rixo_company").orEmpty(),
                            venue = rs.getString("venue_id"),
                            pol = rs.getString("pol"),
                        ),
                    )
                }
            }
        }

        val branches = priceRows.flatMap { expandPriceRow(it.auction, it.stock, it.rixo, it.venue, it.pol) }

        conn.prepareStatement(
            """
            SELECT id, venue_id, pol
            FROM rixo_mapping
            WHERE LOWER(TRIM(COALESCE(auction_name, ''))) = LOWER(TRIM(?))
              AND LOWER(TRIM(stock_location)) = LOWER(TRIM(?))
              AND LOWER(TRIM(rixo_company)) = LOWER(TRIM(?))
            """.trimIndent(),
        ).use { findPs ->
            conn.prepareStatement(
                """
                UPDATE rixo_mapping
                SET venue_id = COALESCE(NULLIF(TRIM(venue_id), ''), ?),
                    pol = COALESCE(NULLIF(TRIM(pol), ''), ?)
                WHERE id = ?
                """.trimIndent(),
            ).use { updatePs ->
                conn.prepareStatement(
                    """
                    INSERT INTO rixo_mapping
                        (rixo_company, auction_name, stock_location, venue_id, pol,
                         supported_vehicle_type, rixo_price, created_at)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, CURRENT_TIMESTAMP)
                    """.trimIndent(),
                ).use { insertPs ->
                    for (branch in branches) {
                        findPs.setString(1, branch.auction)
                        findPs.setString(2, branch.stock)
                        findPs.setString(3, branch.rixo)
                        val matches = mutableListOf<Pair<Long, String?>>()
                        findPs.executeQuery().use { rs ->
                            while (rs.next()) {
                                matches.add(rs.getLong("id") to rs.getString("venue_id"))
                            }
                        }
                        if (matches.isEmpty()) {
                            insertPs.setString(1, branch.rixo)
                            insertPs.setString(2, branch.auction)
                            insertPs.setString(3, branch.stock)
                            setNullableString(insertPs, 4, branch.venue)
                            setNullableString(insertPs, 5, branch.pol)
                            insertPs.executeUpdate()
                        } else {
                            for ((id, _) in matches) {
                                updatePs.setString(1, branch.venue)
                                updatePs.setString(2, branch.pol)
                                updatePs.setLong(3, id)
                                updatePs.executeUpdate()
                            }
                        }
                    }
                }
            }
        }

        conn.createStatement().use { st ->
            st.execute("DROP TABLE IF EXISTS rixo_prices")
        }
    }

    private fun setNullableString(ps: java.sql.PreparedStatement, index: Int, value: String?) {
        if (value.isNullOrBlank()) {
            ps.setNull(index, Types.VARCHAR)
        } else {
            ps.setString(index, value)
        }
    }

    private fun tableExists(conn: java.sql.Connection, table: String): Boolean {
        conn.metaData.getTables(null, null, table, arrayOf("TABLE")).use { rs ->
            return rs.next()
        }
    }

    private data class Quintuple(
        val auction: String,
        val stock: String,
        val rixo: String,
        val venue: String?,
        val pol: String?,
    )

    private fun semicolonTokens(raw: String?): List<String> =
        raw.orEmpty().split(';', ',').map { it.trim() }.filter { it.isNotEmpty() && it != "-" }

    private fun splitRixoInSegment(seg: String): List<String> =
        seg.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() && it != "-" }

    private fun computeRixoLeaves(n: Int, rixosAll: List<String>, stockBranchIdx: Int): List<String> {
        if (n <= 1) {
            val leaves = rixosAll.flatMap { splitRixoInSegment(it) }
                .filter { it.isNotBlank() && it != "-" }
            return if (leaves.isEmpty()) listOf("-") else leaves
        }
        val single = rixosAll.getOrElse(stockBranchIdx) { "" }.trim()
        return listOf(if (single.isBlank() || single == "-") "-" else single)
    }

    private fun expandPriceRow(
        auction: String,
        stock: String,
        rixo: String,
        venue: String?,
        pol: String?,
    ): List<PriceBranch> {
        val stocksRaw = semicolonTokens(stock)
        val stocks = stocksRaw.filter { it.isNotBlank() && it != "-" }
        val stockBranches = if (stocks.isEmpty()) listOf("-") else stocks
        val venues = semicolonTokens(venue)
        val pols = semicolonTokens(pol)
        val rixosAll = semicolonTokens(rixo)
        val n = stockBranches.size
        val out = mutableListOf<PriceBranch>()
        for (i in 0 until n) {
            val stTok = stockBranches[i]
            val venueTok = venues.getOrNull(i)?.trim().orEmpty()
                .ifBlank { venues.firstOrNull().orEmpty() }
                .takeIf { it.isNotBlank() }
            val polTok = pols.getOrNull(i)?.trim().orEmpty()
                .ifBlank { pols.firstOrNull()?.trim().orEmpty().orEmpty() }
                .ifBlank { RixoPolFromStockLocation.derivePol(stTok).orEmpty() }
                .takeIf { it.isNotBlank() }
            val rixoOptions = computeRixoLeaves(n, rixosAll, i)
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "-" }
            val rixoList = if (rixoOptions.isEmpty()) listOf("-") else rixoOptions
            for (r in rixoList) {
                out.add(
                    PriceBranch(
                        auction = auction.trim(),
                        stock = stTok,
                        rixo = r,
                        venue = venueTok,
                        pol = polTok,
                    ),
                )
            }
        }
        return out
    }
}

package db.migration

import com.automan.backend.util.RixoPolFromStockLocation
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Types

/**
 * Sets [rixo_prices.pol] from [rixo_prices.stock_location] using [RixoPolFromStockLocation].
 */
@Suppress("unused", "ClassName")
class V8__BackfillRixoPolFromStockLocation : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val conn = context.connection
        val rows = mutableListOf<Pair<Long, String>>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT id, stock_location FROM rixo_prices").use { rs ->
                while (rs.next()) {
                    rows.add(rs.getLong(1) to (rs.getString(2) ?: ""))
                }
            }
        }
        conn.prepareStatement("UPDATE rixo_prices SET pol = ? WHERE id = ?").use { ps ->
            for ((id, stock) in rows) {
                val pol = RixoPolFromStockLocation.derivePol(stock)
                if (pol == null) {
                    ps.setNull(1, Types.VARCHAR)
                } else {
                    ps.setString(1, pol)
                }
                ps.setLong(2, id)
                ps.executeUpdate()
            }
        }
    }
}

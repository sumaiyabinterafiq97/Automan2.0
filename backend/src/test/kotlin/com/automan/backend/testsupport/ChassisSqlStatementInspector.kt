package com.automan.backend.testsupport

import org.hibernate.resource.jdbc.spi.StatementInspector

/**
 * Test-only Hibernate [StatementInspector]. Counts JDBC statements per thread.
 * Enable via `spring.jpa.properties.hibernate.session_factory.statement_inspector`.
 */
class ChassisSqlStatementInspector : StatementInspector {

    override fun inspect(sql: String): String {
        statements().add(sql)
        return sql
    }

    companion object {
        private val holder: ThreadLocal<MutableList<String>> =
            ThreadLocal.withInitial { mutableListOf() }

        fun reset() {
            holder.get().clear()
        }

        fun statements(): MutableList<String> = holder.get()

        fun countStatements(matcher: (String) -> Boolean): Int =
            statements().count { matcher(it) }

        fun countPurchaseChassisLookups(): Int =
            countStatements { sql ->
                val n = sql.lowercase()
                (n.contains(" from purchases") || n.contains(" from purchase ")) &&
                    (n.contains("chassis_key") || n.contains("chassis "))
            }

        /** Batched equality lookups (`chassis_key in (...)`). */
        fun countPurchaseChassisKeyInQueries(): Int =
            countStatements { sql ->
                val n = sql.lowercase()
                n.contains("chassis_key") && n.contains(" in (")
            }

        fun countCarBrandMappingQueries(): Int =
            countStatements { sql ->
                val n = sql.lowercase()
                n.contains("car_brand_mapping") || n.contains("carbrandmapping")
            }
    }
}

import java.io.File
import java.sql.DriverManager

fun main() {
    val url = "jdbc:mysql://localhost:3307/automan_car_purchase?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
    val username = "automan_user"
    val password = "automan_password"
    
    val connection = DriverManager.getConnection(url, username, password)
    
    try {
        val sqlFile = File("backend/src/main/resources/db/migration/V24__Create_car_brand_mapping_table.sql")
        val sqlContent = sqlFile.readText()
        
        // Split by semicolon and execute each statement
        val statements = sqlContent.split(";").map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("--") }
        
        println("Executing ${statements.size} SQL statements...")
        
        var executed = 0
        statements.forEach { statement ->
            if (statement.isNotEmpty()) {
                try {
                    connection.createStatement().execute(statement)
                    executed++
                    if (executed % 50 == 0) {
                        println("Executed $executed statements...")
                    }
                } catch (e: Exception) {
                    if (!e.message?.contains("already exists") == true) {
                        println("Error executing statement: ${e.message}")
                        println("Statement: ${statement.take(100)}...")
                    }
                }
            }
        }
        
        println("Migration completed! Executed $executed statements.")
        
        // Verify the table was created
        val resultSet = connection.createStatement().executeQuery("SELECT COUNT(*) as count FROM car_brand_mapping")
        if (resultSet.next()) {
            println("Table 'car_brand_mapping' contains ${resultSet.getInt("count")} rows")
        }
        
    } finally {
        connection.close()
    }
}


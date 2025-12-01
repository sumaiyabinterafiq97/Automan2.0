#!/usr/bin/env python3
import sys
import re

try:
    import mysql.connector
    from mysql.connector import Error
except ImportError:
    print("mysql-connector-python not available. Installing...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "mysql-connector-python", "-q"])
    import mysql.connector
    from mysql.connector import Error

def execute_migration():
    try:
        # Connect to MySQL
        connection = mysql.connector.connect(
            host='127.0.0.1',
            port=3307,
            user='automan_user',
            password='automan_password',
            database='automan_car_purchase',
            allow_local_infile=True
        )
        
        cursor = connection.cursor()
        
        # Read SQL file
        with open('backend/src/main/resources/db/migration/V24__Create_car_brand_mapping_table.sql', 'r') as f:
            sql_content = f.read()
        
        # Split by "-- Insert data" to separate CREATE TABLE from INSERTs
        parts = sql_content.split("-- Insert data")
        create_part = parts[0].strip()
        insert_part = parts[1].strip() if len(parts) > 1 else ""
        
        # Execute CREATE TABLE statement
        print("Executing CREATE TABLE statement...")
        try:
            # Execute the entire CREATE TABLE block (it's a single statement ending with ;)
            # Remove trailing semicolon and comments
            create_sql = create_part.split(';')[0].strip()
            # Remove comment lines
            create_sql = '\n'.join([line for line in create_sql.split('\n') if not line.strip().startswith('--')])
            cursor.execute(create_sql)
            connection.commit()
            print("✅ CREATE TABLE executed successfully!")
        except Error as e:
            if "already exists" not in str(e).lower():
                print(f"Warning creating table: {e}")
                # Try to continue anyway
        
        # Check if table exists
        cursor.execute("SHOW TABLES LIKE 'car_brand_mapping'")
        if not cursor.fetchone():
            print("ERROR: Table was not created!")
            return
        
        # Execute INSERT statements one by one
        if insert_part:
            # Split INSERT statements - look for lines ending with );
            lines = insert_part.split('\n')
            insert_statements = []
            current = ""
            
            for line in lines:
                line = line.strip()
                if not line or line.startswith('--'):
                    continue
                current += " " + line
                if line.endswith(');'):
                    insert_statements.append(current.strip())
                    current = ""
            
            print(f"Found {len(insert_statements)} INSERT statements to execute...")
            executed = 0
            failed = 0
            
            for i, statement in enumerate(insert_statements):
                if statement:
                    try:
                        cursor.execute(statement)
                        executed += 1
                        if executed % 50 == 0:
                            connection.commit()
                            print(f"  Committed {executed} INSERT statements...")
                    except Error as e:
                        failed += 1
                        if failed <= 5:  # Only show first 5 errors
                            print(f"  Warning on INSERT {i+1}: {str(e)[:100]}")
            
            connection.commit()
            print(f"\n✅ Migration completed!")
            print(f"   - Executed: {executed} INSERT statements")
            if failed > 0:
                print(f"   - Failed: {failed} INSERT statements")
        
        # Verify the table
        cursor.execute("SELECT COUNT(*) as count FROM car_brand_mapping")
        result = cursor.fetchone()
        if result:
            print(f"\n✅ Table 'car_brand_mapping' contains {result[0]} rows")
        
        # Show sample data
        cursor.execute("SELECT car_brand, chassis, car_name, fuel, wd, shift, cc, door, grade FROM car_brand_mapping LIMIT 3")
        rows = cursor.fetchall()
        print("\nSample data:")
        for row in rows:
            print(f"  {row}")
        
        cursor.close()
        connection.close()
        
    except Error as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    execute_migration()

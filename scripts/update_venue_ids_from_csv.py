#!/usr/bin/env python3
"""
Script to update venue_id in rixo_prices table from CSV file.
Matches rows even when stock_location is missing ("-") by using other fields.
"""

import csv
import re

def normalize_text(text):
    """Normalize text for matching: uppercase, trim, handle empty/null"""
    if not text or text.strip() == '-' or text.strip() == '':
        return None
    return text.strip().upper()

def normalize_type_of_vehicle(vehicle_type):
    """Normalize vehicle type for matching"""
    if not vehicle_type or vehicle_type.strip() == '-' or vehicle_type.strip() == '':
        return None
    # Normalize variations
    normalized = vehicle_type.strip().upper()
    # Handle variations like "CAR / BIG CAR" vs "CAR/BIG CAR"
    normalized = re.sub(r'\s*/\s*', ' / ', normalized)
    return normalized

def generate_sql_update(csv_file_path):
    """Generate SQL UPDATE statements from CSV file"""
    
    updates = []
    
    with open(csv_file_path, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        header = next(reader)  # Skip header
        
        for row_num, row in enumerate(reader, start=2):
            if len(row) < 6:
                continue
            
            auction_house = row[0].strip()
            stock_location = row[1].strip()
            venue_id = row[2].strip()
            rixo_company = row[3].strip()
            type_of_vehicle = row[4].strip()
            
            # Skip if venue_id is empty
            if not venue_id or venue_id == '-':
                continue
            
            # Normalize values
            auction_house_norm = normalize_text(auction_house)
            stock_location_norm = normalize_text(stock_location)
            rixo_company_norm = normalize_text(rixo_company)
            type_of_vehicle_norm = normalize_type_of_vehicle(type_of_vehicle)
            
            # Require at least auction_name
            if not auction_house_norm:
                continue
            
            # Build WHERE clause
            # Escape single quotes for SQL
            auction_house_escaped = auction_house_norm.replace("'", "''")
            
            where_parts = [
                f"UPPER(TRIM(auction_name)) = '{auction_house_escaped}'"
            ]
            
            # Add rixo_company if not missing
            if rixo_company_norm:
                rixo_company_escaped = rixo_company_norm.replace("'", "''")
                where_parts.append(f"UPPER(TRIM(rixo_company)) = '{rixo_company_escaped}'")
            
            # Add stock_location if not missing
            if stock_location_norm:
                stock_location_escaped = stock_location_norm.replace("'", "''")
                where_parts.append(f"UPPER(TRIM(stock_location)) = '{stock_location_escaped}'")
            
            # Add type_of_vehicle matching (handle NULL/empty and comma-separated values)
            if type_of_vehicle_norm:
                # CSV has a specific type_of_vehicle - match flexibly
                type_of_vehicle_escaped = type_of_vehicle_norm.replace("'", "''")
                # Extract base vehicle type (e.g., "CAR / BIG CAR" -> "CAR", "TRUCK" -> "TRUCK")
                base_vehicle = type_of_vehicle_escaped.split('/')[0].strip()
                # Match if:
                # 1. DB value equals CSV value exactly
                # 2. DB value contains CSV value (for comma-separated like "CAR, TRUCK")
                # 3. DB value contains base vehicle type (for variations like "CAR / BIG CAR" matching "CAR")
                # 4. CSV base type matches DB base type (for "CAR / BIG CAR" matching "Car")
                where_parts.append(
                    f"(UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = '{type_of_vehicle_escaped}' "
                    f"OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%{type_of_vehicle_escaped}%' "
                    f"OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) LIKE '%{base_vehicle}%' "
                    f"OR UPPER(TRIM(COALESCE(type_of_vehicle, ''))) = '{base_vehicle}')"
                )
            else:
                # If CSV has empty type_of_vehicle, match ANY type_of_vehicle in DB (or empty)
                # This allows CSV rows with empty type to match DB rows with any type
                where_parts.append("(1=1)")  # Always true - don't filter by type_of_vehicle
            
            where_clause = " AND ".join(where_parts)
            
            # Escape single quotes in venue_id
            venue_id_escaped = venue_id.replace("'", "''")
            
            # Create primary SQL with all conditions
            sql_primary = f"""UPDATE rixo_prices 
SET venue_id = '{venue_id_escaped}'
WHERE {where_clause};"""
            
            # Also create a fallback SQL without type_of_vehicle constraint
            # (for cases where vehicle types differ but other fields match)
            where_parts_fallback = [p for p in where_parts if 'type_of_vehicle' not in p]
            if len(where_parts_fallback) < len(where_parts):
                where_clause_fallback = " AND ".join(where_parts_fallback)
                sql_fallback = f"""UPDATE rixo_prices 
SET venue_id = '{venue_id_escaped}'
WHERE {where_clause_fallback}
  AND (venue_id IS NULL OR venue_id = '');"""
            else:
                sql_fallback = None
            
            updates.append({
                'row_num': row_num,
                'auction_house': auction_house,
                'venue_id': venue_id,
                'sql': sql_primary,
                'sql_fallback': sql_fallback
            })
    
    return updates

def main():
    csv_file = 'tests/data/Test files/ RIXO PRICE SHEET.csv'
    
    print("Generating SQL UPDATE statements from CSV...")
    updates = generate_sql_update(csv_file)
    
    print(f"\nGenerated {len(updates)} UPDATE statements\n")
    
    # Write to SQL file
    sql_file = 'scripts/sql/update_venue_ids.sql'
    with open(sql_file, 'w', encoding='utf-8') as f:
        f.write("-- Update venue_id in rixo_prices table from CSV\n")
        f.write("-- Generated from: RIXO PRICE SHEET.csv\n")
        f.write("-- Matching strategy: auction_name, rixo_company, stock_location (if not '-'), type_of_vehicle\n\n")
        f.write("USE automan_car_purchase;\n\n")
        
        for update in updates:
            f.write(f"-- Row {update['row_num']}: {update['auction_house']} -> venue_id: {update['venue_id']}\n")
            f.write(update['sql'])
            f.write("\n")
            # Add fallback SQL if available
            if update['sql_fallback']:
                f.write(f"-- Fallback (without type_of_vehicle constraint) for Row {update['row_num']}\n")
                f.write(update['sql_fallback'])
                f.write("\n")
            f.write("\n")
    
    print(f"SQL file written to: {sql_file}")
    print(f"\nTotal UPDATE statements: {len(updates)}")
    print("\nTo execute:")
    print(f"  mysql -u root -p automan_car_purchase < {sql_file}")
    print("\nOr copy the SQL statements and run in phpMyAdmin.")

if __name__ == '__main__':
    main()


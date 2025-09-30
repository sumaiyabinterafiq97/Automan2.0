## CSV Import Requirements — Purchases

This document describes the required CSV format for importing purchase data into Automan.

### 1) File Encoding and Format
- Encoding: UTF-8
- Separator: comma (,)
- Quoting: standard CSV quotes ("...") supported; commas inside quotes are allowed
- No BOM/zero-width characters. If present, they will be ignored on headers only.

### 2) Header Row (must be present)
The importer auto-maps headers case-insensitively and accepts common variants.

Required headers:
- DATE
- LOT NO. (also accepts: LOT NO, LOT)
- CHASSIS (also accepts: CHASIS)

Optional headers (any order, any subset):
- YEAR
- CAR NAME (aka CARNAME)
- AUCTION HOUSE (aka AUCTION NAME, AUCTION)
- STOCK LOCATION
- RIXO COMPANY
- CLIENT NAME
- COUNTRY
- PRICE
- AUCTION FEE, RECYCLE FEE, ROAD TAX, TOTAL PRICE, PAYMENT DATE
- RIXO REQUESTED, RIXO CONFIRMED (aka RXO CONFIRMED), RIXO PRICE (aka RIXO CHARGES)
- SHIPMENT DATE, B/L NO (aka B/L NO., BL NO), VESSEL NO (aka VESSEL NO., VESSEL)
- DESTINATION, SHIPMENT CHARGES, FREIGHT, STORAGE CHARGES, MISC CHARGES
- INSPECTION FEE, COMMISSION
- NOTES (Japanese is allowed; common phrases are translated)

Notes:
- Extra columns are ignored.
- Repeated header rows in the middle of the file are automatically skipped.

### 3) Data Rows
- Every row after the header is treated as data unless it matches a repeated header.
- Required field values per row:
  - CHASSIS: non-empty and not "-". Rows with empty/"-" chassis are skipped.
  - LOT NO.: should be present (string)
  - DATE: Japanese dates like "6月2日月曜日" are accepted and converted to English.
- Trailing empty rows or lines with only commas are ignored.

### 4) Examples

Valid header example:
```
DATE,LOT NO.,CHASSIS,YEAR,CAR NAME,AUCTION HOUSE,STOCK LOCATION,CLIENT NAME,PRICE,RIXO CONFIRMED,NOTES
```

Valid row example:
```
6月3日火曜日,30356,ANH20-8170371,2011,VELLFIRE,USS YOKOHAMA,GLOBAL KAWASAKI,AN KIKAKU,"¥460,000",TRUE,書類送付済み
```

### 5) Known Skips/Filters
- Rows with CHASSIS = "-" or blank → skipped
- Blank lines → skipped
- Mid-file header duplicates → skipped


### 7) Where this is implemented
- Frontend: `src/jsMain/kotlin/com/automan/purchase/MinimalPurchaseApp.kt` (`showImportModal` → `handleImport` → POST `/api/purchases/import`)
- Backend: `backend/src/main/kotlin/com/automan/backend/service/PurchaseService.kt` (`importPurchases`), including:
  - Robust CSV parsing with quotes
  - Flexible header mapping and variants
  - Japanese date/notes normalization
  - Duplicate handling (DB unique on `chassis`)

### 8) Quick Checklist Before Import
- CSV is UTF-8 with one header row
- CHASSIS column exists and every data row has a real value (not "-")
- Remove trailing empty/"FALSE" rows and accidental mid-file headers


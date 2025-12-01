# Invoice PDF Mapping List

## Invoice Page Fields → PDF Location Mapping

### **Header Section (Top of PDF)**
1. **Company Logo/Image** → Static: Company logo image (if available) - positioned at top left
2. **Company Name** → Static: "MEMON CO., LTD." (bold, large font)
3. **Company Address** → Static: 
   - "CHIBA-KEN, ICHIKAWA-SHI"
   - "GYOTOKUEKIMAE 3-6-1"
   - "TAIYO MANSION #112"
4. **Company Contact Details** → Static:
   - "TEL : 047-303-3098"
   - "FAX : 047-711-0409"
   - "EMAIL : INFO@MEMONCOLTD.COM"
5. **Invoice Title** → Static: "INVOICE" 
6. **Invoice Number** → From `invoiceNumber` field → PDF: "INVOICE NO:" label + value
7. **Invoice Date** → Current date (date of creation of the pdf)
8. **LC Number** → From `invoiceLcNo` field → PDF: "L/C NO:" label + value

### **Client/Consignee Section (Top Left or Center)**
9. **Client Name** → From `invoiceClient` field → PDF: "TO:" or "CONSIGNEE:" label + value (bold)
10. **Client Address** → From `invoiceClient` field (if contains address) → PDF: Address lines below client name (normal weight)

### **Shipping Details Section (Top Right or Below Client)**
11. **Vessel** → From `invoiceVessel` field → PDF: "VESSEL:" label + value
12. **Shipping Date** → From `invoiceShippingDate` field → PDF: "SHIPPING DATE:"  label + value
13. **Shipping Location FROM** → From `invoiceFrom` field → PDF: "FROM:" label + value
14. **Shipping Location TO** → From `invoiceTo` field → PDF: "TO:" or "DESTINATION:" label + value
15. **Price Type** → From `invoicePriceType` radio (C&F or FOB) → PDF: "PRICE TERMS:" or "TERMS:" label + value

### **Items Table Section (Center of PDF)**
16. **Table Header** → Static headers: "NO.", "CHASSIS", "NAME", "YEAR", "AMOUNT"
17. **Table Rows** → From `invoiceListTableBody` rows:
    - **NO.** → Row number (1, 2, 3, ...)
    - **CHASSIS** → From table cell
    - **NAME** → From table cell (car name)
    - **YEAR** → From table cell (car model year)
    - **AMOUNT** → From table cell (formatted with ¥ symbol)

### **Total Section (Bottom Right of Table)**
18. **Total Amount** → From `invoiceTotalAmount` element → PDF: "TOTAL AMOUNT:" label + formatted value (¥X,XXX,XXX)

### **Bank Account Section (Bottom Left)**
19. **Bank Account Details** → From `invoiceBankAccount` dropdown selected value → PDF: 
    - Bank Name
    - Account Number
    - Account Name
    - SWIFT Code
    (Format: Multi-line text, smaller font)

### **Footer Section (Bottom)**
20. **Message/Notes** → From `invoiceMessage` textarea → PDF: "REMARKS:" or "NOTES:" label + value (if provided)
21. **Signature Section** → Static: Signature field (blank line) above "Director" label

---

## PDF Layout Structure (Typical Invoice Format)

```
┌─────────────────────────────────────────────────────────┐
│  [Company Logo]          INVOICE NO: [value]           │
│  MEMON CO., LTD.         DATE: [current date]          │
│  CHIBA-KEN, ICHIKAWA-SHI L/C NO: [value]              │
│  GYOTOKUEKIMAE 3-6-1                                    │
│  TAIYO MANSION #112                                     │
│  TEL : 047-303-3098                                     │
│  FAX : 047-711-0409                                     │
│  EMAIL : INFO@MEMONCOLTD.COM                            │
├─────────────────────────────────────────────────────────┤
│  TO: [Client Name]                                      │
│      [Client Address]                                   │
│                                                         │
│  VESSEL: [value]        FROM: [value]  TO: [value]    │
│  SHIPPING DATE: [value]  TERMS: [C&F/FOB]              │
├─────────────────────────────────────────────────────────┤
│  NO. │ CHASSIS │ NAME │ YEAR │ AMOUNT                  │
├─────────────────────────────────────────────────────────┤
│  1   │ [value] │ [val]│ [val]│ ¥[amount]              │
│  2   │ [value] │ [val]│ [val]│ ¥[amount]              │
│  ... │  ...    │ ...  │ ...  │ ...                    │
├─────────────────────────────────────────────────────────┤
│                    TOTAL AMOUNT: ¥[total]              │
├─────────────────────────────────────────────────────────┤
│  BANK ACCOUNT:                                          │
│  [Bank Name]                                           │
│  A/C NO: [value]                                       │
│  A/C NAME: [value]                                     │
│  SWIFT: [value]                                        │
│                                                         │
│  REMARKS: [message if provided]                        │
└─────────────────────────────────────────────────────────┘
```

---

## Field Extraction Notes

- **Client Name/Address**: May need to parse `invoiceClient` field if it contains both name and address (separated by newlines)
- **Amount Formatting**: Convert to Japanese Yen format (¥) with comma separators
- **Date Formatting**: Convert `invoiceShippingDate` (YYYY-MM-DD) to readable format (DD.MON.YYYY or DD/MM/YYYY)
- **Bank Account**: Parse the selected option value to extract individual components (Bank Name, A/C No, A/C Name, SWIFT)

---

## Implementation Priority

1. **High Priority** (Core Invoice Data):
   - Invoice Number
   - Client Name
   - Vessel
   - Shipping Date
   - Items Table (NO., CHASSIS, NAME, YEAR, AMOUNT)
   - Total Amount

2. **Medium Priority** (Shipping Details):
   - FROM/TO locations
   - Price Type (C&F/FOB)
   - LC Number

3. **Low Priority** (Additional Info):
   - Bank Account Details
   - Message/Remarks


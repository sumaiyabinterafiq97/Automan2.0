#!/bin/bash

# Wait for backend to be ready
echo "Waiting for backend to be ready..."
MAX_ATTEMPTS=30
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    if curl -s http://localhost:8083/api/booking/mappings/by-country/PAKISTAN > /dev/null 2>&1; then
        echo "Backend is ready!"
        break
    fi
    ATTEMPT=$((ATTEMPT + 1))
    echo "Attempt $ATTEMPT/$MAX_ATTEMPTS: Backend not ready yet, waiting 2 seconds..."
    sleep 2
done

if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
    echo "ERROR: Backend did not become ready after $MAX_ATTEMPTS attempts"
    exit 1
fi

# Base URL
BASE_URL="http://localhost:8083/api/booking/mappings/add"

echo "Seeding booking mappings..."

# Country-level defaults

# Pakistan default
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "PAKISTAN",
    "pod": "KARACHI",
    "consigneeName": "OVERSEAS TRANSIT AGENCY (PVT) LTD.",
    "consigneeAddress": "1201-1203, 12TH FLOOR, Q.M.HOUSE, PLOT NO. 11/2RY9, ELLANDER ROAD, OFF.I.I CHUNDRIGAR ROAD (OPP. SHAHEEN COMPLEX), KARACHI",
    "notes": "Country default mapping for Pakistan"
  }' > /dev/null

# Kenya default
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "KENYA",
    "pod": "MOMBASA",
    "consigneeName": "LAKHANI MOTORS (K) LTD",
    "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM",
    "notes": "Country default mapping for Kenya"
  }' > /dev/null

# South Africa default
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "SOUTH AFRICA",
    "pod": "DURBAN",
    "consigneeName": "LAKHANI MOTORS (K) LTD",
    "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM",
    "notes": "Country default mapping for South Africa"
  }' > /dev/null

# Mozambique default
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "MOZAMBIQUE",
    "pod": "MAPUTO",
    "consigneeName": "LAKHANI MOTORS (K) LTD",
    "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM",
    "notes": "Country default mapping for Mozambique"
  }' > /dev/null

# Uganda default
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "UGANDA",
    "consigneeName": "LAKHANI MOTORS (K) LTD",
    "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM",
    "notes": "Country default mapping for Uganda"
  }' > /dev/null

# UAE default
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "UAE",
    "pod": "JABEL ALI-DUBAI",
    "consigneeName": "LAKHANI MOTORS FZE",
    "consigneeAddress": "SHOWROOM# 108 DUCAMZ RAS AL KHOR, AL AWEER ROAD, DUBAI- UAE, PO BOX: 63280, TEL: 971-4-3339141 FAX:971-4-3338574, EMAIL: lakhanimotors@gmail.com",
    "notes": "Country default mapping for UAE"
  }' > /dev/null

# Client-specific overrides

# SHEHROZE MOTORS
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "PAKISTAN",
    "clientName": "SHEHROZE MOTORS",
    "pod": "KARACHI",
    "stockLocation": "GLOBAL KAWASAKI",
    "pols": "YOKOHAMA",
    "consigneeName": "OVERSEAS TRANSIT AGENCY (PVT) LTD.",
    "consigneeAddress": "1201-1203, 12TH FLOOR, Q.M.HOUSE, PLOT NO. 11/2RY9, ELLANDER ROAD, OFF.I.I CHUNDRIGAR ROAD (OPP. SHAHEEN COMPLEX), KARACHI",
    "notes": "Client-specific override for Shehroze Motors"
  }' > /dev/null

# DAAVI AUTO
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "KENYA",
    "clientName": "DAAVI AUTO",
    "pod": "MOMBASA",
    "stockLocation": "AQUA LOGISTICS",
    "pols": "YOKOHAMA",
    "consigneeName": "LAKHANI MOTORS (K) LTD",
    "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM",
    "notes": "Client-specific override for Daavi Auto"
  }' > /dev/null

# NEW GRAND AUTO (JAWAD)
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "UGANDA",
    "clientName": "NEW GRAND AUTO (JAWAD)",
    "stockLocation": "GLOBAL NAGOYA",
    "pols": "NAGOYA",
    "consigneeName": "LAKHANI MOTORS (K) LTD",
    "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM",
    "notes": "Client-specific override for New Grand Auto (Jawad)"
  }' > /dev/null

# IRSHAD ALI AKHTAR
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "MOZAMBIQUE",
    "clientName": "IRSHAD ALI AKHTAR",
    "pod": "MAPUTO",
    "stockLocation": "FLASHRISE",
    "pols": "NAGOYA",
    "consigneeName": "LAKHANI MOTORS (K) LTD",
    "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM",
    "notes": "Client-specific override for Irshad Ali Akhtar"
  }' > /dev/null

# AAMIR DEDHI
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "UAE",
    "clientName": "AAMIR DEDHI",
    "pod": "JABEL ALI-DUBAI",
    "stockLocation": "KLC",
    "pols": "OSAKA,SENBOKU,KOBE",
    "consigneeName": "LAKHANI MOTORS FZE",
    "consigneeAddress": "SHOWROOM# 108 DUCAMZ RAS AL KHOR, AL AWEER ROAD, DUBAI- UAE, PO BOX: 63280, TEL: 971-4-3339141 FAX:971-4-3338574, EMAIL: lakhanimotors@gmail.com",
    "notes": "Client-specific override for Aamir Dedhi"
  }' > /dev/null

# AUTOHANDLER
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "SOUTH AFRICA",
    "clientName": "AUTOHANDLER",
    "pod": "DURBAN",
    "stockLocation": "GLOBAL HAKATA",
    "pols": "HAKATA",
    "consigneeName": "LAKHANI MOTORS (K) LTD",
    "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM",
    "notes": "Client-specific override for Autohandler"
  }' > /dev/null

# ESSA ADMANI
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "UK",
    "clientName": "ESSA ADMANI",
    "stockLocation": "BARAKI PARKING",
    "notes": "Client-specific override for Essa Admani"
  }' > /dev/null

# IRFAN MEMON HYDERABAD
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "NEWZEALAND",
    "clientName": "IRFAN MEMON HYDERABAD",
    "stockLocation": "LOCAL",
    "notes": "Client-specific override for Irfan Memon Hyderabad"
  }' > /dev/null

# NAVEES AHMAD
curl -s -X POST "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "country": "JAPAN",
    "clientName": "NAVEES AHMAD",
    "notes": "Client-specific override for Navees Ahmad"
  }' > /dev/null

echo "Seeding complete!"

# Verify Pakistan mappings
echo ""
echo "Verifying Pakistan mappings..."
curl -s http://localhost:8083/api/booking/mappings/by-country/PAKISTAN | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8083/api/booking/mappings/by-country/PAKISTAN


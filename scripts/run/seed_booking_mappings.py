#!/usr/bin/env python3
"""
Seed booking_mappings table via API.
Run this script after the backend is running on http://localhost:8083
"""

import requests
import json
import time
import sys

BASE_URL = "http://localhost:8083/api/booking/mappings"

def wait_for_backend(max_attempts=30):
    """Wait for backend to be ready"""
    print("Waiting for backend to be ready...")
    for attempt in range(1, max_attempts + 1):
        try:
            response = requests.get(f"{BASE_URL}/by-country/PAKISTAN", timeout=2)
            if response.status_code in [200, 404]:  # 404 is OK, means backend is up
                print("✅ Backend is ready!")
                return True
        except requests.exceptions.RequestException:
            pass
        print(f"Attempt {attempt}/{max_attempts}: Backend not ready yet, waiting 2 seconds...")
        time.sleep(2)
    print(f"❌ ERROR: Backend did not become ready after {max_attempts} attempts")
    return False

def add_mapping(mapping_data):
    """Add a single mapping"""
    try:
        response = requests.post(f"{BASE_URL}/add", json=mapping_data, timeout=5)
        if response.status_code == 200:
            result = response.json()
            if result.get("success"):
                return True, None
            else:
                return False, result.get("message", "Unknown error")
        else:
            return False, f"HTTP {response.status_code}: {response.text}"
    except Exception as e:
        return False, str(e)

def seed_data():
    """Seed all booking mappings"""
    mappings = [
        # Country-level defaults
        {
            "country": "PAKISTAN",
            "pod": "KARACHI",
            "consigneeName": "OVERSEAS TRANSIT AGENCY (PVT) LTD.",
            "consigneeAddress": "1201-1203, 12TH FLOOR, Q.M.HOUSE, PLOT NO. 11/2RY9, ELLANDER ROAD, OFF.I.I CHUNDRIGAR ROAD (OPP. SHAHEEN COMPLEX), KARACHI"
        },
        {
            "country": "KENYA",
            "pod": "MOMBASA",
            "consigneeName": "LAKHANI MOTORS (K) LTD",
            "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM"
        },
        {
            "country": "SOUTH AFRICA",
            "pod": "DURBAN",
            "consigneeName": "LAKHANI MOTORS (K) LTD",
            "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM"
        },
        {
            "country": "MOZAMBIQUE",
            "pod": "MAPUTO",
            "consigneeName": "LAKHANI MOTORS (K) LTD",
            "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM"
        },
        {
            "country": "UGANDA",
            "consigneeName": "LAKHANI MOTORS (K) LTD",
            "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM"
        },
        {
            "country": "UAE",
            "pod": "JABEL ALI-DUBAI",
            "consigneeName": "LAKHANI MOTORS FZE",
            "consigneeAddress": "SHOWROOM# 108 DUCAMZ RAS AL KHOR, AL AWEER ROAD, DUBAI- UAE, PO BOX: 63280, TEL: 971-4-3339141 FAX:971-4-3338574, EMAIL: lakhanimotors@gmail.com"
        },
        # Client-specific overrides
        {
            "country": "PAKISTAN",
            "clientName": "SHEHROZE MOTORS",
            "pod": "KARACHI",
            "stockLocation": "GLOBAL KAWASAKI",
            "pols": "YOKOHAMA",
            "consigneeName": "OVERSEAS TRANSIT AGENCY (PVT) LTD.",
            "consigneeAddress": "1201-1203, 12TH FLOOR, Q.M.HOUSE, PLOT NO. 11/2RY9, ELLANDER ROAD, OFF.I.I CHUNDRIGAR ROAD (OPP. SHAHEEN COMPLEX), KARACHI"
        },
        {
            "country": "KENYA",
            "clientName": "DAAVI AUTO",
            "pod": "MOMBASA",
            "stockLocation": "AQUA LOGISTICS",
            "pols": "YOKOHAMA",
            "consigneeName": "LAKHANI MOTORS (K) LTD",
            "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM"
        },
        {
            "country": "UGANDA",
            "clientName": "NEW GRAND AUTO (JAWAD)",
            "stockLocation": "GLOBAL NAGOYA",
            "pols": "NAGOYA",
            "consigneeName": "LAKHANI MOTORS (K) LTD",
            "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM"
        },
        {
            "country": "MOZAMBIQUE",
            "clientName": "IRSHAD ALI AKHTAR",
            "pod": "MAPUTO",
            "stockLocation": "FLASHRISE",
            "pols": "NAGOYA",
            "consigneeName": "LAKHANI MOTORS (K) LTD",
            "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM"
        },
        {
            "country": "UAE",
            "clientName": "AAMIR DEDHI",
            "pod": "JABEL ALI-DUBAI",
            "stockLocation": "KLC",
            "pols": "OSAKA,SENBOKU,KOBE",
            "consigneeName": "LAKHANI MOTORS FZE",
            "consigneeAddress": "SHOWROOM# 108 DUCAMZ RAS AL KHOR, AL AWEER ROAD, DUBAI- UAE, PO BOX: 63280, TEL: 971-4-3339141 FAX:971-4-3338574, EMAIL: lakhanimotors@gmail.com"
        },
        {
            "country": "SOUTH AFRICA",
            "clientName": "AUTOHANDLER",
            "pod": "DURBAN",
            "stockLocation": "GLOBAL HAKATA",
            "pols": "HAKATA",
            "consigneeName": "LAKHANI MOTORS (K) LTD",
            "consigneeAddress": "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM"
        },
        {
            "country": "UK",
            "clientName": "ESSA ADMANI",
            "stockLocation": "BARAKI PARKING"
        },
        {
            "country": "NEWZEALAND",
            "clientName": "IRFAN MEMON HYDERABAD",
            "stockLocation": "LOCAL"
        },
        {
            "country": "JAPAN",
            "clientName": "NAVEES AHMAD"
        },
    ]

    print(f"\n📝 Seeding {len(mappings)} booking mappings...\n")
    success_count = 0
    error_count = 0

    for i, mapping in enumerate(mappings, 1):
        desc = mapping.get("clientName") or mapping.get("country", "Unknown")
        print(f"[{i}/{len(mappings)}] Adding: {desc}...", end=" ")
        success, error = add_mapping(mapping)
        if success:
            print("✅")
            success_count += 1
        else:
            print(f"❌ {error}")
            error_count += 1

    print(f"\n📊 Summary: {success_count} successful, {error_count} errors")

    # Verify Pakistan mappings
    print("\n🔍 Verifying Pakistan mappings...")
    try:
        response = requests.get(f"{BASE_URL}/by-country/PAKISTAN", timeout=5)
        if response.status_code == 200:
            result = response.json()
            if result.get("success"):
                mappings = result.get("data", [])
                print(f"✅ Found {len(mappings)} mapping(s) for Pakistan:")
                for m in mappings:
                    client = m.get("clientName", "(default)")
                    pod = m.get("pod", "N/A")
                    print(f"   - {client}: POD={pod}")
            else:
                print(f"❌ API returned success=false: {result.get('message')}")
        else:
            print(f"❌ HTTP {response.status_code}: {response.text}")
    except Exception as e:
        print(f"❌ Error verifying: {e}")

def main():
    if not wait_for_backend():
        sys.exit(1)
    seed_data()
    print("\n✅ Seeding complete!")

if __name__ == "__main__":
    main()


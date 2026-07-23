# Number Place import plan

## Goal
Bring `master_menu.number_place` (Place Name Japanese dropdown) in line with the client's latest sheet.

| Metric | Count |
|--------|------:|
| Client rows | 141 |
| Client unique JP | 138 |
| Current API | 66 |
| **To ADD** | **80** |
| EN spelling fixes (optional) | 3 |
| Extras in DB not on client (optional delete) | 8 |

## Format
Master Menu stores CSV values as: `日本語 (English)`  
Example: `札幌 (Sapporo)`  
API: `POST /api/master-menu/number_place` body `{"value":"..."}` (idempotent skip if exists)

## Step 1 — ADD only (recommended first)
Add the 80 missing values. Keeps existing prefecture-style entries and junk until you decide.

```bash
# dry-run
python3 scripts/import-number-place.py

# apply adds
python3 scripts/import-number-place.py --apply
```

Expected after add: ~146 values (66 + 80 = 146), unless some were added meanwhile.

## Step 2 — Optional EN fixes
Align English to client sheet for same Japanese:

- `市川 (ICHIKAWA)` → `市川 (Ichikawa)`
- `伊勢志摩 (ISESHIMA)` → `伊勢志摩 (Ise-Shima)`
- `北九州(KITA-KYUSHU)` → `北九州 (Kitakyushu)`

```bash
python3 scripts/import-number-place.py --apply --fix-en
```

## Step 3 — Optional delete extras
Not on new client list:

- `茨城 (Ibaraki)`
- `栃木 (Tochigi)`
- `埼玉 (Saitama)`
- `東京 (Tokyo)`
- `神奈川 (Kanagawa)`
- `愛知 (Aichi)`
- `兵庫 (Hyogo)`
- `ggg`

```bash
python3 scripts/import-number-place.py --apply --delete-extras
```

**Caution:** Deleting 東京/神奈川/愛知/etc. may break older purchases that saved those prefecture-style place values in Number Cut. Prefer leaving extras unless client confirms.

## Step 4 — Verify
1. `curl -s http://localhost:8083/api/master-menu/number_place | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))'`
2. Master List → Number Place page shows new cities
3. Add Purchase → Number Cut → Place Name dropdown includes e.g. 横浜, なにわ, 庄内
4. Soft refresh frontend (no rebuild required — options load from API)

## Files
- `scripts/number-place-add-list.txt` — exact POST strings (80)
- `scripts/number-place-client-canonical.txt` — full client unique (138)
- `scripts/import-number-place.py` — dry-run / apply script

## Out of scope
- No Flyway migration (live DB via API; V57 seed stays historical)
- No frontend rebuild needed
- Prod: point `--base` / `AUTOMAN_API_BASE` at prod API after local validation

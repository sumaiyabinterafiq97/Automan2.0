#!/usr/bin/env python3
"""
Import missing Number Place (master_menu.number_place) entries via Automan API.

Safe defaults:
  - ADD only (POST) — does not wipe existing values
  - Skips values that already exist (API is case-insensitive duplicate-aware)
  - Does NOT delete extras or update English unless flags are set

Usage:
  # dry-run (default)
  python3 scripts/import-number-place.py

  # apply adds
  python3 scripts/import-number-place.py --apply

  # also fix EN spelling for JP matches (伊勢志摩, 北九州, 市川, …)
  python3 scripts/import-number-place.py --apply --fix-en

  # also delete extras not on client list (茨城/栃木/埼玉/東京/神奈川/愛知/兵庫/ggg)
  python3 scripts/import-number-place.py --apply --delete-extras

Env:
  AUTOMAN_API_BASE  default http://localhost:8083
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADD_LIST = ROOT / "scripts" / "number-place-add-list.txt"
CANON = ROOT / "scripts" / "number-place-client-canonical.txt"

DEFAULT_BASE = "http://localhost:8083"


def api(base: str, method: str, path: str, body: dict | None = None):
    url = base.rstrip("/") + path
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def parse_entry(s: str):
    s = s.strip()
    m = re.match(r"^(.+?)\s*\(([^)]+)\)\s*$", s)
    if m:
        return m.group(1).strip(), m.group(2).strip(), s
    return s, None, s


def load_lines(path: Path) -> list[str]:
    return [ln.strip() for ln in path.read_text(encoding="utf-8").splitlines() if ln.strip()]


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--base", default=None, help="API base (default AUTOMAN_API_BASE or localhost:8083)")
    p.add_argument("--apply", action="store_true", help="Actually POST/PUT/DELETE (otherwise dry-run)")
    p.add_argument("--fix-en", action="store_true", help="Update English when JP matches client but EN differs")
    p.add_argument("--delete-extras", action="store_true", help="Delete DB values whose JP is not on client list")
    args = p.parse_args()

    import os
    base = args.base or os.environ.get("AUTOMAN_API_BASE", DEFAULT_BASE)

    if not ADD_LIST.exists():
        print(f"Missing add list: {ADD_LIST}", file=sys.stderr)
        return 1

    add_values = load_lines(ADD_LIST)
    canon = OrderedDict()
    for raw in load_lines(CANON):
        jp, en, full = parse_entry(raw)
        canon[jp] = full

    try:
        current = api(base, "GET", "/api/master-menu/number_place")
    except urllib.error.URLError as e:
        print(f"Failed to GET number_place from {base}: {e}", file=sys.stderr)
        return 1

    if not isinstance(current, list):
        print(f"Unexpected response: {current!r}", file=sys.stderr)
        return 1

    current_by_jp = {}
    for raw in current:
        jp, en, full = parse_entry(str(raw))
        current_by_jp[jp] = full

    print(f"API base: {base}")
    print(f"Current count: {len(current)}")
    print(f"Canonical client unique: {len(canon)}")
    print(f"Planned adds: {len(add_values)}")
    print(f"Mode: {'APPLY' if args.apply else 'DRY-RUN'}")
    print()

    added = skipped = failed = 0
    for value in add_values:
        jp, _, _ = parse_entry(value)
        if jp in current_by_jp:
            print(f"SKIP (already present): {value}")
            skipped += 1
            continue
        print(f"ADD: {value}")
        if args.apply:
            try:
                updated = api(base, "POST", "/api/master-menu/number_place", {"value": value})
                current_by_jp[jp] = value
                added += 1
                print(f"  -> ok, now {len(updated)} values")
            except Exception as e:
                failed += 1
                print(f"  -> FAIL: {e}")
        else:
            added += 1

    fixed = 0
    if args.fix_en:
        print()
        for jp, target in canon.items():
            if jp not in current_by_jp:
                continue
            cur = current_by_jp[jp]
            if cur == target:
                continue
            print(f"FIX-EN: {cur!r} -> {target!r}")
            if args.apply:
                try:
                    updated = api(
                        base,
                        "PUT",
                        "/api/master-menu/number_place",
                        {"value": target, "originalValue": cur},
                    )
                    current_by_jp[jp] = target
                    fixed += 1
                    print(f"  -> ok, now {len(updated)} values")
                except Exception as e:
                    failed += 1
                    print(f"  -> FAIL: {e}")
            else:
                fixed += 1

    deleted = 0
    if args.delete_extras:
        print()
        extras = [full for jp, full in list(current_by_jp.items()) if jp not in canon]
        for full in extras:
            print(f"DELETE: {full}")
            if args.apply:
                try:
                    q = urllib.parse.urlencode({"value": full})
                    updated = api(base, "DELETE", f"/api/master-menu/number_place?{q}")
                    jp, _, _ = parse_entry(full)
                    current_by_jp.pop(jp, None)
                    deleted += 1
                    print(f"  -> ok, now {len(updated)} values")
                except Exception as e:
                    failed += 1
                    print(f"  -> FAIL: {e}")
            else:
                deleted += 1

    print()
    print("Summary:")
    print(f"  adds: {added} (skipped already-present: {skipped})")
    if args.fix_en:
        print(f"  en fixes: {fixed}")
    if args.delete_extras:
        print(f"  deletes: {deleted}")
    print(f"  failures: {failed}")
    if not args.apply:
        print()
        print("Dry-run only. Re-run with --apply to write.")
    else:
        final = api(base, "GET", "/api/master-menu/number_place")
        print(f"  final count: {len(final)}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())

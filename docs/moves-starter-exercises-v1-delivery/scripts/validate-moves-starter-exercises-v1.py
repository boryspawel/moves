#!/usr/bin/env python3
"""Validate moves-starter-exercises-v1 delivery without a running application."""
from __future__ import annotations
import csv, json, re, sys, unicodedata
from collections import defaultdict
from pathlib import Path

root = Path(__file__).resolve().parents[1]
ref = root / "src/main/resources/reference/exercises/v1"
jsonl = ref / "moves-starter-exercises-v1.jsonl"
coverage = ref / "moves-starter-exercises-v1-coverage.csv"
evidence = ref / "moves-starter-exercises-v1-evidence.csv"

records = [json.loads(line) for line in jsonl.read_text(encoding="utf-8").splitlines() if line]
assert len(records) == 120
assert [r["sourceRecordKey"] for r in records] == sorted(r["sourceRecordKey"] for r in records)
assert len({r["sourceRecordKey"] for r in records}) == 120
assert jsonl.stat().st_size < 10 * 1024 * 1024
assert max(len(line.encode("utf-8")) for line in jsonl.read_text(encoding="utf-8").splitlines()) < 256 * 1024

allowed_equipment = {"BODYWEIGHT","MAT","DUMBBELL","BAND","CHAIR","BENCH","KETTLEBELL","STEP_BOX","STRAP","WALL"}
allowed_positions = {"STANDING","SUPINE","PRONE","KNEELING","SEATED","SIDE_LYING","QUADRUPED","HALF_KNEELING","SPLIT_STANCE","FRONT_SUPPORT","SIDE_SUPPORT","SQUAT"}
allowed_units = {"REP","SECOND","METER"}
unsafe = {"contraindications","injuries","treatment","safeFor"}
seen = {}
def norm(value):
    return re.sub(r"\s+"," ",unicodedata.normalize("NFKC", value).strip()).casefold()

for record in records:
    assert record["schemaVersion"] == "moves.exercise-import/1.0"
    assert 3 <= len(record["instructions"]) <= 7
    assert set(record["equipment"]) <= allowed_equipment
    assert record["position"] in allowed_positions
    assert not (unsafe & set(record))
    assert record["license"]["redistributionAllowed"] is True
    for dose in record["doseCapabilities"]:
        assert dose["unit"] in allowed_units
        assert dose.get("minimum",0) <= dose.get("maximum",0)
    for contribution in record["contributions"]:
        assert 0 <= contribution["coefficientLow"] <= contribution["coefficientHigh"] <= 1
    for value in [record["name"], *record.get("aliases",[])]:
        key = norm(value)
        assert key not in seen, (value, seen.get(key))
        seen[key] = record["sourceRecordKey"]

with coverage.open(encoding="utf-8", newline="") as f:
    coverage_keys = {row["source_record_key"] for row in csv.DictReader(f)}
with evidence.open(encoding="utf-8", newline="") as f:
    evidence_keys = {row["source_record_key"] for row in csv.DictReader(f)}
keys = {r["sourceRecordKey"] for r in records}
assert coverage_keys == keys
assert evidence_keys == keys
print(f"PASS: {len(records)} records; SHA-256={__import__('hashlib').sha256(jsonl.read_bytes()).hexdigest()}")

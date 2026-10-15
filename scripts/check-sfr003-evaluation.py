#!/usr/bin/env python3
"""Offline readiness check. No network, no inference, no fabricated accuracy."""
import csv
import hashlib
from decimal import Decimal, InvalidOperation
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
STRUCTURED = ["Address", "DepositWon", "MonthlyRentWon", "MaintenanceFeeWon", "AreaSqm", "Floor"]


def truth_valid(field, value):
    value = value.strip()
    if value == "null":
        return True
    if not value:
        return False
    if field == "Address":
        return len(value) <= 160
    if field == "Floor":
        return bool(re.fullmatch(r"-?\d+", value)) and -20 <= int(value) <= 200
    if field.endswith("Won"):
        return bool(re.fullmatch(r"\d+", value)) and int(value) <= 1_000_000_000_000
    try:
        number = Decimal(value)
        return number.is_finite() and 0 <= number <= 100000
    except InvalidOperation:
        return False


def check(rows, base=ROOT):
    issues = []
    seen = set()
    seen_sources = set()
    for row in rows:
        sample = row.get("sampleId", "").strip()
        missing = []
        if not sample or sample in seen:
            missing.append("uniqueSampleId")
        seen.add(sample)
        if row.get("sampleType", "").strip() != "representative":
            missing.append("representativeSampleType")
        if row.get("privacyChecked", "").strip().lower() not in {"yes", "true"}:
            missing.append("privacyConfirmation")
        if not row.get("truthReviewedBy", "").strip():
            missing.append("humanTruthReviewer")
        for column in ("sourceFile", "expectedRawTextFile"):
            name = row.get(column, "").strip()
            path = base / name
            if not name or not path.is_file() or path.stat().st_size == 0:
                missing.append(column)
            elif column == "sourceFile":
                digest = hashlib.sha256(path.read_bytes()).digest()
                if digest in seen_sources:
                    missing.append("uniqueSourceContent")
                seen_sources.add(digest)
        for field in STRUCTURED:
            if not truth_valid(field, row.get("expected" + field, "")):
                missing.append("expected" + field)
        if missing:
            issues.append({"sampleId": sample, "missingOrInvalid": missing})
    ready = len(rows) - len(issues)
    return {"scope": "offline_representative_dataset_readiness", "target": 20, "templateRows": len(rows),
            "readySamples": ready, "additionalReadySamplesNeeded": max(0, 20 - ready),
            "accuracy": None, "externalApiCalls": 0, "issues": issues}


if __name__ == "__main__":
    source = ROOT / "docs/sfr003/evaluation-20-template.csv"
    with source.open(newline="", encoding="utf-8") as file:
        result = check(list(csv.DictReader(file)))
    (source.parent / "evaluation-readiness.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({key: value for key, value in result.items() if key != "issues"}))

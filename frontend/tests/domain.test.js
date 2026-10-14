import { test } from "vitest";
import assert from "node:assert/strict";
import { audit } from "../src/features/crosscheck/domain/audit.js";
import { fixedCost } from "../src/features/listing/domain/cost.js";
import { statistics } from "../src/features/review/domain/statistics.js";
import { validateComparison } from "../src/features/comparison/domain/selection.js";
test("missing maintenance fee is not zero; real zero is retained", () => {
  assert.equal(fixedCost({ rent: 350000, fee: null }), null);
  assert.equal(fixedCost({ rent: 350000, fee: 0 }), 350000);
});
test("comparison denominator excludes incompatible area bases and building totals", () => {
  const r = {
    area: 20,
    areaType: "전용",
    floor: 2,
    official: { area: 20, areaType: "공급", floor: 2, scope: "호실" },
  };
  assert.deepEqual([audit(r).rate, audit(r).count], [100, 1]);
  r.official.scope = "건물";
  assert.deepEqual([audit(r).rate, audit(r).count], [null, 0]);
});
test("area tolerance boundary and discrepancy", () => {
  const r = {
    area: 20,
    areaType: "전용",
    floor: 2,
    official: { area: 20.5, areaType: "전용", floor: 2, scope: "호실" },
  };
  assert.equal(audit(r).rate, 100);
  r.official.area = 20.51;
  assert.equal(audit(r).rate, 50);
});
test("review mean excludes no response; independent building statistics", () => {
  const rs = [1, 3, 5, null].map((n) => ({
    building: "a",
    scores: { 방음: n },
  }));
  rs.push({ building: "b", scores: { 방음: 1 } });
  let s = statistics(rs, "a");
  assert.equal(s.count, 4);
  assert.equal(s.items[0].count, 3);
  assert.equal(s.items[0].value, 3);
  assert.equal(s.items[1].value, null);
});
test("comparison accepts only 2–3 distinct listings", () => {
  assert.equal(validateComparison(["a", "b"]), true);
  assert.equal(validateComparison(["a", "b", "c"]), true);
  for (const x of [[], ["a"], ["a", "a"], ["a", "b", "c", "d"]])
    assert.equal(validateComparison(x), false);
});

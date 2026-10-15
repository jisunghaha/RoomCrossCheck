export const categories = [
  "방음",
  "수압",
  "채광",
  "환기",
  "주차",
  "관리",
  "벌레",
  "결로",
];
export const money = (v) =>
  v == null
    ? "정보 없음"
    : `${new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 1 }).format(v / 10000)}만`;
export const fixedCost = (r) =>
  r.rent == null || r.fee == null ? null : r.rent + r.fee;
export function audit(r) {
  if (!r.official) return { rows: [], rate: null, count: 0 };
  const o = r.official;
  const rows = [
    {
      label: "면적",
      ad: r.area == null ? "정보 없음" : `${r.area}㎡ (${r.areaType})`,
      official: o.area == null ? "자료 없음" : `${o.area}㎡ (${o.areaType})`,
      comparable:
        r.area != null &&
        o.area != null &&
        r.areaType !== "미상" &&
        r.areaType === o.areaType &&
        o.scope === "호실",
      match: Math.abs(r.area - o.area) <= 0.5,
      reason: "동일 호실·같은 면적 종류만 비교 · 허용오차 0.5㎡",
    },
    {
      label: "층수",
      ad: r.floor == null ? "정보 없음" : `${r.floor}층`,
      official: o.floor == null ? "자료 없음" : `${o.floor}층`,
      comparable: r.floor != null && o.floor != null && o.scope === "호실",
      match: r.floor === o.floor,
      reason: "같은 호실의 층수 비교",
    },
  ];
  let count = rows.filter((x) => x.comparable).length;
  return {
    rows,
    count,
    rate: count
      ? Math.round(
          (rows.filter((x) => x.comparable && x.match).length / count) * 100,
        )
      : null,
  };
}
export function statistics(reviews, building) {
  let rs = reviews.filter((x) => x.building === building);
  return {
    count: rs.length,
    items: categories.map((c) => {
      let scores = rs
        .map((x) => x.scores[c])
        .filter((x) => Number.isInteger(x) && x >= 1 && x <= 5);
      return {
        label: c,
        count: scores.length,
        value: scores.length
          ? scores.reduce((a, b) => a + b, 0) / scores.length
          : null,
      };
    }),
  };
}
export function validateComparison(ids) {
  return ids.length >= 2 && ids.length <= 3 && new Set(ids).size === ids.length;
}

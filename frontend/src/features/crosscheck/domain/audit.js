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

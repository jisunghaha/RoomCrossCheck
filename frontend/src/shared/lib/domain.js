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
const checklistTips = [
  "복도와 이웃집 소음",
  "온수와 수압 확인",
  "낮 시간 햇빛 방향",
  "창문·환풍기 작동",
  "주차 가능 대수와 비용",
  "공용부 청결과 관리",
  "방충망·틈새·흔적",
  "창틀·벽면 습기",
  "수도·인터넷·전기 포함 여부",
];
/**
 * 기본 방문 체크리스트. ID는 기존 브라우저 기록의 체크 순번(0~8)과 같아 이전 기록을 그대로 읽는다.
 */
export const defaultChecklist = [...categories, "관리비 포함 항목"].map((label, index) => ({
  id: String(index), label, tip: checklistTips[index],
}));
/** 회원이 구성한 체크리스트가 없으면 기본 항목을 돌려준다. */
export const checklistItems = (custom) => (Array.isArray(custom) && custom.length ? custom : defaultChecklist);
/** 현재 체크리스트 기준으로 확인 완료한 항목 수를 센다. 삭제한 항목의 과거 체크는 세지 않는다. */
export function checkedCount(visit, items) {
  const checks = visit?.checks || {};
  return items.filter((item) => checks[item.id]).length;
}
export const isDemoReview = (review) => typeof review?.id === "string" && review.id.startsWith("review-demo:");
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
      reason: r.area == null || o.area == null ? "등록 면적 또는 해당 호실의 공식 전유면적 자료가 없습니다."
        : r.areaType !== o.areaType || r.areaType === "미상" ? "면적 종류가 다르거나 미상이어서 비교할 수 없습니다."
        : o.scope !== "호실" ? "건물 전체 면적은 호실 면적과 비교할 수 없습니다."
        : Math.abs(r.area - o.area) <= 0.5 ? "동일 호실·전용 기준 허용오차 0.5㎡ 이내입니다."
        : `등록 면적이 공식 면적보다 ${r.area > o.area ? "넓습니다" : "좁습니다"} · 차이 ${Math.abs(r.area - o.area).toFixed(2)}㎡ (허용오차 0.5㎡).`,
    },
    {
      label: "층수",
      ad: r.floor == null ? "정보 없음" : `${r.floor}층`,
      official: o.floor == null ? "자료 없음" : `${o.floor}층`,
      comparable: r.floor != null && o.floor != null && o.scope === "호실",
      match: r.floor === o.floor,
      reason: r.floor == null || o.floor == null || o.scope !== "호실" ? "같은 호실의 공식 층수 자료가 있어야 비교할 수 있습니다."
        : r.floor === o.floor ? "같은 호실의 층수가 일치합니다." : `등록 ${r.floor}층 / 공식 ${o.floor}층 · ${Math.abs(r.floor - o.floor)}개 층 차이입니다.`,
    },
  ];
  if (o.buildingId) rows.unshift({
    label: "주소", ad: r.address, official: o.address || o.jibunAddress || "자료 없음",
    comparable: Boolean(o.address || o.jibunAddress), match: o.addressMatch === true,
    reason: o.addressMatch ? "등록 주소가 선택한 공식 도로명 또는 지번주소와 일치합니다. 동·호실은 선택한 대상을 별도로 확인하세요."
      : "등록 주소와 선택한 공식 주소가 다릅니다. 주소 오기·표기 방식 차이 또는 다른 건물 선택 여부를 확인해 주세요.",
  });
  if (o.buildingId && o.scope === "호실") {
    const ho = r.address.match(/(?:^|\s|,)([0-9A-Za-z-]+)호(?:\s|$)/)?.[1];
    const dong = r.address.match(/(?:^|\s|,)([0-9A-Za-z-]+)동(?:\s|$)/)?.[1];
    const clean = value => String(value || "").replace(/[\s동호]/g, "");
    const same = clean(ho) === clean(o.ho) && (!dong || clean(dong) === clean(o.dong));
    rows.splice(1, 0, {
      label: "동·호실", ad: ho ? `${dong ? `${dong}동 ` : ""}${ho}호` : "등록 주소에 호실 없음",
      official: `${o.dong || ""}동 ${o.ho || ""}`, comparable: Boolean(ho && o.ho), match: same,
      reason: !ho ? "등록 주소에 호실이 없어 선택한 공식 호실과의 동일 여부는 확인할 수 없습니다."
        : same ? "등록 주소의 동·호실과 선택한 공식 호실이 일치합니다."
        : "등록 주소의 동·호실과 선택한 공식 호실이 다릅니다. 조회 대상을 다시 확인해 주세요.",
    });
  }
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
/**
 * 건물 통계를 고른다. 서버 통계가 있고 그 건물에 브라우저 시연 후기가 없으면 서버 값을, 아니면 화면의 후기로 계산한다.
 * @param {Array} reviews 화면에 표시 중인 후기
 * @param {string} building 건물 키
 * @param {Object} [serverStats] 건물 키별 서버 통계
 * @return {{count: number, items: Array}} 후기 수와 항목별 평균
 */
export function buildingStatistics(reviews, building, serverStats) {
  const server = serverStats?.[building];
  if (server && !reviews.some((review) => review.building === building && isDemoReview(review))) return server;
  return statistics(reviews, building);
}
export function validateComparison(ids) {
  return ids.length >= 2 && ids.length <= 3 && new Set(ids).size === ids.length;
}

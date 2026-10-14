export const money = (v) =>
  v == null
    ? "정보 없음"
    : `${new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 1 }).format(v / 10000)}만`;

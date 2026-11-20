import { createApiClient } from "../../shared/lib/http.js";

/** 신고 사유 코드와 화면 문구. 서버 Report.Reason과 같은 값을 쓴다. */
export const reportReasons = [
  ["SPAM", "광고·도배"],
  ["ABUSE", "욕설·비방"],
  ["PRIVACY", "개인정보 노출"],
  ["FALSE_INFO", "허위 정보"],
  ["OTHER", "기타"],
];
export const reasonLabel = code => reportReasons.find(([value]) => value === code)?.[1] || code;
export const targetLabel = { POST: "게시글", COMMENT: "댓글", REVIEW: "거주 후기" };

/**
 * 신고 접수와 관리자 신고 처리 API 클라이언트를 만든다.
 * @param {Object} [options] API 기본 경로와 요청 함수
 * @return {Object} 신고 접수·목록·처리 요청 함수
 */
export function createReportApi(options) {
  const api = createApiClient(options);
  return {
    /** targetType은 POST·COMMENT·REVIEW, 댓글이면 postId도 보낸다. */
    report: ({ targetType, targetId, postId, reason, detail }) =>
      api("/reports", { method: "POST", json: { targetType, targetId, postId: postId || null, reason, detail: detail || null } }),
    list: ({ status = "PENDING", page = 0 } = {}) =>
      api(`/admin/reports?status=${encodeURIComponent(status)}&page=${page}&size=20`),
    resolve: (id, action, note) =>
      api(`/admin/reports/${encodeURIComponent(id)}/resolve`, { method: "POST", json: { action, note: note || null } }),
  };
}

import { useCallback, useEffect, useMemo, useState } from "react";
import { PageHeading } from "../../shared/ui/UI.jsx";
import { createReportApi, reasonLabel, targetLabel } from "./api.js";

const statuses = [["PENDING", "처리 대기"], ["ACCEPTED", "삭제 처리"], ["DISMISSED", "반려"], ["ALL", "전체"]];
const statusLabel = code => statuses.find(([value]) => value === code)?.[1] || code;
const dateTime = value => (value ? new Date(value).toLocaleString("ko-KR") : "—");

/** SFR-019 관리자 신고 처리 화면. 관리자가 아니면 서버가 403을 돌려준다. */
export default function ReportsPage({ member, api: providedApi }) {
  const api = useMemo(() => providedApi || createReportApi(), [providedApi]);
  const [status, setStatus] = useState("PENDING");
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [notes, setNotes] = useState({});
  const load = useCallback(async () => {
    setError("");
    try { setData(await api.list({ status, page })); } catch (failure) { setData(null); setError(failure.message); }
  }, [api, status, page]);
  useEffect(() => { if (member?.role === "ADMIN") load(); }, [load, member]);
  async function resolve(report, action) {
    setBusy(true); setError("");
    try { await api.resolve(report.id, action, notes[report.id]); await load(); }
    catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  if (member?.role !== "ADMIN") return <div className="empty large">
    <h2>관리자만 볼 수 있는 화면이에요</h2><p>관리자 계정으로 로그인해 주세요.</p>
  </div>;
  return <>
    <PageHeading kicker="MODERATION" title="신고 관리" description="신고된 게시글·댓글·후기를 확인하고 삭제하거나 반려하세요." />
    <div className="compare-picker" role="group" aria-label="처리 상태">
      {statuses.map(([value, label]) => <label key={value}>
        <input type="radio" name="report-status" checked={status === value} onChange={() => { setStatus(value); setPage(0); }} />{label}
      </label>)}
    </div>
    {error && <p role="alert">{error}</p>}
    {!data && !error && <p role="status">신고 목록을 불러오는 중이에요…</p>}
    {data && !data.content.length && <div className="empty">{statusLabel(status)} 신고가 없어요.</div>}
    {data?.content.map(report => <section className="panel report-item" key={report.id}>
      <div className="section-title">
        <div>
          <div className="eyebrow">{targetLabel[report.targetType]} · {reasonLabel(report.reason)}</div>
          <h2>{report.targetPreview || "(내용 없음)"}</h2>
        </div>
        <span className="badge muted">{statusLabel(report.status)}</span>
      </div>
      <p className="helper">
        신고 {dateTime(report.createdAt)} · 같은 대상 신고 {report.targetReportCount}건 · 작성자 회원 #{report.targetAuthorId ?? "?"}
        {!report.targetExists && " · 대상이 이미 삭제됨"}
      </p>
      {report.detail && <p>{report.detail}</p>}
      {report.status === "PENDING" ? <>
        <label className="field">처리 메모 (선택)
          <input maxLength="300" value={notes[report.id] || ""} disabled={busy}
            onChange={event => setNotes(value => ({ ...value, [report.id]: event.target.value }))} />
        </label>
        <div className="modal-actions">
          <button className="secondary" disabled={busy} onClick={() => resolve(report, "DISMISS")}>문제없음 (반려)</button>
          <button className="primary" disabled={busy} onClick={() => resolve(report, "DELETE_TARGET")}>
            {report.targetExists ? "대상 삭제" : "처리 완료"}
          </button>
        </div>
      </> : <p className="helper">처리 {dateTime(report.handledAt)}{report.handlerNote && ` · ${report.handlerNote}`}</p>}
    </section>)}
    {data && data.totalPages > 1 && <div className="modal-actions">
      <button className="secondary" disabled={data.first || busy} onClick={() => setPage(value => value - 1)}>이전</button>
      <span>{data.number + 1} / {data.totalPages}</span>
      <button className="secondary" disabled={data.last || busy} onClick={() => setPage(value => value + 1)}>다음</button>
    </div>}
  </>;
}

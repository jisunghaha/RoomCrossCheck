import { useMemo, useState } from "react";
import Modal from "../../shared/ui/Modal.jsx";
import { Field } from "../../shared/ui/UI.jsx";
import { createReportApi, reportReasons, targetLabel } from "./api.js";

/**
 * 게시글·댓글·후기에 붙이는 신고 버튼과 신고 입력 창.
 * 로그인하지 않았으면 서버가 401로 안내한다. 본인 글·중복 신고 여부도 서버가 판단한다.
 */
export default function ReportButton({ targetType, targetId, postId, api: providedApi, disabled = false }) {
  const api = useMemo(() => providedApi || createReportApi(), [providedApi]);
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("SPAM");
  const [detail, setDetail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);
  async function submit(event) {
    event.preventDefault();
    if (reason === "OTHER" && !detail.trim()) { setError("기타 사유는 설명을 입력해 주세요."); return; }
    setBusy(true); setError("");
    try {
      await api.report({ targetType, targetId, postId, reason, detail: detail.trim() });
      setDone(true);
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  function close() { setOpen(false); setError(""); }
  return <>
    <button type="button" className="text-button" disabled={disabled || done} onClick={() => setOpen(true)}>
      {done ? "신고 접수됨" : "신고"}
    </button>
    {open && <Modal title={`${targetLabel[targetType]} 신고`} onClose={close}>
      {done ? <>
        <h2>신고를 접수했어요</h2>
        <p>관리자가 확인한 뒤 삭제 또는 유지 여부를 결정합니다.</p>
        <div className="modal-actions"><button className="primary" onClick={close}>닫기</button></div>
      </> : <form onSubmit={submit}>
        <h2>{{ POST: "게시글을", COMMENT: "댓글을", REVIEW: "거주 후기를" }[targetType]} 신고할까요?</h2>
        <Field label="신고 사유 *">
          <select value={reason} onChange={event => setReason(event.target.value)} disabled={busy}>
            {reportReasons.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </Field>
        <Field label={reason === "OTHER" ? "설명 *" : "설명 (선택)"}>
          <textarea rows="3" maxLength="500" value={detail} disabled={busy}
            onChange={event => setDetail(event.target.value)} placeholder="관리자가 확인할 내용을 적어 주세요." />
        </Field>
        {error && <p role="alert">{error}</p>}
        <div className="modal-actions">
          <button type="button" className="secondary" onClick={close} disabled={busy}>취소</button>
          <button className="primary" disabled={busy}>{busy ? "접수 중…" : "신고하기"}</button>
        </div>
      </form>}
    </Modal>}
  </>;
}

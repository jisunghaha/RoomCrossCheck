import { useEffect, useRef, useState } from "react";
import { reviewedAdvertisementValues } from "../lib/advertisement.js";

const labels = {
  DRAFT: "임시 저장", EXTRACTION_PENDING: "추출 대기", EXTRACTION_READY: "추출 결과 확인 필요",
  EXTRACTION_FAILED: "추출 실패 · 재시도 가능", REVIEWED: "사용자 확인 완료",
};

function ReviewForm({ draft, busy, onSave }) {
  const result = draft.reviewed || draft.extraction;
  const number = (value) => value === null || value === undefined ? "" : value;
  return <form className="ad-review" onSubmit={event => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const numeric = name => data.get(name) === "" ? null : Number(data.get(name));
    onSave(draft.id, {
      rawText: data.get("rawText"), address: data.get("address") || null,
      depositWon: numeric("depositWon"), monthlyRentWon: numeric("monthlyRentWon"),
      maintenanceFeeWon: numeric("maintenanceFeeWon"), areaSqm: numeric("areaSqm"), floor: numeric("floor"),
    });
  }}>
    <p>AI 결과를 원본 광고와 비교하고 틀린 값을 고쳐 주세요. 빈칸은 확인되지 않은 값입니다.</p>
    <label>읽은 광고 글<textarea name="rawText" rows="3" maxLength="10000" defaultValue={result?.rawText || ""} /></label>
    <div className="form-grid">
      <label>주소<input name="address" maxLength="160" defaultValue={result?.address || ""} /></label>
      <label>보증금 (원)<input name="depositWon" type="number" min="0" max="1000000000000" step="1" defaultValue={number(result?.depositWon)} /></label>
      <label>월세 (원)<input name="monthlyRentWon" type="number" min="0" max="1000000000000" step="1" defaultValue={number(result?.monthlyRentWon)} /></label>
      <label>관리비 (원)<input name="maintenanceFeeWon" type="number" min="0" max="1000000000000" step="1" defaultValue={number(result?.maintenanceFeeWon)} /></label>
      <label>면적 (㎡)<input name="areaSqm" type="number" min="0" max="100000" step="any" defaultValue={number(result?.areaSqm)} /></label>
      <label>층수 (지하는 음수)<input name="floor" type="number" min="-20" max="200" step="1" defaultValue={number(result?.floor)} /></label>
    </div>
    <button className="secondary" type="submit" disabled={busy}>확인한 값 저장</button>
  </form>;
}

export default function AdDraftPanel({ onRegister }) {
  const [drafts, setDrafts] = useState([]);
  const [text, setText] = useState("");
  const [image, setImage] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const key = useRef(crypto.randomUUID());

  async function refresh() {
    const response = await fetch("/api/advertisements");
    if (!response.ok) throw new Error("광고 원본 목록을 불러오지 못했어요.");
    setDrafts(await response.json());
  }
  useEffect(() => { refresh().catch((cause) => setError(cause.message)); }, []);
  const changed = () => { key.current = crypto.randomUUID(); setError(""); };

  async function submit(event) {
    event.preventDefault();
    if (!text.trim() && !image) { setError("광고 글이나 이미지를 넣어 주세요."); return; }
    const form = event.currentTarget;
    const data = new FormData(form);
    if (!image) data.delete("image");
    setBusy(true); setError(""); setMessage("");
    try {
      const response = await fetch("/api/advertisements", {
        method: "POST", headers: { "Idempotency-Key": key.current }, body: data,
      });
      if (!response.ok) throw new Error(`원본을 저장하지 못했어요. (${response.status})`);
      const saved = await response.json();
      await refresh();
      setMessage(`원본을 임시 저장했어요. ID: ${saved.id}`);
      form.reset(); setText(""); setImage(null); key.current = crypto.randomUUID();
    } catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  }
  async function confirm(id) {
    setBusy(true); setError(""); setMessage("");
    try {
      const response = await fetch(`/api/advertisements/${encodeURIComponent(id)}/confirm`, { method: "POST" });
      if (!response.ok) throw new Error(`확정하지 못했어요. (${response.status})`);
      await refresh();
      setMessage("원본을 확정했어요. 준비되면 Gemini 추출을 시작할 수 있습니다.");
    } catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  }

  async function extract(id) {
    setBusy(true); setError(""); setMessage("");
    try {
      const response = await fetch(`/api/advertisements/${encodeURIComponent(id)}/extract`, { method: "POST" });
      if (!response.ok) {
        if (response.status === 503) throw new Error("서버에 Gemini 추출 설정이 없습니다. 로컬 환경변수를 확인해 주세요.");
        if (response.status === 429) throw new Error("Gemini 사용 한도에 도달했습니다. 나중에 다시 시도해 주세요.");
        throw new Error(`추출에 실패했어요. (${response.status})`);
      }
      await refresh();
      setMessage("추출 결과가 도착했어요. 원본과 비교해 확인해 주세요.");
    } catch (cause) { setError(cause.message); await refresh().catch(() => {}); }
    finally { setBusy(false); }
  }
  async function saveReview(id, result) {
    setBusy(true); setError(""); setMessage("");
    try {
      const response = await fetch(`/api/advertisements/${encodeURIComponent(id)}/extraction`, {
        method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(result),
      });
      if (!response.ok) throw new Error(`확인한 값을 저장하지 못했어요. (${response.status})`);
      await refresh();
      setMessage("확인한 추출 값을 저장했어요.");
    } catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  }

  return <section className="list-section ad-drafts">
    <div className="section-title"><h2>광고 원본 등록</h2><p>원본을 보관하고, 확정 후 Gemini 추출 결과를 확인합니다.</p></div>
    <form onSubmit={submit}>
      <label>광고 글<textarea name="text" value={text} maxLength="10000" rows="3" onChange={event => { setText(event.target.value); changed(); }} /></label>
      <div className="form-grid">
        <label>주소 힌트<input name="addressHint" maxLength="160" onChange={changed} /></label>
        <label>동·호수<input name="unitLabel" maxLength="40" onChange={changed} /></label>
        <label>출처 URL<input name="sourceUrl" type="url" maxLength="1000" onChange={changed} /></label>
        <label>광고 이미지 (JPG·PNG, 최대 10MB)<input name="image" type="file" accept=".jpg,.jpeg,.png,image/jpeg,image/png" onChange={event => { setImage(event.target.files?.[0] || null); changed(); }} /></label>
      </div>
      <button className="primary" disabled={busy} type="submit">{busy ? "저장 중…" : "광고 원본 임시 저장"}</button>
    </form>
    {error && <p role="alert">{error}</p>}
    {message && <p role="status">{message}</p>}
    {drafts.length > 0 && <div className="ad-draft-list">
      {drafts.map(draft => <article key={draft.id}>
        <strong>{draft.addressHint || draft.imageName || draft.originalText?.slice(0, 30) || "주소 확인 필요"}</strong>
        <span className="badge muted">{labels[draft.status] || draft.status}</span>
        <p>{draft.imageName ? `이미지: ${draft.imageName}` : "글 광고"} · {draft.sourceUrl || "출처 URL 미입력"}</p>
        {draft.status === "DRAFT" && <button className="secondary" disabled={busy} onClick={() => confirm(draft.id)}>원본 확정</button>}
        {["EXTRACTION_PENDING", "EXTRACTION_FAILED"].includes(draft.status) && <button className="secondary" disabled={busy} onClick={() => extract(draft.id)}>{draft.status === "EXTRACTION_FAILED" ? "Gemini 추출 재시도" : "Gemini 추출 시작"}</button>}
        {draft.extractionError && <p role="status">이전 추출 오류: {draft.extractionError}</p>}
        {["EXTRACTION_READY", "REVIEWED"].includes(draft.status) && <ReviewForm key={`${draft.id}-${draft.status}-${draft.reviewedAt || ""}`} draft={draft} busy={busy} onSave={saveReview} />}
        {onRegister && reviewedAdvertisementValues(draft) && <>
          <p className="helper">마지막으로 저장한 확인 값으로 매물 입력을 시작합니다. 수정 중인 값은 먼저 저장해 주세요.</p>
          <button className="secondary" disabled={busy} onClick={() => onRegister(reviewedAdvertisementValues(draft), draft.id)}>확인한 값으로 매물 등록</button>
        </>}
      </article>)}
    </div>}
  </section>;
}

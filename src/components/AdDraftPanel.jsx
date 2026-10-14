import { useEffect, useRef, useState } from "react";

const labels = { DRAFT: "임시 저장", EXTRACTION_PENDING: "추출 대기 · OCR 미연결" };

export default function AdDraftPanel() {
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
      setMessage("원본을 확정했어요. OCR·AI 추출은 아직 연결되지 않았습니다.");
    } catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  }

  return <section className="list-section ad-drafts">
    <div className="section-title"><h2>광고 원본 등록</h2><p>OCR 전 단계로 글·이미지를 보관합니다.</p></div>
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
      </article>)}
    </div>}
  </section>;
}

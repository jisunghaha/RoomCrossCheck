import { useEffect, useRef, useState } from "react";
import OptionsField from "../../../shared/ui/OptionsField.jsx";
import { parseOptions, optionsText } from "../../../shared/lib/options.js";
import { reviewedAdvertisementValues } from "../model/reviewedAdvertisement.js";
import ExtractionEvidence from "./ExtractionEvidence.jsx";
import { useUnsavedChanges } from "../../../app/routing/unsavedChanges.js";
import ReviewHistory from "./ReviewHistory.jsx";

const labels = {
  DRAFT: "임시 저장", EXTRACTION_PENDING: "추출 대기", EXTRACTION_READY: "추출 결과 확인 필요",
  EXTRACTION_FAILED: "추출 실패 · 재시도 가능", REVIEWED: "사용자 확인 완료",
};

async function uploadError(response) {
  const fallback = {
    400: "광고 글과 입력 항목, JPG·PNG 이미지 형식을 확인해 주세요.",
    409: "같은 요청의 내용이 달라 저장하지 못했습니다. 입력 내용을 확인해 주세요.",
    413: "업로드 용량을 초과했습니다. 광고 이미지는 10MB 이하로 올려 주세요.",
    415: "JPG·PNG 이미지 형식으로 올려 주세요.",
  };
  if (fallback[response.status]) {
    try {
      const body = await response.json();
      if (typeof body.message === "string" && body.message.trim() && body.message.length <= 300)
        return body.message;
    } catch { /* A proxy may return an empty or HTML error response. */ }
    return fallback[response.status];
  }
  return "원본을 저장하지 못했습니다. 입력 내용은 유지됩니다. 잠시 후 다시 시도해 주세요.";
}

async function extractionError(response) {
  const failures = {
    CONFIGURATION_MISSING: [503, "Gemini 추출 설정이 없습니다. 서버 설정을 확인해 주세요."],
    AUTHENTICATION_FAILED: [502, "AI 서비스 인증 또는 접근 권한을 확인해 주세요."],
    RATE_LIMITED: [429, "AI 서비스 사용 한도에 도달했습니다. 잠시 후 다시 시도해 주세요."],
    PROVIDER_UNAVAILABLE: [502, "AI 서비스가 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."],
    TIMEOUT: [504, "AI 서비스 응답 시간이 초과됐습니다. 잠시 후 다시 시도해 주세요."],
    CONNECTION_FAILED: [502, "AI 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."],
    INVALID_RESPONSE: [502, "AI 응답 형식이 올바르지 않아 결과를 저장하지 않았습니다. 다시 시도해 주세요."],
    NO_READABLE_TEXT: [502, "광고에서 읽을 수 있는 글자를 찾지 못했습니다. 더 선명한 이미지나 광고 글로 다시 등록해 주세요."],
    INVALID_VALUES: [502, "AI가 허용 범위 밖의 값을 반환해 결과를 저장하지 않았습니다. 다시 시도해 주세요."],
    SOURCE_UNAVAILABLE: [500, "저장된 광고 이미지를 읽지 못했습니다. 원본 보관 상태를 확인해 주세요."],
    INTERRUPTED: [502, "AI 추출 요청이 중단됐습니다. 잠시 후 다시 시도해 주세요."],
  };
  try {
    const body = await response.json();
    const known = Object.hasOwn(failures, body?.code) ? failures[body.code] : null;
    if (known && known[0] === response.status) return known[1];
  } catch { /* Unknown bodies are never displayed as provider error details. */ }
  if (response.status === 503) return failures.CONFIGURATION_MISSING[1];
  if (response.status === 429) return failures.RATE_LIMITED[1];
  if (response.status === 504) return failures.TIMEOUT[1];
  if (response.status === 409) return "추출 상태가 변경됐거나 이미 추출 중입니다. 목록을 확인한 뒤 다시 시도해 주세요.";
  return "AI 추출을 완료하지 못했습니다. 원본은 보존됩니다. 잠시 후 다시 시도해 주세요.";
}

function ReviewForm({ draft, busy, onSave, onDirty }) {
  const result = draft.reviewed || draft.extraction;
  const [optionText, setOptionText] = useState(() => optionsText(result?.options));
  const [noOptions, setNoOptions] = useState(() => Array.isArray(result?.options) && result.options.length === 0);
  const [optionError, setOptionError] = useState("");
  const formRef = useRef(null);
  const initial = useRef(null);
  const [dirty, setDirty] = useState(false);
  useUnsavedChanges(dirty);
  useEffect(() => {
    initial.current = JSON.stringify([...new FormData(formRef.current)]);
    return () => onDirty(draft.id, false);
  }, []);
  const trackChanges = () => {
    const changed = JSON.stringify([...new FormData(formRef.current)]) !== initial.current;
    setDirty(changed);
    onDirty(draft.id, changed);
  };
  // Options can disable their textarea, so compare after React has updated the DOM as well.
  useEffect(() => { if (initial.current !== null) trackChanges(); }, [optionText, noOptions]);
  const number = (value) => value === null || value === undefined ? "" : value;
  return <form ref={formRef} className="ad-review" onChange={trackChanges} onSubmit={event => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const numeric = name => data.get(name) === "" ? null : Number(data.get(name));
    let options;
    try { options = parseOptions(optionText, noOptions); setOptionError(""); }
    catch (cause) { setOptionError(cause.message); return; }
    onSave(draft.id, {
      rawText: data.get("rawText"), address: data.get("address") || null,
      depositWon: numeric("depositWon"), monthlyRentWon: numeric("monthlyRentWon"),
      maintenanceFeeWon: numeric("maintenanceFeeWon"), areaSqm: numeric("areaSqm"), floor: numeric("floor"),
      areaType: data.get("areaType"), options,
    }, draft.reviewVersion ?? 0);
  }}>
    <fieldset disabled={busy} className="data-operation-boundary">
    <p>AI 결과를 원본 광고와 비교하고 틀린 값을 고쳐 주세요. 빈칸은 확인되지 않은 값입니다. 면적 종류가 불명확하면 미상으로 유지해 주세요.</p>
    <ExtractionEvidence draft={draft} />
    <label>읽은 광고 글<textarea name="rawText" rows="3" maxLength="10000" defaultValue={result?.rawText || ""} /></label>
    <div className="form-grid">
      <label>주소<input name="address" maxLength="160" defaultValue={result?.address || ""} /></label>
      <label>보증금 (원)<input name="depositWon" type="number" min="0" max="1000000000000" step="1" defaultValue={number(result?.depositWon)} /></label>
      <label>월세 (원)<input name="monthlyRentWon" type="number" min="0" max="1000000000000" step="1" defaultValue={number(result?.monthlyRentWon)} /></label>
      <label>관리비 (원)<input name="maintenanceFeeWon" type="number" min="0" max="1000000000000" step="1" defaultValue={number(result?.maintenanceFeeWon)} /></label>
      <label>면적 (㎡)<input name="areaSqm" type="number" min="0" max="100000" step="any" defaultValue={number(result?.areaSqm)} /></label>
      <label>면적 종류<select name="areaType" defaultValue={result?.areaType ?? "미상"}>
        {["전용", "공급", "미상"].map(type => <option key={type} value={type}>{type}</option>)}
      </select></label>
      <label>층수 (지하는 음수)<input name="floor" type="number" min="-20" max="200" step="1" defaultValue={number(result?.floor)} /></label>
    </div>
    <OptionsField text={optionText} none={noOptions} onTextChange={setOptionText} onNoneChange={setNoOptions} />
    {optionError && <p role="alert">{optionError}</p>}
    {dirty && <p role="status">저장하지 않은 확인값이 있습니다. 먼저 저장해 주세요.</p>}
    <button className="secondary" type="submit" disabled={busy}>확인한 값 저장</button>
    </fieldset>
  </form>;
}

export default function AdDraftPanel({ onRegister }) {
  const [drafts, setDrafts] = useState([]);
  const [text, setText] = useState("");
  const [image, setImage] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [dirtyIds, setDirtyIds] = useState(() => new Set());
  const [conflictId, setConflictId] = useState(null);
  const markDirty = (id, dirty) => setDirtyIds(previous => {
    if (previous.has(id) === dirty) return previous;
    const next = new Set(previous);
    if (dirty) next.add(id); else next.delete(id);
    return next;
  });
  const key = useRef(crypto.randomUUID());

  async function refresh() {
    const response = await fetch("/api/advertisements");
    if (!response.ok) throw new Error("광고 원본 목록을 불러오지 못했어요.");
    const received = await response.json();
    // Refreshing another ad must not silently discard edits in this form.
    setDrafts(previous => received.map(draft => dirtyIds.has(draft.id)
      ? previous.find(saved => saved.id === draft.id) ?? draft : draft));
  }
  useEffect(() => { refresh().catch((cause) => setError(cause.message)); }, []);
  const changed = () => { key.current = crypto.randomUUID(); setError(""); };

  async function submit(event) {
    event.preventDefault();
    if (!text.trim() && !image) { setError("광고 글이나 이미지를 넣어 주세요."); return; }
    if (image && image.size > 10 * 1024 * 1024) {
      setError("광고 이미지는 10MB 이하로 올려 주세요.");
      return;
    }
    const form = event.currentTarget;
    const data = new FormData(form);
    if (!image) data.delete("image");
    setBusy(true); setError(""); setMessage("");
    try {
      let response;
      try {
        response = await fetch("/api/advertisements", {
          method: "POST", headers: { "Idempotency-Key": key.current }, body: data,
        });
      } catch {
        throw new Error("서버에 연결하지 못했습니다. 입력 내용은 유지됩니다. 연결을 확인하고 다시 시도해 주세요.");
      }
      if (!response.ok) throw new Error(await uploadError(response));
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
      let response;
      try {
        response = await fetch(`/api/advertisements/${encodeURIComponent(id)}/extract`, { method: "POST" });
      } catch {
        throw new Error("추출 서버에 연결하지 못했습니다. 원본은 보존됩니다. 연결을 확인하고 다시 시도해 주세요.");
      }
      if (!response.ok) throw new Error(await extractionError(response));
      await refresh();
      setMessage("추출 결과가 도착했어요. 원본과 비교해 확인해 주세요.");
    } catch (cause) { setError(cause.message); await refresh().catch(() => {}); }
    finally { setBusy(false); }
  }
  async function saveReview(id, result, version) {
    setBusy(true); setError(""); setMessage("");
    try {
      const response = await fetch(`/api/advertisements/${encodeURIComponent(id)}/extraction`, {
        method: "PUT", headers: { "Content-Type": "application/json", "X-Review-Version": String(version) }, body: JSON.stringify(result),
      });
      if (!response.ok) {
        if ([409, 428].includes(response.status)) {
          setConflictId(id);
          throw new Error("다른 화면에서 확인값이 변경됐거나 최신 버전이 필요합니다. 입력은 유지됩니다. 최신값을 불러온 뒤 다시 확인해 주세요.");
        }
        throw new Error(response.status === 400 ? "입력값의 형식과 허용 범위를 확인해 주세요. 수정 내용은 유지됩니다." : "확인값을 저장하지 못했습니다. 수정 내용은 유지됩니다. 잠시 후 다시 시도해 주세요.");
      }
      const saved = await response.json();
      setDrafts(previous => previous.map(draft => draft.id === id ? saved : draft));
      markDirty(id, false);
      setConflictId(null);
      setMessage("확인한 추출 값을 저장했어요.");
    } catch (cause) { setError(cause.message); }
    finally { setBusy(false); }
  }

  async function reloadConflicted(id) {
    if (dirtyIds.has(id) && !window.confirm("수정 중인 값을 버리고 서버의 최신 확인값을 불러올까요?")) return;
    setBusy(true); setError("");
    try {
      const response = await fetch(`/api/advertisements/${encodeURIComponent(id)}`);
      if (!response.ok) throw new Error("최신값을 불러오지 못했습니다. 입력은 유지됩니다.");
      const latest = await response.json();
      setDrafts(previous => previous.map(draft => draft.id === id ? latest : draft));
      markDirty(id, false); setConflictId(null);
    } catch { setError("최신값을 불러오지 못했습니다. 입력은 유지됩니다. 다시 시도해 주세요."); }
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
        {["EXTRACTION_READY", "REVIEWED"].includes(draft.status) && <ReviewForm key={`${draft.id}-${draft.status}-${draft.reviewVersion ?? 0}-${draft.reviewedAt || ""}`} draft={draft} busy={busy} onSave={saveReview} onDirty={markDirty} />}
        {conflictId === draft.id && <button className="secondary" disabled={busy} onClick={() => reloadConflicted(draft.id)}>최신값 불러오기</button>}
        {draft.status === "REVIEWED" && <ReviewHistory key={`history-${draft.id}-${draft.reviewVersion ?? 0}`} id={draft.id} />}
        {onRegister && reviewedAdvertisementValues(draft) && <>
          <p className="helper">마지막으로 저장한 확인 값으로 매물 입력을 시작합니다. 수정 중인 값은 먼저 저장해 주세요.</p>
          <button className="secondary" disabled={busy || dirtyIds.size > 0} onClick={() => onRegister(reviewedAdvertisementValues(draft), draft.id)}>확인한 값으로 매물 등록</button>
        </>}
      </article>)}
    </div>}
  </section>;
}

import { useState } from "react";
import { optionsSummary } from "../../../shared/lib/options.js";

const fields = [["address", "주소"], ["depositWon", "보증금 (원)"], ["monthlyRentWon", "월세 (원)"],
  ["maintenanceFeeWon", "관리비 (원)"], ["areaSqm", "면적 (㎡)"], ["areaType", "면적 종류"], ["floor", "층수"]];

export default function ReviewHistory({ id }) {
  const [entries, setEntries] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function load() {
    setBusy(true); setError("");
    try {
      const response = await fetch(`/api/advertisements/${encodeURIComponent(id)}/reviews`);
      if (!response.ok) throw new Error();
      const data = await response.json();
      if (!Array.isArray(data)) throw new Error();
      setEntries(data);
    } catch { setError("수정 이력을 불러오지 못했습니다. 다시 시도해 주세요."); }
    finally { setBusy(false); }
  }
  return <section aria-label="확인값 수정 이력">
    <button type="button" className="text-button" disabled={busy} onClick={() => entries === null ? load() : setEntries(null)}>
      {busy ? "이력 불러오는 중…" : entries === null ? "수정 이력 보기" : "수정 이력 닫기"}
    </button>
    {error && <p role="alert">{error}</p>}
    {entries && <>
      <p className="helper">최근 저장 이력 최대 20건입니다. AI 최초 추출값은 위 근거 표에 별도로 보존됩니다.</p>
      {entries.length === 0 && <p>이력 기록 도입 전에 저장한 값입니다. 다음 확인값 저장부터 이력이 남습니다.</p>}
      {entries.map(entry => <details key={entry.version}>
        <summary>{entry.version === 0 ? "이력 도입 전 마지막 저장값" : `저장 버전 ${entry.version}`} · {entry.reviewedAt ? new Date(entry.reviewedAt).toLocaleString("ko-KR") : "저장 시각 미기록"}</summary>
        <dl className="confirmation-grid">
          {fields.map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{entry.reviewed?.[key] ?? "미확인"}</dd></div>)}
          <div><dt>옵션</dt><dd>{optionsSummary(entry.reviewed?.options)}</dd></div>
        </dl>
        <p>확인한 광고 글</p><pre style={{ whiteSpace: "pre-wrap" }}>{entry.reviewed?.rawText || "미확인"}</pre>
      </details>)}
    </>}
  </section>;
}

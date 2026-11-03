import { useRef, useState } from "react";
import { flushSync } from "react-dom";
import { useRooms } from "../../../app/state/RoomContext.jsx";
import { Field } from "../../../shared/ui/UI.jsx";

let postcodeScript;
function loadPostcode() {
  if (window.kakao?.Postcode || window.daum?.Postcode) return Promise.resolve();
  if (!postcodeScript) postcodeScript = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";
    const timeout = setTimeout(() => { script.remove(); postcodeScript = null; reject(new Error("주소 검색 연결이 지연됩니다. 다시 시도하거나 지번을 직접 입력해 주세요.")); }, 15000);
    script.onload = () => { clearTimeout(timeout); resolve(); };
    script.onerror = () => { clearTimeout(timeout); postcodeScript = null; script.remove(); reject(new Error("주소 검색을 불러오지 못했습니다. 연결을 확인하거나 지번을 직접 입력해 주세요.")); };
    document.head.appendChild(script);
  });
  return postcodeScript;
}

export default function OfficialLookup({ room }) {
  const { dataMode, searchOfficial, saveOfficial } = useRooms();
  const [query, setQuery] = useState({ legalCode: "", bun: "", ji: "0", landType: "0" });
  const [address, setAddress] = useState("");
  const [result, setResult] = useState(null);
  const [buildingId, setBuildingId] = useState("");
  const [unitId, setUnitId] = useState("");
  const [ho, setHo] = useState(() => room.address.match(/([0-9A-Za-z-]+호)/)?.[1] || "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [pickerOpen, setPickerOpen] = useState(false);
  const picker = useRef(null);
  const requestBusy = useRef(false);
  if (dataMode !== "listing-api") return <p className="helper">공식 조회는 서버에 저장한 매물에서 사용할 수 있습니다.</p>;

  async function openAddress() {
    setError(""); setBusy(true); flushSync(() => setPickerOpen(true));
    try {
      await loadPostcode();
      const Postcode = window.kakao?.Postcode || window.daum?.Postcode;
      new Postcode({ width: "100%", height: "100%", oncomplete(data) {
        const jibun = data.jibunAddress;
        const lot = jibun?.match(/(?:^|\s)(산\s*)?(\d+)(?:-(\d+))?(?:번지)?$/);
        if (!lot || !/^\d{10}$/.test(data.bcode)) {
          setError("지번을 확정할 수 없는 주소입니다. 검색에서 지번주소를 선택하거나 직접 입력해 주세요.");
          return;
        }
        setQuery({ legalCode: data.bcode, bun: lot[2], ji: lot[3] || "0", landType: lot[1] ? "1" : "0" });
        setAddress(data.roadAddress || jibun); setResult(null); setBuildingId(""); setUnitId(""); setPickerOpen(false);
      } }).embed(picker.current, { q: room.address.replace(/(?:,\s*)?\s+[0-9A-Za-z-]+(?:동|층|호)(?=\s|$)/g, "").trim() });
    } catch (e) { setError(e.message); setPickerOpen(false); }
    finally { setBusy(false); }
  }
  async function lookup(withUnit = false) {
    if (requestBusy.current) return;
    requestBusy.current = true; setBusy(true); setError(""); setResult(null); setUnitId("");
    if (!withUnit) setBuildingId("");
    try {
      const next = await searchOfficial(room.id, { ...query, ...(withUnit ? { buildingId, ho: ho.trim() } : {}) });
      setResult(next);
      if (next.buildings.length === 1) setBuildingId(next.buildings[0].id);
      if (next.units.length === 1) setUnitId(next.units[0].id);
    } catch (e) { setError(e.message || "공식 조회에 실패했습니다. 다시 시도해 주세요."); }
    finally { requestBusy.current = false; setBusy(false); }
  }
  async function save(buildingOnly = false) {
    if (!result || requestBusy.current) return;
    requestBusy.current = true; setBusy(true); setError("");
    try {
      const ok = await saveOfficial(room.id, { searchId: result.searchId, buildingId, unitId: buildingOnly ? null : unitId || null });
      if (ok) { setResult(null); setBuildingId(""); setUnitId(""); }
      else setError("공식 정보를 저장하지 못했습니다. 다시 조회한 뒤 저장해 주세요.");
    } finally { requestBusy.current = false; setBusy(false); }
  }
  const change = key => e => { setQuery(q => ({ ...q, [key]: e.target.value })); setAddress(""); setResult(null); setBuildingId(""); setUnitId(""); };
  return <section className="official-lookup" aria-label="건축물대장 조회">
    <h3>건축물대장으로 등록 정보 확인</h3>
    <p>등록 주소: <strong>{room.address}</strong></p>
    <p className="helper">등록 주소로 검색한 뒤 정확한 건물을 선택하세요. 등록한 주소·면적·층수는 변경하지 않고 공식 기록과 비교합니다.</p>
    <fieldset disabled={busy} aria-busy={busy}>
      <button type="button" className="secondary" onClick={openAddress}>등록 주소로 검색</button>
      {pickerOpen && <div className="official-address-picker">
        <button type="button" className="text-button" onClick={() => setPickerOpen(false)}>주소 검색 닫기</button>
        <div ref={picker} style={{ height: 420 }} />
      </div>}
      {address && <p className="helper">선택한 조회 주소: {address}</p>}
      <details className="official-manual">
        <summary>지번으로 직접 조회하기</summary>
        <p className="helper">주소 검색을 사용할 수 없을 때 입력하세요. 도로명 건물번호가 아닌 지번입니다.</p>
        <div className="form-grid">
          <Field label="법정동코드 10자리"><input value={query.legalCode} onChange={change("legalCode")} inputMode="numeric" maxLength={10} placeholder="예: 1168010100" /></Field>
          <Field label="지번 본번"><input value={query.bun} onChange={change("bun")} inputMode="numeric" maxLength={4} placeholder="예: 737" /></Field>
          <Field label="지번 부번"><input value={query.ji} onChange={change("ji")} inputMode="numeric" maxLength={4} placeholder="없으면 0" /></Field>
          <Field label="토지 구분"><select value={query.landType} onChange={change("landType")}><option value="0">일반</option><option value="1">산</option></select></Field>
        </div>
      </details>
      <button type="button" className="primary" disabled={!/^\d{10}$/.test(query.legalCode) || !/^\d{1,4}$/.test(query.bun) || !/^\d{1,4}$/.test(query.ji)} onClick={() => lookup()}>건축물대장 조회</button>
      {result && <>
        <p role="status">{result.message}</p>
        {result.buildings.map(b => <label className="official-choice" key={b.id}>
          <input type="radio" name={`building-${room.id}`} checked={buildingId === b.id} onChange={() => { setBuildingId(b.id); setUnitId(""); }} />
          <span><strong>{b.name || "건물명 없음"} {b.dong}</strong><br />{b.address || b.jibunAddress}<br /><small>{b.jibunAddress} · {b.use || "용도 정보 없음"} · 지상 {b.aboveGroundFloors ?? "—"}층 / 지하 {b.undergroundFloors ?? "—"}층</small></span>
        </label>)}
        {buildingId && <>
          <Field label="비교할 호실"><input value={ho} maxLength={40} onChange={e => { setHo(e.target.value); setUnitId(""); setResult(r => r ? { ...r, units: [] } : r); }} placeholder="예: 301호" /></Field>
          <button type="button" className="secondary" disabled={!ho.trim()} onClick={() => lookup(true)}>선택한 건물의 호실 조회</button>
          {result.units.map(u => <label className="official-choice" key={u.id}>
            <input type="radio" name={`unit-${room.id}`} checked={unitId === u.id} onChange={() => setUnitId(u.id)} />
            <span>{u.dong}동 {u.ho} · 전용 {u.area ?? "미상"}㎡ · {u.floor ?? "미상"}층<br /><small>{u.reason}</small></span>
          </label>)}
          <div className="official-actions">
            {unitId && <button type="button" className="primary" onClick={() => save()}>이 호실로 비교 결과 저장</button>}
            <button type="button" className="secondary" onClick={() => save(true)}>건물 정보만 저장 · 호실 비교 제외</button>
          </div>
        </>}
      </>}
    </fieldset>
    {busy && <p role="status">공식 정보를 처리하고 있어요…</p>}
    {error && <p role="alert" className="form-error">{error}</p>}
    <p className="helper">보증금·월세·관리비는 건축물대장으로 확인할 수 없습니다. 호실 자료가 없는 건물은 면적·층수를 비교하지 않습니다.</p>
  </section>;
}

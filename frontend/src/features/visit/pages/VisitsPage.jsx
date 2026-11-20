import { useState } from "react";
import { useRooms } from "../../../app/state/RoomContext.jsx";
import { audit, checkedCount, checklistItems, defaultChecklist } from "../../../shared/lib/domain.js";
import { Field, Icon, PageHeading } from "../../../shared/ui/UI.jsx";
import { randomId } from "../../../shared/lib/id.js";

const MAX_ITEMS = 30;

/** SFR-009 체크리스트 구성 편집기. 항목 ID는 기존 체크 결과와 연결되므로 이름을 바꿔도 유지한다. */
function ChecklistEditor({ items, custom, onClose }) {
  const { saveChecklist, saving } = useRooms();
  const [draft, setDraft] = useState(() => items.map((item) => ({ ...item })));
  const [error, setError] = useState("");
  const update = (index, key, value) =>
    setDraft((rows) => rows.map((row, i) => (i === index ? { ...row, [key]: value } : row)));
  const move = (index, offset) => setDraft((rows) => {
    const next = [...rows];
    const target = index + offset;
    if (target < 0 || target >= next.length) return rows;
    [next[index], next[target]] = [next[target], next[index]];
    return next;
  });
  async function save() {
    const cleaned = draft.map((row) => ({ ...row, label: row.label.trim(), tip: (row.tip || "").trim() }));
    if (!cleaned.length) { setError("항목을 하나 이상 남겨 주세요."); return; }
    if (cleaned.some((row) => !row.label)) { setError("항목 이름을 모두 입력해 주세요."); return; }
    if (await saveChecklist(cleaned)) onClose();
  }
  async function reset() {
    if (await saveChecklist(null)) onClose();
  }
  return (
    <section className="panel checklist-editor" aria-label="체크리스트 구성">
      <div className="section-title">
        <div>
          <div className="eyebrow">MY CHECKLIST</div>
          <h2>체크리스트 구성</h2>
        </div>
        <span className="badge muted">{draft.length} / {MAX_ITEMS}개</span>
      </div>
      <p className="helper">내게 중요한 항목을 추가하거나 이름을 바꿔 보세요. 모든 매물의 방문 기록에 같은 항목이 적용됩니다.</p>
      {draft.map((row, index) => (
        <div className="form-grid checklist-row" key={row.id}>
          <Field label={`항목 ${index + 1} 이름`}>
            <input value={row.label} maxLength="30" onChange={(e) => update(index, "label", e.target.value)} />
          </Field>
          <Field label="확인 방법 (선택)">
            <input value={row.tip || ""} maxLength="80" onChange={(e) => update(index, "tip", e.target.value)} />
          </Field>
          <div className="modal-actions">
            <button type="button" className="text-button" onClick={() => move(index, -1)} disabled={index === 0}
              aria-label={`${row.label || "항목"} 위로`}>위로</button>
            <button type="button" className="text-button" onClick={() => move(index, 1)} disabled={index === draft.length - 1}
              aria-label={`${row.label || "항목"} 아래로`}>아래로</button>
            <button type="button" className="text-button danger" aria-label={`${row.label || "항목"} 삭제`}
              onClick={() => setDraft((rows) => rows.filter((_, i) => i !== index))}>삭제</button>
          </div>
        </div>
      ))}
      <button type="button" className="secondary" disabled={draft.length >= MAX_ITEMS}
        onClick={() => setDraft((rows) => [...rows, { id: `c-${randomId().slice(0, 8)}`, label: "", tip: "" }])}>
        <Icon name="plus" /> 항목 추가
      </button>
      {error && <p role="alert">{error}</p>}
      <div className="modal-actions">
        {custom && <button type="button" className="text-button danger" onClick={reset} disabled={saving}>기본 항목으로 되돌리기</button>}
        <button type="button" className="secondary" onClick={onClose} disabled={saving}>취소</button>
        <button type="button" className="primary" onClick={save} disabled={saving}>구성 저장</button>
      </div>
    </section>
  );
}

function VisitEditor({ room, items }) {
  const { visits, saveVisit } = useRooms();
  const [draft, setDraft] = useState(
    () => ({ checks: {}, note: "", visitedOn: "", ...visits[room.id] }),
  );
  return (
    <>
      <div className="notice">
        {audit(room).rate === 100
          ? "공식정보가 일치해도 방음·수압·관리 상태는 직접 확인하세요."
          : "확인 질문: 면적·층수의 공식 기록과 관리비 포함 항목을 담당자에게 확인하세요."}
      </div>
      <Field label="방문일">
        <input type="date" value={draft.visitedOn || ""}
          onChange={(e) => setDraft((d) => ({ ...d, visitedOn: e.target.value }))} />
      </Field>
      <div className="check-grid">
        {items.map((item) => (
          <label className="check-row" key={item.id}>
            <input
              type="checkbox"
              checked={!!draft.checks[item.id]}
              onChange={(e) =>
                setDraft((d) => ({
                  ...d,
                  checks: { ...d.checks, [item.id]: e.target.checked },
                }))
              }
            />
            <span>
              {item.label}
              {item.tip && <small>{item.tip}</small>}
            </span>
            <span className="check-state">
              {draft.checks[item.id] ? "확인 완료" : "미확인"}
            </span>
          </label>
        ))}
      </div>
      <Field label="나만의 방문 메모">
        <textarea
          rows="4"
          maxLength="2000"
          placeholder="직접 관찰한 내용과 안내받은 내용을 구분해서 적어주세요."
          value={draft.note}
          onChange={(e) => setDraft((d) => ({ ...d, note: e.target.value }))}
        />
      </Field>
      <button className="primary" onClick={() => saveVisit(room.id, draft)}>
        <Icon name="check" />
        방문 기록 저장
      </button>
      <p className="helper">
        매물 이동 전 저장 버튼을 눌러 주세요.
        공개 후기와 공유되지 않습니다.
      </p>
    </>
  );
}

/** SFR-010 저장한 방문 기록을 한눈에 보여 주는 목록. 항목을 누르면 해당 매물 기록으로 이동한다. */
function VisitHistory({ rooms, visits, items, active, onSelect }) {
  const recorded = rooms.filter((r) => visits[r.id]);
  return (
    <section className="panel visit-history" aria-label="내 방문 기록">
      <div className="section-title">
        <div>
          <div className="eyebrow">MY VISITS</div>
          <h2>내 방문 기록</h2>
        </div>
        <span className="badge muted">{recorded.length}건</span>
      </div>
      {recorded.length ? (
        <ul className="visit-history-list">
          {recorded.map((r) => {
            const visit = visits[r.id];
            return (
              <li key={r.id}>
                <button type="button" className={`text-button ${r.id === active ? "active" : ""}`} onClick={() => onSelect(r.id)}>
                  <strong>{r.name}</strong>
                </button>
                <small>
                  {visit.visitedOn ? `${visit.visitedOn} 방문` : "방문일 미입력"} ·
                  확인 {checkedCount(visit, items)} / {items.length}개
                  {visit.note ? ` · ${visit.note.slice(0, 40)}${visit.note.length > 40 ? "…" : ""}` : ""}
                </small>
              </li>
            );
          })}
        </ul>
      ) : (
        <p className="helper">아직 저장한 방문 기록이 없어요.</p>
      )}
    </section>
  );
}

export default function VisitsPage({ initialRoom }) {
  const { rooms, visits, checklist } = useRooms();
  const [active, setActive] = useState(initialRoom || rooms[0]?.id);
  const [editing, setEditing] = useState(false);
  const room = rooms.find((r) => r.id === active) || rooms[0];
  const items = checklistItems(checklist);
  return (
    <>
      <PageHeading
        kicker="VISIT NOTES"
        title="직접 보고, 꼼꼼히 기록."
        description="방문에서 확인한 내용을 나만의 체크리스트에 남겨보세요."
      >
        {!editing && (
          <button className="secondary" onClick={() => setEditing(true)}>체크리스트 구성</button>
        )}
      </PageHeading>
      {editing && (
        <ChecklistEditor items={items} custom={items !== defaultChecklist} onClose={() => setEditing(false)} />
      )}
      {room ? (
        <>
          <section className="panel visit-panel">
            <Field label="방문할 매물">
              <select value={room.id} onChange={(e) => setActive(e.target.value)}>
                {rooms.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.name}
                  </option>
                ))}
              </select>
            </Field>
            <VisitEditor key={`${room.id}:${items.map((item) => item.id).join(",")}`} room={room} items={items} />
          </section>
          <VisitHistory rooms={rooms} visits={visits} items={items} active={room.id} onSelect={setActive} />
        </>
      ) : (
        <div className="empty">먼저 매물을 등록해 주세요.</div>
      )}
    </>
  );
}

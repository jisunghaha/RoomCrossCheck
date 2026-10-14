import { useState } from "react";
import { useRooms } from "../../../app/state/RoomContext.jsx";
import { audit } from "../../crosscheck/domain/audit.js";
import { categories } from "../../review/domain/categories.js";
import { Field, Icon, PageHeading } from "../../../shared/ui/UI.jsx";
const tips = [
  "복도와 이웃집 소음",
  "온수와 수압 확인",
  "낮 시간 햇빛 방향",
  "창문·환풍기 작동",
  "주차 가능 대수와 비용",
  "공용부 청결과 관리",
  "방충망·틈새·흔적",
  "창틀·벽면 습기",
  "수도·인터넷·전기 포함 여부",
];
function VisitEditor({ room }) {
  const { visits, saveVisit } = useRooms();
  const [draft, setDraft] = useState(
    () => visits[room.id] || { checks: {}, note: "" },
  );
  return (
    <>
      <div className="notice">
        {audit(room).rate === 100
          ? "공식정보가 일치해도 방음·수압·관리 상태는 직접 확인하세요."
          : "확인 질문: 면적·층수의 공식 기록과 관리비 포함 항목을 담당자에게 확인하세요."}
      </div>
      <div className="check-grid">
        {[...categories, "관리비 포함 항목"].map((c, i) => (
          <label className="check-row" key={c}>
            <input
              type="checkbox"
              checked={!!draft.checks[i]}
              onChange={(e) =>
                setDraft((d) => ({
                  ...d,
                  checks: { ...d.checks, [i]: e.target.checked },
                }))
              }
            />
            <span>
              {c}
              <small>{tips[i]}</small>
            </span>
            <span className="check-state">
              {draft.checks[i] ? "확인 완료" : "미확인"}
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
export default function VisitsPage({ initialRoom }) {
  const { rooms } = useRooms();
  const [active, setActive] = useState(initialRoom || rooms[0]?.id);
  const room = rooms.find((r) => r.id === active) || rooms[0];
  return (
    <>
      <PageHeading
        kicker="VISIT NOTES"
        title="직접 보고, 꼼꼼히 기록."
        description="방문에서 확인한 내용을 나만의 체크리스트에 남겨보세요."
      />
      {room ? (
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
          <VisitEditor key={room.id} room={room} />
        </section>
      ) : (
        <div className="empty">먼저 매물을 등록해 주세요.</div>
      )}
    </>
  );
}

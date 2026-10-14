import { useState } from "react";
import { useRooms } from "../context/RoomContext.jsx";
import { categories } from "../lib/domain.js";
import { Field } from "./UI.jsx";
export default function ReviewForm({ onDone, onCancel }) {
  const { rooms, addReview } = useRooms();
  const buildings = [...new Map(rooms.map((r) => [r.building, r])).values()];
  const [building, setBuilding] = useState(buildings[0]?.building || ""),
    [period, setPeriod] = useState(""),
    [body, setBody] = useState(""),
    [scores, setScores] = useState({}),
    [error, setError] = useState("");
  async function submit(e) {
    e.preventDefault();
    if (!period.trim() || body.trim().length < 10) {
      setError("거주 시기와 10자 이상의 후기를 입력해 주세요.");
      return;
    }
    const saved = await addReview({
      building,
      period: period.trim(),
      body: body.trim(),
      scores: Object.fromEntries(
        categories.map((c) => [c, scores[c] ? Number(scores[c]) : null]),
      ),
    });
    if (saved) onDone();
  }
  return (
    <>
      <h2>살아본 경험을 나눠주세요</h2>
      <p className="helper">
        모든 점수는 높을수록 양호합니다. 벌레·결로는 적을수록 높은 점수예요.
      </p>
      <form onSubmit={submit}>
        <Field label="건물 *">
          <select
            value={building}
            onChange={(e) => setBuilding(e.target.value)}
            required
          >
            {buildings.map((r) => (
              <option key={r.building} value={r.building}>
                {r.address}
              </option>
            ))}
          </select>
        </Field>
        <Field label="거주 시기 *">
          <input
            value={period}
            onChange={(e) => setPeriod(e.target.value)}
            required
            maxLength="50"
            placeholder="예: 2025년 하반기"
          />
        </Field>
        <div className="form-grid">
          {categories.map((c) => (
            <Field key={c} label={c}>
              <select
                value={scores[c] || ""}
                onChange={(e) =>
                  setScores((s) => ({ ...s, [c]: e.target.value }))
                }
              >
                <option value="">모름 / 해당 없음</option>
                {[1, 2, 3, 4, 5].map((n) => (
                  <option key={n} value={n}>
                    {n}점
                    {n === 5 ? " · 매우 양호" : n === 1 ? " · 매우 불편" : ""}
                  </option>
                ))}
              </select>
            </Field>
          ))}
        </div>
        <Field label="후기 내용 *">
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            required
            minLength="10"
            maxLength="1500"
            rows="4"
            placeholder="10자 이상 작성해 주세요. 정확한 호수·연락처 등 개인정보는 쓰지 마세요."
          />
        </Field>
        <div className="notice">
          거주 미인증 후기입니다. 실제 계약서나 신분증은 수집하지 않습니다.
        </div>
        {error && <p role="alert">{error}</p>}
        <div className="modal-actions">
          <button className="secondary" type="button" onClick={onCancel}>
            취소
          </button>
          <button className="primary" type="submit">
            후기 저장
          </button>
        </div>
      </form>
    </>
  );
}

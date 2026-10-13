import { useRooms } from "../context/RoomContext.jsx";
import { audit, fixedCost, money } from "../lib/domain.js";
import { AuditBadge, RatingGrid, RoomScene } from "./UI.jsx";
export default function RoomDetail({ room: r, onEdit, onDelete, onVisit }) {
  const { reviews } = useRooms();
  const a = audit(r);
  return (
    <>
      <div className="detail-heading">
        <AuditBadge room={r} />
        <h2>{r.name}</h2>
        <p>{r.address}</p>
      </div>
      <RoomScene theme={r.theme} />
      <div className="detail-prices">
        <div>
          <small>보증금 / 월세</small>
          <strong>
            {money(r.deposit)} / {money(r.rent)}
          </strong>
        </div>
        <div>
          <small>월 고정비</small>
          <strong>{money(fixedCost(r))}</strong>
        </div>
      </div>
      <h3>
        광고 · 공식정보 대조 <span className="badge muted">가상 데이터</span>
      </h3>
      {a.count ? (
        <p className="helper">
          일치율 {a.rate}% · 비교 가능한 {a.count}개 항목 기준 ·{" "}
          {r.official.source} · 조회 {r.official.date}
        </p>
      ) : (
        <p className="helper">
          공식 API는 아직 연결되지 않았습니다. 공식 기록 없는 매물은 미실시로
          표시합니다.
        </p>
      )}
      {a.rows.map((x) => (
        <div className="audit-row" key={x.label}>
          <strong>{x.label}</strong>
          <span>
            광고 {x.ad}
            <br />
            공식 {x.official}
            <small>{x.reason}</small>
          </span>
          <span
            className={`badge ${!x.comparable ? "muted" : x.match ? "good" : "warn"}`}
          >
            {!x.comparable ? "확인 불가" : x.match ? "일치" : "차이 있음"}
          </span>
        </div>
      ))}
      <details>
        <summary>저장한 광고 원문</summary>
        <p className="original">{r.original}</p>
      </details>
      <h3>건물별 거주 평가</h3>
      <RatingGrid reviews={reviews} building={r.building} />
      <div className="modal-actions">
        <button className="text-button danger" onClick={onDelete}>
          매물 삭제
        </button>
        <button className="secondary" onClick={onEdit}>
          정보 수정
        </button>
        <button className="primary" onClick={onVisit}>
          방문 기록하기
        </button>
      </div>
    </>
  );
}

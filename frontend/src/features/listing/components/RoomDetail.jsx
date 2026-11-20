import { useRooms } from "../../../app/state/RoomContext.jsx";
import { audit, fixedCost, money } from "../../../shared/lib/domain.js";
import { AuditBadge, RatingGrid, RoomScene } from "../../../shared/ui/UI.jsx";
import OfficialLookup from "./OfficialLookup.jsx";
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
        등록 정보 · 공식정보 대조 <span className="badge muted">{r.official ? r.official.buildingId ? "건축물대장 조회 완료" : "가상 공식정보" : "공식 조회 미실시"}</span>
      </h3>
      {a.count ? (
        <p className="helper">
          일치율 {a.rate}% · 비교 가능한 {a.count}개 항목 기준 ·{" "}
          {r.official.source} · 조회 {new Date(r.official.date).toLocaleString("ko-KR")}
        </p>
      ) : (
        <p className="helper">
          아래에서 건축물대장을 조회하세요. 공식 자료가 없거나 기준이 다르면 비교 불가로 표시합니다.
        </p>
      )}
      {a.rows.map((x) => (
        <div className="audit-row" key={x.label}>
          <strong>{x.label}</strong>
          <span>
            등록 {x.ad}
            <br />
            공식 {x.official}
            <small>{x.reason}</small>
          </span>
          <span
            className={`badge ${!x.comparable ? "muted" : x.match ? "good" : "warn"}`}
          >
            {!x.comparable ? "비교 불가" : x.match ? "일치" : "차이 있음"}
          </span>
        </div>
      ))}
      {r.official?.buildingId && <div className="official-facts">
        <p><strong>선택한 공식 대상</strong> {r.official.buildingName} {r.official.dong} {r.official.ho}</p>
        <p>도로명주소: {r.official.address || "자료 없음"}<br />지번주소: {r.official.jibunAddress || "자료 없음"}</p>
        <p>용도: {r.official.use || "자료 없음"} · 사용승인일: {r.official.approvedOn || "자료 없음"}</p>
        <p>건물 전체: 지상 {r.official.aboveGroundFloors ?? "—"}층 / 지하 {r.official.undergroundFloors ?? "—"}층</p>
      </div>}
      <OfficialLookup room={r} />
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

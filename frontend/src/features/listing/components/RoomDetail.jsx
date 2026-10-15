import { useState } from "react";
import { useRooms } from "../../../app/state/RoomContext.jsx";
import { audit } from "../../crosscheck/domain/audit.js";
import { fixedCost } from "../domain/cost.js";
import { money } from "../../../shared/lib/money.js";
import { RatingGrid } from "../../review/components/RatingGrid.jsx";
import { RoomScene } from "./RoomScene.jsx";

/** 광고·공식 자료·개인 방문·공개 후기를 탭으로 나누어 정보의 출처를 구분한다. */
export default function RoomDetail({ room: r, onEdit, onDelete, onVisit, onReviews }) {
  const { reviews, visits } = useRooms();
  const [tab, setTab] = useState("ad");
  const a = audit(r);
  const visit = visits[r.id];
  const tabs = [["ad", "광고 정보"], ["official", "공식 대조"], ["visit", "방문 기록"], ["review", "건물 후기"]];
  return <>
    <div className="detail-heading"><span className="badge muted">{r.status === "DRAFT" ? "임시 저장" : "확정 매물"}</span><h2>{r.name}</h2><p>{r.address}</p></div>
    <div className="section-tabs" role="group" aria-label="매물 상세 분류">{tabs.map(([key, label]) => <button key={key} className={tab === key ? "selected" : ""} aria-pressed={tab === key} onClick={() => setTab(key)}>{label}</button>)}</div>
    {tab === "ad" && <div className="detail-tab-content">
      <RoomScene theme={r.theme} />
      <div className="detail-prices"><div><small>보증금 / 월세</small><strong>{money(r.deposit)} / {money(r.rent)}</strong></div><div><small>월 고정비</small><strong>{money(fixedCost(r))}</strong></div></div>
      <dl className="confirmation-grid">{[["고정 관리비", r.fee == null ? "정보 없음" : money(r.fee)], ["면적", r.area == null ? "정보 없음" : `${r.area}㎡ · ${r.areaType}`], ["층수", r.floor == null ? "정보 없음" : `${r.floor}층`], ["동·호수", [r.buildingDong, r.unitNumber].filter(Boolean).join(" / ") || "정보 없음"]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
      <h3>저장한 광고 원문</h3><p className="original">{r.original || "저장한 글이 없습니다."}</p>
      {r.sourceUrl && /^https?:\/\//i.test(r.sourceUrl) && <a className="text-button" href={r.sourceUrl} target="_blank" rel="noreferrer">광고 출처 열기 ↗</a>}
      {r.attachmentName && <p className="helper">첨부명: {r.attachmentName} · 원본 이미지는 저장되지 않은 미리보기 자료입니다.</p>}
    </div>}
    {tab === "official" && <div className="detail-tab-content">
      <h3>조회 상태 <span className="badge muted">{r.official ? "시연 자료 있음" : "미조회"}</span></h3>
      <p className="helper">{r.official ? `${r.official.source} · ${r.official.date}` : "공공 API는 연동 준비 중이며 공식 조회를 수행하지 않았습니다."}</p>
      <p className="helper">동일 대상·기준만 비교합니다. 시연 자료의 비교 가능한 항목은 {a.count}개입니다.</p>
      {a.rows.map(x => <div className="audit-row" key={x.label}><strong>{x.label === "면적" ? "전용면적" : "방 층수"}</strong><span>광고 {x.ad}<br />공식 {x.official}<small>{x.reason}</small></span><span className={`badge ${!x.comparable ? "muted" : x.match ? "good" : "warn"}`}>{!x.comparable ? "비교 불가" : x.match ? "일치" : "차이 있음"}</span></div>)}
      {(r.official ? ["건물 층수", "용도", "주차", "승용승강기", "사용승인일"] : ["전용면적", "방 층수", "건물 층수", "용도", "주차", "승용승강기", "사용승인일"]).map(label => <div className="audit-row" key={label}><strong>{label}</strong><span>{r.official ? "해당 항목의 조회·대조는 연동 준비 중" : "공식 조회 후 같은 대상과 기준을 확인합니다."}</span><span className="badge muted">대조 대기</span></div>)}
      <div className="notice">조회 상태와 대조 결과는 별개입니다. 일치 여부는 계약 안전성이나 적법성을 보증하지 않습니다.</div>
    </div>}
    {tab === "visit" && <div className="detail-tab-content"><h3>나만의 방문 기록</h3>{visit ? <><p>체크 완료 {Object.values(visit.checks).filter(Boolean).length} / 9개</p><p className="original">{visit.note || "저장한 메모가 없습니다."}</p></> : <p className="helper">아직 저장한 방문 기록이 없습니다. 현장에서 확인할 내용을 남겨보세요.</p>}<p className="helper">현재 저장된 기록을 표시합니다. 날짜별 이력과 사진은 다음 단계에서 확장합니다.</p></div>}
    {tab === "review" && <div className="detail-tab-content"><h3>건물별 거주 평가</h3><RatingGrid reviews={reviews} building={r.building} /><p className="notice">거주 사실을 확인하지 않은 경험담입니다. 해당 호실의 상태를 보증하지 않습니다.</p><button className="text-button" onClick={onReviews}>커뮤니티에서 후기 보기 →</button></div>}
    <div className="modal-actions"><button className="text-button danger" onClick={onDelete}>매물 삭제</button><button className="secondary" onClick={onEdit}>{r.status === "DRAFT" ? "등록 이어가기" : "정보 수정"}</button><button className="primary" onClick={onVisit} disabled={r.status === "DRAFT"}>방문 기록하기</button></div>
  </>;
}

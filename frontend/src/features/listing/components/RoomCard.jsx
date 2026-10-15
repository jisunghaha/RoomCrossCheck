import { useRooms } from "../../../app/state/RoomContext.jsx";
import { money, audit, statistics } from "../../../shared/lib/domain.js";
import { Icon, RoomScene, AuditBadge } from "../../../shared/ui/UI.jsx";
export default function RoomCard({ room: r, onDetail }) {
  const { reviews, selected, toggleSelected } = useRooms();
  const a = audit(r),
    s = statistics(reviews, r.building),
    picked = selected.includes(r.id);
  return (
    <article className={`room-card ${picked ? "picked" : ""}`}>
      <div className="card-image">
        <RoomScene theme={r.theme} />
        <label className="pick">
          <input
            type="checkbox"
            checked={picked}
            onChange={() => toggleSelected(r.id)}
            aria-label={`${r.name} 비교 선택`}
          />
          <span>비교 선택</span>
        </label>
        <AuditBadge room={r} />
        <span className="badge muted">{r.source === "server" ? "DB 저장 매물" : "가상·브라우저 매물"}</span>
      </div>
      <div className="card-body">
        <div className="location">{r.address}</div>
        <button className="card-title" onClick={() => onDetail(r.id)}>
          {r.name}
          <Icon name="arrow" />
        </button>
        <div className="price">
          {money(r.deposit)}
          <span>/</span>
          {money(r.rent)}
          <small>보증금 / 월세</small>
        </div>
        <div className="room-meta">
          {r.area ?? "—"}㎡ · {r.areaType} · {r.floor ?? "—"}층{" "}
          <span>관리비 {money(r.fee)}</span>
        </div>
        <div className="tags">
          {r.tags.map((t) => (
            <span key={t}>{t}</span>
          ))}
        </div>
        <div className="card-info">
          <div>
            <span>공식정보 일치율</span>
            <strong
              className={
                a.rate === 100
                  ? "green-text"
                  : a.rate != null
                    ? "amber-text"
                    : ""
              }
            >
              {a.rate == null ? "—" : `${a.rate}%`}
              <small>{a.count ? ` ${a.count}개 항목` : " 미실시"}</small>
            </strong>
          </div>
          <div>
            <span>거주 후기</span>
            <strong>
              {s.count ? (
                <>
                  <span className="star">★</span> {s.count}건
                </>
              ) : (
                "아직 없어요"
              )}
            </strong>
          </div>
        </div>
        <button className="card-bottom" onClick={() => onDetail(r.id)}>
          매물 자세히 보기 <Icon name="arrow" />
        </button>
      </div>
    </article>
  );
}

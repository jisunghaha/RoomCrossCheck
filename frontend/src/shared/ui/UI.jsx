import { audit, statistics } from "../lib/domain.js";
export function Icon({ name }) {
  const shapes = {
    home: (
      <path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z" />
    ),
    compare: (
      <>
        <rect x="3" y="4" width="7" height="16" rx="2" />
        <rect x="14" y="4" width="7" height="16" rx="2" />
      </>
    ),
    review: (
      <>
        <path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l-2 2V11.5A8.5 8.5 0 0 1 10.5 3H13a8 8 0 0 1 8 8.5Z" />
        <path d="M7 9h10M7 14h6" />
      </>
    ),
    check: <path d="m5 12 4 4L19 6" />,
    plus: <path d="M12 5v14M5 12h14" />,
    search: (
      <>
        <circle cx="10" cy="10" r="6" />
        <path d="m15 15 5 5" />
      </>
    ),
    arrow: <path d="M4 12h16m-6-6 6 6-6 6" />,
    shield: (
      <>
        <path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Z" />
        <path d="m8 12 3 3 5-6" />
      </>
    ),
    book: (
      <>
        <path d="M4 3h13l3 3v15H4Z" />
        <path d="M8 9h8M8 13h8M8 17h5" />
      </>
    ),
  };
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {shapes[name] || shapes.home}
    </svg>
  );
}
export function PageHeading({ kicker, title, description, children }) {
  return (
    <div className="page-heading">
      <div>
        <div className="eyebrow">{kicker}</div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {children}
    </div>
  );
}
export function RoomScene({ theme }) {
  return (
    <div
      className={`room-scene ${theme}`}
      role="img"
      aria-label="가상 원룸 공간 일러스트"
    >
      <div className="window">
        <i />
        <i />
      </div>
      <div className="poster" />
      <div className="lamp" />
      <div className="bed">
        <i />
        <b />
      </div>
      <div className="rug" />
      <div className="plant">
        <i />
        <b />
      </div>
      <span className="scene-label">CONCEPT ROOM · 가상 이미지</span>
    </div>
  );
}
export function AuditBadge({ room }) {
  const { count, rate } = audit(room);
  return (
    <span
      className={`badge ${!count ? "muted" : rate === 100 ? "good" : "warn"}`}
    >
      {rate === 100 && <Icon name="check" />}
      {!count ? "대조 미실시" : rate === 100 ? "정보 일치" : "차이 확인 필요"}
    </span>
  );
}
export function RatingGrid({ reviews, building }) {
  const stats = statistics(reviews, building);
  return (
    <div className="rating-grid">
      {stats.items.map((item) => (
        <div key={item.label}>
          <span>{item.label}</span>
          <div className="rating-track">
            <i style={{ width: `${(item.value || 0) * 20}%` }} />
          </div>
          <strong>{item.value?.toFixed(1) ?? "—"}</strong>
          <small>
            {item.count}건{item.count > 0 && item.count < 3 && " · 표본 부족"}
          </small>
        </div>
      ))}
    </div>
  );
}
export function Field({ label, children }) {
  return (
    <label className="field">
      {label}
      {children}
    </label>
  );
}

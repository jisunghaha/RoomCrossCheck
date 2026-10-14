import { statistics } from "../domain/statistics.js";
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

import { audit } from "../domain/audit.js";
import { Icon } from "../../../shared/ui/UI.jsx";
export function AuditBadge({ room }) {
  if (room.status === "DRAFT") return <span className="badge muted">임시 저장</span>;
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

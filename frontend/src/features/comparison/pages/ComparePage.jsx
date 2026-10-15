import { useRooms } from "../../../app/state/RoomContext.jsx";
import { audit } from "../../crosscheck/domain/audit.js";
import { categories } from "../../review/domain/categories.js";
import { fixedCost } from "../../listing/domain/cost.js";
import { money } from "../../../shared/lib/money.js";
import { statistics } from "../../review/domain/statistics.js";
import { validateComparison } from "../domain/selection.js";
import { AuditBadge } from "../../crosscheck/components/AuditBadge.jsx";
import { Icon, PageHeading } from "../../../shared/ui/UI.jsx";
import { RoomScene } from "../../listing/components/RoomScene.jsx";
export default function ComparePage({ navigate }) {
  const { rooms: allRooms, reviews, visits, selected, toggleSelected } = useRooms();
  const rooms = allRooms.filter(r => r.status !== "DRAFT");
  const chosen = rooms.filter((r) => selected.includes(r.id));
  const rows = [
    ["보증금", (r) => money(r.deposit)],
    ["월세", (r) => money(r.rent)],
    ["고정 관리비", (r) => money(r.fee)],
    ["월 고정비", (r) => <strong>{money(fixedCost(r))}</strong>],
    ["면적 / 종류", (r) => `${r.area ?? "—"}㎡ · ${r.areaType}`],
    ["층수", (r) => `${r.floor ?? "—"}층`],
    [
      "공식정보 일치율",
      (r) => {
        const a = audit(r);
        return a.rate == null
          ? "N/A · 비교 자료 없음"
          : `${a.rate}% · 비교 가능 ${a.count}개 항목`;
      },
    ],
    [
      "거주 후기",
      (r) => `${statistics(reviews, r.building).count}건 · 건물 기준`,
    ],
    ...categories.map((c) => [
      c,
      (r) => {
        const s = statistics(reviews, r.building).items.find(
          (i) => i.label === c,
        );
        return s.value == null ? (
          "N/A"
        ) : (
          <>
            {s.value.toFixed(1)} / 5{" "}
            <small>
              ({s.count}건{s.count < 3 && " · 표본 부족"})
            </small>
          </>
        );
      },
    ]),
    [
      "방문 확인",
      (r) =>
        `${Object.values(visits[r.id]?.checks || {}).filter(Boolean).length} / 9개 완료`,
    ],
  ];
  return (
    <>
      <PageHeading
        kicker="SIDE BY SIDE"
        title="고민되는 방을 한눈에."
        description="같은 기준으로 비교하고, 나에게 중요한 조건을 찾아보세요."
      >
        <button className="secondary" onClick={() => navigate("rooms")}>
          매물 선택하기
        </button>
      </PageHeading>
      <div className="compare-picker">
        {rooms.map((r) => (
          <label key={r.id}>
            <input
              type="checkbox"
              checked={selected.includes(r.id)}
              onChange={() => toggleSelected(r.id)}
            />
            {r.name}
          </label>
        ))}
      </div>
      {validateComparison(selected) ? (
        <>
          <div className="table-wrap">
            <table className="compare-table">
              <thead>
                <tr>
                  <th scope="col">비교 항목</th>
                  {chosen.map((r) => (
                    <th key={r.id} scope="col">
                      <RoomScene theme={r.theme} />
                      <h3>{r.name}</h3>
                      <AuditBadge room={r} />
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map(([label, get]) => (
                  <tr key={label}>
                    <th scope="row">{label}</th>
                    {chosen.map((r) => (
                      <td key={r.id}>{get(r)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="helper">
            월 고정비는 월세 + 고정 관리비입니다. 변동 공과금은 포함하지 않으며,
            관리비가 미상이면 합계를 확정하지 않습니다. 후기 평점은 높을수록
            양호합니다.
          </p>
        </>
      ) : (
        <div className="empty large">
          <Icon name="compare" />
          <h2>비교할 방을 2~3개 골라주세요</h2>
          <p>비용, 공식정보, 8개 항목의 거주 경험을 나란히 볼 수 있어요.</p>
        </div>
      )}
    </>
  );
}

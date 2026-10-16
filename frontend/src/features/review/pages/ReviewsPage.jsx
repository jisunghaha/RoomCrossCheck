import { useRooms } from "../../../app/state/RoomContext.jsx";
import { statistics } from "../domain/statistics.js";
import { Icon, PageHeading } from "../../../shared/ui/UI.jsx";
import { RatingGrid } from "../components/RatingGrid.jsx";
export default function ReviewsPage({ onAdd }) {
  const { rooms, reviews, deleteReview } = useRooms();
  const buildings = [...new Map(rooms.map((r) => [r.building, r])).values()];
  return (
    <>
      <PageHeading
        kicker="LIVED-IN INSIGHTS"
        title="살아본 사람의 이야기."
        description="서류에 없는 생활의 질, 8가지 항목으로 확인하세요."
      >
        <button className="primary" onClick={onAdd} disabled={!rooms.some(r => r.status !== "DRAFT")}>
          <Icon name="plus" />
          후기 작성
        </button>
      </PageHeading>
      <div className="notice">
        모든 후기는 건물 기준의 경험입니다. 시연 후기는 거주 미인증이며, 실제
        거주 사실을 보증하지 않습니다.
      </div>
      <div className="review-grid">
        {buildings.map((r) => (
          <section className="panel" key={r.building}>
            <div className="section-title">
              <div>
                <div className="eyebrow">RESIDENT REVIEW</div>
                <h2>{r.address}</h2>
              </div>
              <span className="badge muted">
                {statistics(reviews, r.building).count}건
              </span>
            </div>
            <RatingGrid reviews={reviews} building={r.building} />
            {reviews
              .filter((v) => v.building === r.building)
              .map((v) => (
                <article className="review" key={v.id}>
                  <div>
                    <span className="avatar small">{v.demo ? "예" : "나"}</span>
                    <strong>
                      {v.demo ? "가상 거주자" : "내가 작성한 후기"}
                    </strong>
                    <span className="badge muted">거주 미인증</span>
                    {!v.demo && (
                      <button
                        className="text-button danger"
                        onClick={() => deleteReview(v.id)}
                      >
                        삭제
                      </button>
                    )}
                  </div>
                  <small>
                    {v.period} · {v.createdAt}
                  </small>
                  <p>{v.body}</p>
                </article>
              ))}
            {!reviews.some((v) => v.building === r.building) && (
              <p className="helper">아직 등록된 후기가 없어요.</p>
            )}
          </section>
        ))}
      </div>
      {!buildings.length && (
        <div className="empty">먼저 매물을 등록해 주세요.</div>
      )}
    </>
  );
}

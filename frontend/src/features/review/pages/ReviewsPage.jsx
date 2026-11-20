import { useRooms } from "../../../app/state/RoomContext.jsx";
import { buildingStatistics } from "../../../shared/lib/domain.js";
import { Icon, PageHeading, RatingGrid } from "../../../shared/ui/UI.jsx";
import ReportButton from "../../report/ReportButton.jsx";
export default function ReviewsPage({ onAdd }) {
  const { rooms, reviews, reviewStats, deleteReview, setDemoReviews, dataMode, saving, loading } = useRooms();
  const buildings = [...new Map(rooms.map((r) => [r.building, r])).values()];
  // 서버 후기는 mine으로 본인 여부를 받고, 브라우저 저장 후기는 시연 후기가 아니면 본인 후기다.
  const mine = (review) => review.mine ?? !review.demo;
  return (
    <>
      <PageHeading
        kicker="LIVED-IN INSIGHTS"
        title="살아본 사람의 이야기."
        description="서류에 없는 생활의 질, 8가지 항목으로 확인하세요."
      >
        <button className="primary" onClick={onAdd} disabled={!rooms.length}>
          <Icon name="plus" />
          후기 작성
        </button>
      </PageHeading>
      <div className="notice">
        모든 후기는 건물 기준의 경험이며 같은 건물을 저장한 다른 회원에게도 공개됩니다.
        거주 사실은 인증하지 않으며, 시연 후기는 이 브라우저에만 표시되는 가상 후기입니다.
      </div>
      {dataMode !== "api" && (
        <div className="modal-actions">
          <button className="secondary" disabled={!rooms.length || saving || loading} onClick={() => setDemoReviews(true)}>
            시연 후기 생성 (건물당 5건)
          </button>
          {reviews.some(review => review.id.startsWith("review-demo:")) && (
            <button className="text-button danger" disabled={saving || loading} onClick={() => setDemoReviews(false)}>
              생성한 시연 후기 삭제
            </button>
          )}
        </div>
      )}
      <div className="review-grid">
        {buildings.map((r) => (
          <section className="panel" key={r.building}>
            <div className="section-title">
              <div>
                <div className="eyebrow">RESIDENT REVIEW</div>
                <h2>{r.address}</h2>
              </div>
              <span className="badge muted">
                {buildingStatistics(reviews, r.building, reviewStats).count}건
              </span>
            </div>
            <RatingGrid reviews={reviews} building={r.building} stats={buildingStatistics(reviews, r.building, reviewStats)} />
            {reviews
              .filter((v) => v.building === r.building)
              .map((v) => (
                <article className="review" key={v.id}>
                  <div>
                    <span className="avatar small">{v.demo ? "예" : mine(v) ? "나" : "이"}</span>
                    <strong>
                      {v.demo ? "가상 거주자" : mine(v) ? "내가 작성한 후기" : "이웃 회원 후기"}
                    </strong>
                    <span className="badge muted">거주 미인증</span>
                    {!v.demo && mine(v) && (
                      <button
                        className="text-button danger"
                        onClick={() => deleteReview(v.id)}
                      >
                        삭제
                      </button>
                    )}
                    {dataMode === "listing-api" && !v.demo && !mine(v) && (
                      <ReportButton targetType="REVIEW" targetId={v.id} />
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

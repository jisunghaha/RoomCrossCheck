import { useCallback, useEffect, useRef, useState } from "react";
import { useRooms } from "./state/RoomContext.jsx";
import { useHashRoute } from "./routing/useHashRoute.js";
import Layout from "./layout/Layout.jsx";
import Modal from "../shared/ui/Modal.jsx";
import RoomDetail from "../features/listing/components/RoomDetail.jsx";
import RoomForm from "../features/listing/components/RoomForm.jsx";
import ReviewForm from "../features/review/components/ReviewForm.jsx";
import RoomsPage from "../features/listing/pages/RoomsPage.jsx";
import ComparePage from "../features/comparison/pages/ComparePage.jsx";
import ReviewsPage from "../features/review/pages/ReviewsPage.jsx";
import VisitsPage from "../features/visit/pages/VisitsPage.jsx";
import RegisterRoomPage from "../features/listing/pages/RegisterRoomPage.jsx";
import CommunityPage from "../features/community/CommunityPage.jsx";
import ReportsPage from "../features/report/ReportsPage.jsx";
const independentPages = ["community", "reports"];
export default function App() {
  const [page, navigate] = useHashRoute();
  const [authRevision, setAuthRevision] = useState(0);
  const [member, setMember] = useState(null);
  const { rooms, deleteRoom, toast, loading, loadError, authRequired, saving, reload, dataMode } = useRooms();
  const memberId = useRef(undefined);
  const onSessionChange = useCallback((value) => {
    setMember(value ?? null);
    setAuthRevision(revision => revision + 1);
  }, []);
  // 창 포커스마다 오는 세션 확인은 무시하고, 로그인·로그아웃으로 회원이 바뀐 경우에만 내 자료를 다시 불러온다.
  useEffect(() => {
    const id = member?.id ?? null;
    if (memberId.current === undefined) { memberId.current = id; return; }
    if (memberId.current === id) return;
    memberId.current = id;
    if (dataMode !== "local") reload();
  }, [member, dataMode]);
  const [modal, setModal] = useState(null),
    [visitRoom, setVisitRoom] = useState(null);
  const close = () => setModal(null),
    room = rooms.find((r) => r.id === modal?.id);
  const blocked = !independentPages.includes(page) && (loading || loadError || authRequired);
  return (
    <>
      <fieldset disabled={!independentPages.includes(page) && saving} className="data-operation-boundary" aria-busy={!independentPages.includes(page) && saving}>
      <Layout page={page} member={member} onGuide={() => setModal({ type: "guide" })} onSessionChange={onSessionChange}>
        {blocked && (
          <div className={authRequired ? "empty large" : undefined}>
            {authRequired ? (
              <>
                <h2>로그인하면 내 매물을 볼 수 있어요</h2>
                <p>매물·방문 기록·비교 선택은 회원별로 저장됩니다. 왼쪽 계정 카드에서 로그인하거나 회원가입해 주세요.</p>
              </>
            ) : (
              <>
                <p role={loadError ? "alert" : "status"}>{loadError || "매물을 불러오는 중이에요…"}</p>
                {loadError && <button onClick={reload}>다시 불러오기</button>}
              </>
            )}
          </div>
        )}
        {page === "reports" && <ReportsPage member={member} />}
        {!blocked && page === "register" && <RegisterRoomPage navigate={navigate} />}
        {page === "community" && <CommunityPage authRevision={authRevision} />}
        {!blocked && page === "rooms" && (
          <RoomsPage
            onAdd={() => navigate("register")}
            onDetail={(id) => setModal({ type: "detail", id })}
            navigate={navigate}
          />
        )}
        {!blocked && page === "compare" && <ComparePage navigate={navigate} />}
        {!blocked && page === "reviews" && (
          <ReviewsPage onAdd={() => setModal({ type: "review" })} />
        )}
        {!blocked && page === "visits" && (
          <VisitsPage key={visitRoom || "default"} initialRoom={visitRoom} />
        )}
      </Layout>
      {modal && (
        <Modal
          key={`${modal.type}-${modal.id || modal.advertisementId || ""}`}
          title={
            {
              guide: "이용 가이드",
              form: "매물 입력",
              detail: "매물 상세",
              review: "후기 작성",
              delete: "매물 삭제 확인",
            }[modal.type]
          }
          onClose={close}
        >
          {modal.type === "form" && (
            <RoomForm
              room={room}
              initialValues={modal.initialValues}
              onCancel={close}
              onDone={() => {
                close();
                navigate("rooms");
              }}
            />
          )}
          {modal.type === "review" && (
            <ReviewForm
              onCancel={close}
              onDone={() => {
                close();
                navigate("reviews");
              }}
            />
          )}
          {modal.type === "detail" && room && (
            <RoomDetail
              room={room}
              onEdit={() => setModal({ type: "form", id: room.id })}
              onDelete={() => setModal({ type: "delete", id: room.id })}
              onVisit={() => {
                setVisitRoom(room.id);
                close();
                navigate("visits");
              }}
            />
          )}
          {modal.type === "delete" && (
            <>
              <h2>이 매물을 삭제할까요?</h2>
              <p>
                연결된 개인 방문 기록도 삭제됩니다. 공개 거주 후기는 유지됩니다.
              </p>
              <div className="modal-actions">
                <button className="secondary" onClick={close}>
                  취소
                </button>
                <button
                  className="primary"
                  onClick={async () => {
                    if (await deleteRoom(modal.id)) close();
                  }}
                >
                  삭제하기
                </button>
              </div>
            </>
          )}
          {modal.type === "guide" && (
            <>
              <h2>내 첫 방, 세 단계로 확인해요.</h2>
              <ol className="guide">
                <li>
                  <strong>관심 매물 저장</strong>
                  <p>광고의 금액, 면적, 층수를 입력하세요.</p>
                </li>
                <li>
                  <strong>같은 기준으로 비교</strong>
                  <p>
                    2~3개 방을 골라 비용과 공식정보 차이, 거주 평가를
                    확인하세요.
                  </p>
                </li>
                <li>
                  <strong>현장에서 직접 확인</strong>
                  <p>체크리스트에 방음·수압 등 실제 상태를 기록하세요.</p>
                </li>
              </ol>
              <div className="notice">
                {/* 기존 로그인 미연결 문구는 실제 인증 UI와 모순되므로 제거하고 저장 방식별 안내를 갱신했다. */}
                {dataMode === "local"
                  ? "현재 매물 데이터는 이 브라우저에 저장됩니다. 회원가입·로그인은 연결된 서버를 이용합니다."
                  : dataMode === "listing-api"
                    ? "매물·광고·방문 기록·체크리스트·비교 선택은 로그인한 회원별로 서버에 저장되며 본인만 볼 수 있습니다. 거주 후기는 건물 기준으로 공개되고, 시연 후기만 이 브라우저에 남습니다."
                    : "데이터는 연결된 서버를 통해 저장됩니다. 회원가입·로그인을 이용할 수 있으며 OCR·공공 API 제공 여부는 서버 구현에 따라 달라집니다."}
              </div>
            </>
          )}
        </Modal>
      )}
      </fieldset>
      <div
        id="toast"
        role="status"
        aria-live="polite"
        className={toast ? "show" : ""}
      >
        {toast}
      </div>
    </>
  );
}

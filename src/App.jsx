import { useState } from "react";
import { useRooms } from "./context/RoomContext.jsx";
import { useHashRoute } from "./hooks/useHashRoute.js";
import Layout from "./components/Layout.jsx";
import Modal from "./components/Modal.jsx";
import RoomDetail from "./components/RoomDetail.jsx";
import RoomForm from "./components/RoomForm.jsx";
import ReviewForm from "./components/ReviewForm.jsx";
import RoomsPage from "./pages/RoomsPage.jsx";
import ComparePage from "./pages/ComparePage.jsx";
import ReviewsPage from "./pages/ReviewsPage.jsx";
import VisitsPage from "./pages/VisitsPage.jsx";
export default function App() {
  const [page, navigate] = useHashRoute();
  const { rooms, deleteRoom, toast, loading, loadError, saving, reload, dataMode } = useRooms();
  const [modal, setModal] = useState(null),
    [visitRoom, setVisitRoom] = useState(null);
  const close = () => setModal(null),
    room = rooms.find((r) => r.id === modal?.id);
  if (loading || loadError) return (
    <main><p role={loadError ? "alert" : "status"}>{loadError || "매물을 불러오는 중이에요…"}</p>
      {loadError && <button onClick={reload}>다시 불러오기</button>}
    </main>
  );
  return (
    <>
      <fieldset disabled={saving} className="data-operation-boundary" aria-busy={saving}>
      <Layout page={page} onGuide={() => setModal({ type: "guide" })}>
        {page === "rooms" && (
          <RoomsPage
            onAdd={() => setModal({ type: "form" })}
            onRegisterAdvertisement={(initialValues, advertisementId) => setModal({ type: "form", initialValues, advertisementId })}
            onDetail={(id) => setModal({ type: "detail", id })}
            navigate={navigate}
          />
        )}
        {page === "compare" && <ComparePage navigate={navigate} />}
        {page === "reviews" && (
          <ReviewsPage onAdd={() => setModal({ type: "review" })} />
        )}
        {page === "visits" && (
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
                {dataMode === "local"
                  ? "현재 데이터는 이 브라우저에 저장됩니다. 로그인·OCR·공공 API는 아직 연결되지 않았습니다."
                  : dataMode === "listing-api"
                    ? "매물은 MySQL에 저장됩니다. 광고 추출은 사용자 확인 후 매물 입력에 활용합니다. 후기·방문 기록은 이 브라우저에만 저장되고 로그인·공공 API는 아직 연결되지 않았습니다."
                    : "데이터는 연결된 서버를 통해 저장됩니다. 로그인·OCR·공공 API 제공 여부는 서버 구현에 따라 달라집니다."}
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

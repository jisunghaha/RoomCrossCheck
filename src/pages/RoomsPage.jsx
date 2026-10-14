import { useMemo, useState } from "react";
import { useRooms } from "../context/RoomContext.jsx";
import { audit } from "../lib/domain.js";
import { Icon, PageHeading } from "../components/UI.jsx";
import RoomCard from "../components/RoomCard.jsx";
import AdDraftPanel from "../components/AdDraftPanel.jsx";
export default function RoomsPage({ onAdd, onDetail, navigate }) {
  const { rooms, selected, clearSelected, dataMode } = useRooms();
  const [query, setQuery] = useState(""),
    [filter, setFilter] = useState("all"),
    [sort, setSort] = useState("recent");
  const visible = useMemo(
    () =>
      rooms
        .filter(
          (r) =>
            (r.name + " " + r.address).includes(query) &&
            (filter === "all" ||
              (filter === "match" && audit(r).rate === 100) ||
              (filter === "check" && audit(r).rate !== 100)),
        )
        .sort(
          sort === "rent"
            ? (a, b) => (a.rent ?? Infinity) - (b.rent ?? Infinity)
            : (a, b) => b.createdAt.localeCompare(a.createdAt),
        ),
    [rooms, query, filter, sort],
  );
  const matched = rooms.filter((r) => audit(r).rate === 100).length;
  return (
    <>
      <PageHeading
        kicker="A PLACE TO CALL HOME"
        title="나에게 맞는 방, 차근차근."
        description="저장한 매물의 조건과 실제 거주 경험을 함께 살펴보세요."
      >
        <button className="primary" onClick={onAdd}>
          <Icon name="plus" />새 매물 등록
        </button>
      </PageHeading>
      {dataMode === "listing-api" && <AdDraftPanel />}
      <section className="overview">
        {[
          ["home", "", "저장한 매물", rooms.length],
          ["shield", "green", "공식정보 일치", matched],
          ["book", "amber", "추가 확인 필요", rooms.length - matched],
        ].map(([icon, color, title, count]) => (
          <div key={title}>
            <span className={`metric-icon ${color}`}>
              <Icon name={icon} />
            </span>
            <div>
              <small>{title}</small>
              <strong>
                {count}
                <em>개</em>
              </strong>
            </div>
          </div>
        ))}
        <div className="overview-note">
          <span>조금 더 꼼꼼하게,</span>
          <strong>내 첫 방을 고르는 시간.</strong>
          <span className="mini-house">⌂</span>
        </div>
      </section>
      <section className="list-section">
        <div className="section-title">
          <h2>
            관심 매물 <span>{rooms.length}</span>
          </h2>
          <p>마음에 드는 방을 골라 비교해 보세요</p>
        </div>
        <div className="toolbar">
          <div className="tabs">
            {[
              ["all", "전체 매물"],
              ["match", "정보 일치"],
              ["check", "확인 필요"],
            ].map(([value, label]) => (
              <button
                key={value}
                className={filter === value ? "selected" : ""}
                onClick={() => setFilter(value)}
              >
                {label}
              </button>
            ))}
          </div>
          <div className="search-sort">
            <label className="search">
              <Icon name="search" />
              <input
                placeholder="매물명, 건물 검색"
                aria-label="매물 검색"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </label>
            <select
              aria-label="정렬"
              value={sort}
              onChange={(e) => setSort(e.target.value)}
            >
              <option value="recent">최근 등록순</option>
              <option value="rent">월세 낮은순</option>
            </select>
          </div>
        </div>
        <div className="room-grid">
          {visible.length ? (
            visible.map((r) => (
              <RoomCard key={r.id} room={r} onDetail={onDetail} />
            ))
          ) : (
            <div className="empty">
              <h3>찾는 매물이 없어요</h3>
              <p>검색 조건을 바꾸거나 새 매물을 등록해 보세요.</p>
            </div>
          )}
        </div>
      </section>
      <div className="bottom-note">
        <Icon name="shield" />
        <div>
          <strong>정보가 일치해도, 현장 확인은 꼭 필요해요.</strong>
          <p>
            일치율은 비교 가능한 공식 항목의 일치 정도입니다. 계약 안전성을
            보증하지 않아요.
          </p>
        </div>
        <button className="text-button" onClick={() => navigate("visits")}>
          방문 준비하기 <Icon name="arrow" />
        </button>
      </div>
      {selected.length > 0 && (
        <div className="dock">
          <div>
            <strong>
              {selected.length}
              <span> / 3</span>
            </strong>
            개 선택됨{" "}
            <span className="dock-hint">최대 3개의 방을 한눈에 비교하세요</span>
          </div>
          <button className="text-button" onClick={clearSelected}>
            선택 해제
          </button>
          <button
            className="primary"
            onClick={() => navigate("compare")}
            disabled={selected.length < 2}
          >
            비교하기 <Icon name="arrow" />
          </button>
        </div>
      )}
    </>
  );
}

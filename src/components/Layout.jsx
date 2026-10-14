import { useRooms } from "../context/RoomContext.jsx";
import { Icon } from "./UI.jsx";
export const navigation = [
  ["rooms", "home", "관심 매물"],
  ["compare", "compare", "매물 비교"],
  ["reviews", "review", "거주 후기"],
  ["visits", "book", "방문 체크리스트"],
];
export default function Layout({ page, onGuide, children }) {
  const { rooms, dataMode } = useRooms();
  return (
    <>
      <aside className="sidebar">
        <a className="brand" href="#rooms">
          <Icon name="home" />
          <span>
            원룸체크<span className="brand-dot">.</span>
          </span>
        </a>
        <div className="workspace">
          <span className="workspace-icon">나</span>
          <div>
            <strong>나의 자취 준비</strong>
            <small>첫 자취, 꼼꼼하게</small>
          </div>
          <span>⌄</span>
        </div>
        <div className="nav-label">MY WORKSPACE</div>
        <nav>
          {navigation.map(([p, icon, title]) => (
            <a
              key={p}
              href={`#${p}`}
              className={`nav-item ${page === p ? "active" : ""}`}
              aria-current={page === p ? "page" : undefined}
            >
              <Icon name={icon} />
              {title}
              {p === "rooms" && (
                <span className="nav-count">{rooms.length}</span>
              )}
            </a>
          ))}
        </nav>
        <div className="sidebar-tip">
          <div className="tip-icon">
            <Icon name="shield" />
          </div>
          <strong>좋은 방을 찾는 나만의 기준</strong>
          <p>
            광고 속 정보부터 살아본 경험까지,
            <br />
            한곳에서 꼼꼼히 확인하세요.
          </p>
          <button className="text-button" onClick={onGuide}>
            이용 가이드 <Icon name="arrow" />
          </button>
        </div>
        <div className="profile">
          <div className="avatar">나</div>
          <div>
            <strong>데모 사용자</strong>
            <small>{dataMode === "api" ? "서버 저장 모드" : "브라우저에만 저장됩니다"}</small>
          </div>
          <span className="demo-dot" />
        </div>
      </aside>
      <main>
        <header className="topbar">
          <div>
            나의 자취 준비 <span>/</span>
            <strong>{navigation.find((n) => n[0] === page)?.[2]}</strong>
          </div>
          <span className="demo-badge">{dataMode === "api" ? "SERVER" : "REACT DEMO"}</span>
        </header>
        <div className="content">{children}</div>
        <footer>
          원룸체크 <span>더 나은 자취의 시작</span>
          <span>{dataMode === "local" ? "모든 초기 매물·공식 기록·후기는 가상 예시입니다." : "연결된 서버의 데이터를 표시합니다."}</span>
        </footer>
      </main>
    </>
  );
}

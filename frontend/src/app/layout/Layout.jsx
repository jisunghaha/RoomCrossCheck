import { useRooms } from "../state/RoomContext.jsx";
import { Icon } from "../../shared/ui/UI.jsx";
import AuthPanel from "../../features/auth/AuthPanel.jsx";
export const navigation = [
  ["rooms", "home", "관심 매물"],
  ["compare", "compare", "매물 비교"],
  ["reviews", "review", "거주 후기"],
  ["visits", "book", "방문 체크리스트"],
  ["community", "review", "커뮤니티"],
];
export default function Layout({ page, onGuide, onSessionChange, children }) {
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
        {/* 고정된 준비 카드는 제거하고 실제 인증 상태와 계정 버튼을 같은 위치로 통합한다. */}
        <AuthPanel onSessionChange={onSessionChange} />
        <div className="nav-label">MY WORKSPACE</div>
        <nav className="workspace-navigation" aria-label="주요 메뉴">
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
        {/* 중복된 하단 가상 프로필은 위 계정 카드에서 실제 회원 정보를 표시하므로 제거했다. */}
      </aside>
      <main>
        <header className="topbar">
          <div>
            나의 자취 준비 <span>/</span>
            <strong>{page === "register" ? "매물 등록" : navigation.find((n) => n[0] === page)?.[2]}</strong>
          </div>
          {/* COMMUNITY·SERVER 배지는 제거했다. 계정 접근은 왼쪽 카드로 모으고 저장 방식은 기존 하단 안내를 유지한다. */}
        </header>
        <div className="content">{children}</div>
        <footer>
          원룸체크 <span>더 나은 자취의 시작</span>
          <span>{page === "community" ? "게시글과 댓글은 연결된 서버를 통해 저장됩니다." : dataMode === "local" ? "모든 초기 매물·공식 기록·후기는 가상 예시입니다." : "연결된 서버의 데이터를 표시합니다."}</span>
        </footer>
      </main>
    </>
  );
}

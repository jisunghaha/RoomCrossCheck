import { Icon, PageHeading } from "../../../shared/ui/UI.jsx";
import { RoomScene } from "../../listing/components/RoomScene.jsx";

/** 첫 방문자에게 서비스 흐름과 공개 콘텐츠·개인 매물 화면의 진입점을 제공한다. */
export default function HomePage({ navigate }) {
  return (
    <>
      <section className="welcome-hero panel">
        <div>
          <div className="eyebrow">YOUR FIRST ROOM, WITH CONFIDENCE</div>
          <h1>내 첫 방을 고르는 일,<br />조금 더 꼼꼼하게.</h1>
          <p>광고와 공식 기록, 현장에서 남긴 메모와 거주 경험까지.<br />흩어진 정보를 모아 나에게 맞는 방을 비교하세요.</p>
          <div className="button-row">
            <button className="primary" onClick={() => navigate("register")}>매물 등록 살펴보기 <Icon name="arrow" /></button>
            <button className="secondary" onClick={() => navigate("community")}>커뮤니티 둘러보기</button>
          </div>
          <p className="helper">현재는 시연 화면입니다. 회원 인증·OCR·공식 조회는 연동 준비 중입니다.</p>
        </div>
        <div className="welcome-art" aria-hidden="true"><RoomScene theme="sage" /></div>
      </section>
      <PageHeading kicker="HOW IT WORKS" title="한 번의 등록, 차근차근 확인." description="AI는 추출을 돕고, 정보의 확인과 최종 선택은 내가 합니다." />
      <div className="flow-grid">
        {[
          ["01", "광고를 모으고", "광고 이미지와 글을 가져와 원본을 보관합니다.", "plus"],
          ["02", "같은 기준으로 확인", "추출값을 직접 확인하고 공식 기록의 출처와 차이를 살핍니다.", "shield"],
          ["03", "직접 보고 기록", "방문 체크리스트와 메모로 현장에서 확인할 질문을 정리합니다.", "book"],
          ["04", "2~3개를 나란히", "비용·조건·방문 결과와 건물 후기를 함께 비교합니다.", "compare"],
        ].map(([number, title, body, icon]) => <article className="panel flow-card" key={number}>
          <span className="eyebrow">STEP {number}</span><Icon name={icon} /><h2>{title}</h2><p>{body}</p>
        </article>)}
      </div>
      <section className="panel welcome-bottom">
        <div><h2>방을 보기 전, 다른 사람의 경험도 함께.</h2><p>공지사항·자취 정보·건물별 후기는 공개 콘텐츠로 구성합니다.</p></div>
        <button className="text-button" onClick={() => navigate("community/reviews")}>거주 후기 보기 <Icon name="arrow" /></button>
      </section>
    </>
  );
}

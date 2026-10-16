import { useState } from "react";
import { communityPosts } from "../../../app/data/communitySeed.js";
import { Icon, PageHeading } from "../../../shared/ui/UI.jsx";
import ReviewsPage from "../../review/pages/ReviewsPage.jsx";

export const communityTabs = [["notice", "공지사항"], ["tips", "자취 정보"], ["reviews", "거주 후기"]];

/** 공지·자취 정보의 예시 목록과 기존 건물 후기를 하나의 공개 진입점에 배치한다. */
export default function CommunityPage({ tab = "notice", navigate, onAddReview }) {
  const [query, setQuery] = useState("");
  const visible = communityPosts.filter(p => p.category === tab && `${p.title} ${p.body.join(" ")}`.includes(query));
  return <>
    <PageHeading kicker="LIVING TOGETHER" title="혼자 살지만, 정보는 함께." description="공지와 자취 정보, 건물별 거주 경험을 한곳에서 살펴보세요." />
    <nav className="section-tabs" aria-label="커뮤니티 분류">{communityTabs.map(([key, label]) => <a key={key} className={tab === key ? "selected" : ""} aria-current={tab === key ? "page" : undefined} href={`#community/${key}`}>{label}</a>)}</nav>
    {tab === "reviews" ? <ReviewsPage onAdd={onAddReview} /> : <section className="panel community-panel">
      <div className="section-title"><h2>{tab === "notice" ? "공지사항" : "자취 정보"}</h2><span className="badge muted">시연 글</span></div>
      <label className="search community-search"><Icon name="search" /><input aria-label="게시글 검색" value={query} onChange={e => setQuery(e.target.value)} placeholder="제목과 내용 검색" /></label>
      <div className="post-list">{visible.map(post => <a className="post-row" href={`#post/${post.id}`} key={post.id}><div><span className="eyebrow">{tab === "notice" ? "NOTICE" : "LIVING TIPS"}</span><h3>{post.title}</h3><p>{post.body[0]}</p></div><small>{post.date}</small><Icon name="arrow" /></a>)}</div>
      {!visible.length && <p className="empty">검색 결과가 없습니다.</p>}
      <p className="helper">공개 글을 살펴보는 시연 화면입니다. 글 작성과 댓글·반응·신고는 다음 단계에서 추가합니다.</p>
    </section>}
  </>;
}

/** 주소의 글 ID가 없거나 삭제된 경우에도 목록으로 돌아갈 수 있게 한다. */
export function CommunityPostPage({ id, navigate }) {
  const post = communityPosts.find(p => p.id === id);
  if (!post) return <section className="panel"><h1>글을 찾을 수 없습니다.</h1><button className="secondary" onClick={() => navigate("community")}>목록으로 돌아가기</button></section>;
  return <article className="panel post-detail">
    <button className="text-button" onClick={() => navigate(`community/${post.category}`)}>← {post.category === "notice" ? "공지사항" : "자취 정보"} 목록</button>
    <span className="badge muted">시연 글</span><h1>{post.title}</h1><p className="helper">{post.author} · {post.date}</p>
    <div className="post-body">{post.body.map((paragraph, i) => <p key={i}>{paragraph}</p>)}</div>
    <div className="notice">댓글·좋아요·싫어요·신고는 다음 단계에서 연결합니다.</div>
  </article>;
}

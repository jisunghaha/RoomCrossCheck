import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Icon, PageHeading } from "../../shared/ui/UI.jsx";
import Modal from "../../shared/ui/Modal.jsx";
import { createCommunityApi } from "./api.js";
import DemoLoginPanel from "./DemoLoginPanel.jsx";
import "./community.css";

const guest = { authenticated: false, userId: null, administrator: false };
const postLink = id => `#community/post/${encodeURIComponent(id)}`;
const author = id => `회원 #${id}`;
const date = value => value ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }) : "";
const owns = (item, session) => session.authenticated && session.userId === item.authorId;
const managesPost = (post, session) => session.authenticated && (session.administrator || (!post.notice && owns(post, session)));

function route() {
  const parts = window.location.hash.slice(1).split("/");
  if (parts.length === 1) return { kind: "list" };
  if (parts.length === 2 && parts[1] === "new") return { kind: "new" };
  if (parts[1] === "post" && parts[2] && (parts.length === 3 || (parts.length === 4 && parts[3] === "edit"))) {
    try { return { kind: parts[3] === "edit" ? "edit" : "detail", id: decodeURIComponent(parts[2]) }; } catch { /* 잘못 인코딩된 주소는 아래에서 처리한다. */ }
  }
  return { kind: "missing" };
}

function useResource(load) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState({ data: null, loading: true, error: "" });
  useEffect(() => {
    const controller = new AbortController();
    setState({ data: null, loading: true, error: "" });
    load(controller.signal).then(data => {
      if (!controller.signal.aborted) setState({ data, loading: false, error: "" });
    }).catch(error => {
      if (!controller.signal.aborted) setState({ data: null, loading: false, error: error.message });
    });
    // 페이지 이동 후 늦게 도착한 응답이 새 화면을 덮어쓰지 않도록 한다.
    return () => controller.abort();
  }, [load, attempt]);
  return { ...state,
    retry: () => { setState({ data: null, loading: true, error: "" }); setAttempt(value => value + 1); },
    replace: data => setState({ data, loading: false, error: "" }),
  };
}

function useMutation(onPermissionError) {
  const lock = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function run(action, done) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    try { const result = await action(); done?.(result); } catch (failure) {
      setError(failure.message);
      if (failure.status === 401 || failure.status === 403) onPermissionError();
    } finally { lock.current = false; setBusy(false); }
  }
  return { busy, error, run, clearError: () => setError("") };
}

function ResourceState({ resource, label }) {
  if (resource.loading) return <p className="community-state" role="status">{label} 불러오는 중…</p>;
  if (resource.error) return <div className="community-error" role="alert"><p>{resource.error}</p>
    <button className="secondary" onClick={resource.retry}>{label} 다시 불러오기</button></div>;
  return null;
}

function Pagination({ value, onChange, label, disabled = false }) {
  if (!value || value.totalPages <= 1) return null;
  return <nav className="community-pagination" aria-label={`${label} 페이지`}>
    <button className="secondary" disabled={disabled || value.number === 0} onClick={() => onChange(value.number - 1)}>{label} 이전</button>
    <span aria-live="polite">{value.number + 1} / {value.totalPages}</span>
    <button className="secondary" disabled={disabled || value.number + 1 >= value.totalPages} onClick={() => onChange(value.number + 1)}>{label} 다음</button>
  </nav>;
}

function DeleteDialog({ title, description, busy, error, onClose, onDelete }) {
  return <Modal title={title} onClose={() => !busy && onClose()}>
    <h2>{title}</h2><p className="helper">{description}</p>
    {error && <p className="community-error" role="alert">{error}</p>}
    <div className="modal-actions">
      <button className="secondary" disabled={busy} onClick={onClose}>취소</button>
      <button className="primary" disabled={busy} onClick={onDelete}>{busy ? "삭제 중…" : "삭제하기"}</button>
    </div>
  </Modal>;
}

/** 커뮤니티 목록·상세·편집 화면을 서버 인증 상태와 연결한다. */
export default function CommunityPage({ api: providedApi }) {
  const api = useMemo(() => providedApi || createCommunityApi(), [providedApi]);
  const [view, setView] = useState(route);
  const sessionResource = useResource(useCallback(signal => api.session({ signal }), [api]));
  const session = sessionResource.data || guest;
  useEffect(() => {
    const change = () => setView(route());
    window.addEventListener("hashchange", change);
    return () => window.removeEventListener("hashchange", change);
  }, []);
  const props = { api, session, sessionReady: !sessionResource.loading, onPermissionError: sessionResource.retry };
  return <section className="community" aria-label="커뮤니티">
    <PageHeading kicker="COMMUNITY" title="같이 알아보는 자취 생활."
      description="방 구하기부터 일상의 작은 팁까지, 궁금한 이야기를 나눠요.">
      {view.kind === "list" && session.authenticated && <a className="primary" href="#community/new"><Icon name="plus" />글 작성</a>}
    </PageHeading>
    {!providedApi && <DemoLoginPanel session={session} onChanged={sessionResource.retry} />}
    <div className="community-session">
      <p>{sessionResource.loading ? "로그인 상태 확인 중…" : session.authenticated
        ? `${session.administrator ? "관리자로" : "회원으로"} 이용 중입니다.`
        : "누구나 글을 읽을 수 있어요. 작성·댓글·좋아요는 로그인 후 이용할 수 있습니다."}</p>
      <button className="text-button" disabled={sessionResource.loading} onClick={sessionResource.retry}>로그인 상태 새로고침</button>
    </div>
    {sessionResource.error && <p className="community-error" role="alert">{sessionResource.error} 공개 글은 계속 조회할 수 있습니다.</p>}
    {view.kind === "list" && <PostList {...props} />}
    {view.kind === "detail" && <PostDetail key={view.id} {...props} postId={view.id} />}
    {(view.kind === "new" || view.kind === "edit") && <PostEditor key={`${view.kind}-${view.id || "new"}`} {...props} postId={view.id} />}
    {view.kind === "missing" && <div className="empty">찾을 수 없는 커뮤니티 주소입니다. <a href="#community">목록으로</a></div>}
  </section>;
}

function PostList({ api }) {
  const [page, setPage] = useState(0);
  const resource = useResource(useCallback(signal => api.listPosts(page, { signal }), [api, page]));
  const data = resource.data;
  return <section className="panel community-list" aria-label="게시글 목록">
    <div className="section-title"><h2>이웃들의 이야기</h2>{data && <span className="badge muted">전체 {data.totalElements}개</span>}</div>
    <ResourceState resource={resource} label="게시글" />
    {data && !data.content.length && <div className="empty"><strong>아직 등록된 이야기가 없어요.</strong><p>첫 번째 이야기를 기다리고 있습니다.</p></div>}
    {data?.content.map(post => <a key={post.id} className="community-post-row" href={postLink(post.id)}>
      <div className="community-post-copy"><span className={`community-kind ${post.notice ? "is-notice" : ""}`}>{post.notice ? "공지" : "이야기"}</span>
        <h3>{post.title}</h3><p>{post.content}</p></div>
      <div className="community-meta"><span>{author(post.authorId)}</span><time dateTime={post.createdAt}>{date(post.createdAt)}</time></div>
      <Icon name="arrow" />
    </a>)}
    <Pagination value={data} onChange={setPage} label="게시글" />
  </section>;
}

function PostDetail({ api, postId, session, sessionReady, onPermissionError }) {
  const resource = useResource(useCallback(signal => api.getPost(postId, { signal }), [api, postId]));
  const likeResource = useResource(useCallback(signal => sessionReady ? api.getLikes(postId, { signal }) : Promise.resolve(null), [api, postId, session.userId, sessionReady]));
  const mutation = useMutation(onPermissionError);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const post = resource.data;
  return <>
    <a className="text-button community-back" href="#community">← 게시글 목록</a>
    <ResourceState resource={resource} label="게시글" />
    {post && <article className="panel community-detail">
      <span className={`community-kind ${post.notice ? "is-notice" : ""}`}>{post.notice ? "공지사항" : "이웃 이야기"}</span>
      <h2>{post.title}</h2>
      <div className="community-detail-meta"><span>{author(post.authorId)}</span>
        <time dateTime={post.createdAt}>{date(post.createdAt)}</time>
        {post.updatedAt !== post.createdAt && <span>수정 {date(post.updatedAt)}</span>}</div>
      <p className="community-body">{post.content}</p>
      <div className="community-detail-actions">
        <div><ResourceState resource={likeResource} label="좋아요" />
          {likeResource.data && <button className={`secondary community-like ${likeResource.data.liked ? "is-liked" : ""}`}
            disabled={!session.authenticated || mutation.busy || likeResource.loading} aria-pressed={likeResource.data.liked}
            onClick={() => mutation.run(() => api.setLike(postId, !likeResource.data.liked), likeResource.replace)}>
            {likeResource.data.liked ? "♥" : "♡"} 좋아요 {likeResource.data.count}
          </button>}</div>
        {managesPost(post, session) && <div className="community-inline-actions">
          <a className="text-button" href={`${postLink(postId)}/edit`}>게시글 수정</a>
          <button className="text-button danger" disabled={mutation.busy} onClick={() => { mutation.clearError(); setConfirmDelete(true); }}>게시글 삭제</button>
        </div>}
      </div>
      {mutation.error && !confirmDelete && <p className="community-error" role="alert">{mutation.error}</p>}
    </article>}
    {post && <Comments api={api} postId={postId} session={session} onPermissionError={onPermissionError} />}
    {confirmDelete && <DeleteDialog title="게시글을 삭제할까요?" description="게시글과 연결된 댓글·좋아요가 함께 삭제됩니다."
      busy={mutation.busy} error={mutation.error} onClose={() => setConfirmDelete(false)}
      onDelete={() => mutation.run(() => api.deletePost(postId), () => { window.location.hash = "community"; })} />}
  </>;
}

function PostEditor(props) {
  const { api, postId } = props;
  const resource = useResource(useCallback(signal => postId ? api.getPost(postId, { signal }) : Promise.resolve(null), [api, postId]));
  return <>
    <a className="text-button community-back" href={postId ? postLink(postId) : "#community"}>← {postId ? "게시글로" : "목록으로"}</a>
    {postId && <ResourceState resource={resource} label="게시글" />}
    {(!postId || resource.data) && <PostForm {...props} post={resource.data} />}
  </>;
}

function PostForm({ api, postId, post, session, onPermissionError }) {
  const [title, setTitle] = useState(post?.title || "");
  const [content, setContent] = useState(post?.content || "");
  const [notice, setNotice] = useState(false);
  const [validation, setValidation] = useState("");
  const mutation = useMutation(onPermissionError);
  const allowed = post ? managesPost(post, session) : session.authenticated && (!notice || session.administrator);
  async function submit(event) {
    event.preventDefault();
    if (!allowed) return;
    if (!title.trim() || !content.trim() || title.length > 200 || new TextEncoder().encode(content).length > 65535) {
      setValidation("제목과 내용을 확인해 주세요. 제목은 200자, 본문은 UTF-8 기준 65,535바이트까지 입력할 수 있습니다.");
      return;
    }
    setValidation("");
    mutation.run(() => postId ? api.updatePost(postId, { title, content }) : api.createPost({ title, content }, notice),
      saved => { window.location.hash = postLink(saved.id); });
  }
  return <section className="panel community-editor">
    <h2>{post ? "게시글 수정" : "새 이야기 작성"}</h2>
    {!allowed && <p className="notice">{session.authenticated ? "이 게시글을 작성하거나 수정할 권한이 없습니다." : "로그인 후 작성·수정할 수 있습니다."}</p>}
    <form onSubmit={submit}>
      <fieldset disabled={!allowed || mutation.busy}>
        <label className="field">제목<input required maxLength={200} value={title} onChange={event => setTitle(event.target.value)} placeholder="어떤 이야기를 나누고 싶으세요?" /></label>
        <label className="field">내용<textarea required rows={12} value={content} onChange={event => setContent(event.target.value)} placeholder="궁금한 점이나 도움이 될 정보를 적어 주세요." /></label>
      </fieldset>
      {!post && session.administrator && <label className="community-notice-option"><input type="checkbox" checked={notice} disabled={mutation.busy} onChange={event => setNotice(event.target.checked)} />공지사항으로 등록</label>}
      {post?.notice && <p className="helper">공지사항을 수정하고 있습니다.</p>}
      {(validation || mutation.error) && <p className="community-error" role="alert">{validation || mutation.error}</p>}
      <div className="community-form-actions"><a className="secondary" href={postId ? postLink(postId) : "#community"}>취소</a>
        <button className="primary" disabled={!allowed || mutation.busy} type="submit">{mutation.busy ? "저장 중…" : post ? "변경 저장" : "게시글 등록"}</button></div>
    </form>
  </section>;
}

function Comments({ api, postId, session, onPermissionError }) {
  const [page, setPage] = useState(0);
  const [draft, setDraft] = useState("");
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [message, setMessage] = useState("");
  const [validation, setValidation] = useState("");
  const resource = useResource(useCallback(signal => api.listComments(postId, page, { signal }), [api, postId, page]));
  const mutation = useMutation(onPermissionError);
  const refresh = () => { setEditing(null); resource.retry(); };
  function submit(event, commentId) {
    event.preventDefault();
    const content = commentId ? editing.content : draft;
    if (!content.trim() || new TextEncoder().encode(content).length > 65535) { setValidation("댓글 내용을 확인해 주세요. UTF-8 기준 65,535바이트까지 입력할 수 있습니다."); return; }
    setValidation("");
    mutation.run(() => commentId ? api.updateComment(postId, commentId, content) : api.createComment(postId, content), () => {
      setMessage(commentId ? "댓글을 수정했습니다." : "댓글을 등록했습니다.");
      if (!commentId) {
        setDraft("");
        const lastPage = Math.floor((resource.data?.totalElements || 0) / 10);
        setPage(lastPage);
      }
      refresh();
    });
  }
  return <section className="panel community-comments" aria-label="댓글">
    <div className="section-title"><h2>함께 나누는 답변</h2>{resource.data && <span className="badge muted">댓글 {resource.data.totalElements}개</span>}</div>
    <ResourceState resource={resource} label="댓글" />
    {resource.data?.content.map(comment => <article key={comment.id} className="community-comment">
      <div className="community-comment-head"><strong>{author(comment.authorId)}</strong><time dateTime={comment.createdAt}>{date(comment.createdAt)}</time>
        {session.authenticated && (session.administrator || owns(comment, session)) && <div className="community-inline-actions">
          <button className="text-button" disabled={mutation.busy} onClick={() => { mutation.clearError(); setEditing({ id: comment.id, content: comment.content }); }}>댓글 수정</button>
          <button className="text-button danger" disabled={mutation.busy} onClick={() => { mutation.clearError(); setDeleting(comment); }}>댓글 삭제</button>
        </div>}</div>
      {editing?.id === comment.id ? <form onSubmit={event => submit(event, comment.id)}>
        <label className="field">댓글 수정 내용<textarea required rows={3} value={editing.content} disabled={mutation.busy || !session.authenticated}
          onChange={event => setEditing({ ...editing, content: event.target.value })} /></label>
        <div className="community-form-actions"><button className="secondary" type="button" disabled={mutation.busy} onClick={() => setEditing(null)}>수정 취소</button>
          <button className="primary" disabled={mutation.busy || !session.authenticated}>댓글 변경 저장</button></div>
      </form> : <p className="community-body">{comment.content}</p>}
    </article>)}
    {resource.data && !resource.data.content.length && <p className="community-state">아직 댓글이 없어요. 첫 답변을 남겨 주세요.</p>}
    <Pagination value={resource.data} label="댓글" disabled={mutation.busy} onChange={number => { setPage(number); setEditing(null); }} />
    <form className="community-comment-form" onSubmit={event => submit(event)}>
      <label className="field">댓글 작성<textarea required rows={3} value={draft} disabled={!session.authenticated || mutation.busy}
        onChange={event => setDraft(event.target.value)} placeholder={session.authenticated ? "다른 이웃에게 도움이 되는 답변을 남겨 주세요." : "로그인 후 댓글을 작성할 수 있습니다."} /></label>
      <div className="community-form-actions"><button className="primary" disabled={!session.authenticated || mutation.busy || !resource.data}>{mutation.busy ? "처리 중…" : "댓글 등록"}</button></div>
    </form>
    {(validation || (mutation.error && !deleting)) && <p className="community-error" role="alert">{validation || mutation.error}</p>}
    <p className="helper" role="status">{message}</p>
    {deleting && <DeleteDialog title="댓글을 삭제할까요?" description="삭제한 댓글은 되돌릴 수 없습니다." busy={mutation.busy} error={mutation.error}
      onClose={() => setDeleting(null)} onDelete={() => mutation.run(() => api.deleteComment(postId, deleting.id), () => {
        setDeleting(null); setMessage("댓글을 삭제했습니다.");
        if (page > 0 && resource.data?.content.length === 1) setPage(page - 1);
        refresh();
      })} />}
  </section>;
}

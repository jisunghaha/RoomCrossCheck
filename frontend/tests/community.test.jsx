import React from "react";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import CommunityPage from "../src/features/community/CommunityPage.jsx";
import { CommunityApiError } from "../src/features/community/api.js";
import App from "../src/app/App.jsx";
import { RoomProvider } from "../src/app/state/RoomContext.jsx";

const guest = { authenticated: false, userId: null, administrator: false };
const member = { authenticated: true, userId: 15, administrator: false };
const admin = { authenticated: true, userId: 30, administrator: true };
const post = { id: "p1", authorId: 15, title: "자취방 계약 질문", content: "계약 전 어떤 점을 확인하나요?", notice: false, createdAt: "2026-11-05T00:00:00Z", updatedAt: "2026-11-05T00:00:00Z" };
const comment = { id: "c1", postId: "p1", authorId: 15, content: "등기부터 확인하세요.", createdAt: post.createdAt, updatedAt: post.updatedAt };
const page = (content, number = 0, totalElements = content.length) => ({ content, number, size: 10, totalElements, totalPages: Math.ceil(totalElements / 10), first: number === 0, last: (number + 1) * 10 >= totalElements });

function fakeApi(session = guest) {
  return {
    session: vi.fn().mockResolvedValue(session), listPosts: vi.fn().mockResolvedValue(page([post])),
    getPost: vi.fn().mockResolvedValue({ ...post }), createPost: vi.fn().mockImplementation(async values => ({ ...post, ...values })),
    updatePost: vi.fn().mockImplementation(async (_id, values) => ({ ...post, ...values })), deletePost: vi.fn().mockResolvedValue(),
    listComments: vi.fn().mockResolvedValue(page([comment])), createComment: vi.fn().mockResolvedValue(comment),
    updateComment: vi.fn().mockResolvedValue(comment), deleteComment: vi.fn().mockResolvedValue(),
    getLikes: vi.fn().mockResolvedValue({ postId: "p1", count: 2, liked: false }),
    setLike: vi.fn().mockResolvedValue({ postId: "p1", count: 3, liked: true }),
  };
}

function setup(api, hash = "#community") {
  history.replaceState(null, "", `/${hash}`);
  render(<CommunityPage api={api} />);
  return userEvent.setup();
}

async function go(hash) {
  await act(async () => { window.location.hash = hash; window.dispatchEvent(new HashChangeEvent("hashchange")); });
}

test("guest can browse posts, comments and likes without write controls", async () => {
  const api = fakeApi();
  const user = setup(api);
  await user.click(await screen.findByRole("link", { name: /자취방 계약 질문/ }));
  expect(await screen.findByRole("heading", { name: post.title })).toBeInTheDocument();
  expect(await screen.findByText(comment.content)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: /좋아요 2/ })).toBeDisabled();
  expect(screen.getByLabelText("댓글 작성")).toBeDisabled();
  expect(screen.queryByRole("button", { name: "게시글 삭제" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "댓글 수정" })).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "글 작성" })).not.toBeInTheDocument();
});

test("post list pagination requests the next server page", async () => {
  const api = fakeApi();
  api.listPosts.mockImplementation(async number => page([{ ...post, title: `페이지 ${number + 1}` }], number, 11));
  const user = setup(api);
  await user.click(await screen.findByRole("button", { name: "게시글 다음" }));
  expect(await screen.findByRole("heading", { name: "페이지 2" })).toBeInTheDocument();
  expect(api.listPosts).toHaveBeenLastCalledWith(1, expect.objectContaining({ signal: expect.any(AbortSignal) }));
  expect(screen.getByRole("button", { name: "게시글 다음" })).toBeDisabled();
});

test("member creates a post and navigates to its detail", async () => {
  const api = fakeApi(member);
  const user = setup(api);
  await user.click(await screen.findByRole("link", { name: "글 작성" }));
  await user.type(await screen.findByLabelText("제목"), "새로운 질문");
  await user.type(screen.getByLabelText("내용"), "등록할 내용");
  await user.click(screen.getByRole("button", { name: "게시글 등록" }));
  await waitFor(() => expect(api.createPost).toHaveBeenCalledWith({ title: "새로운 질문", content: "등록할 내용" }, false));
  expect(await screen.findByRole("heading", { name: post.title })).toBeInTheDocument();
  expect(screen.queryByLabelText("공지사항으로 등록")).not.toBeInTheDocument();
});

test("admin can select notice creation", async () => {
  const api = fakeApi(admin);
  const user = setup(api, "#community/new");
  await user.type(await screen.findByLabelText("제목"), "공지 제목");
  await user.type(screen.getByLabelText("내용"), "공지 내용");
  await user.click(screen.getByLabelText("공지사항으로 등록"));
  await user.click(screen.getByRole("button", { name: "게시글 등록" }));
  await waitFor(() => expect(api.createPost).toHaveBeenCalledWith({ title: "공지 제목", content: "공지 내용" }, true));
});

test("failed save preserves draft and repeated clicks do not duplicate writes", async () => {
  const api = fakeApi(member);
  let reject;
  api.createPost.mockImplementation(() => new Promise((_resolve, fail) => { reject = fail; }));
  const user = setup(api, "#community/new");
  await user.type(await screen.findByLabelText("제목"), "보존할 제목");
  await user.type(screen.getByLabelText("내용"), "보존할 내용");
  await user.dblClick(screen.getByRole("button", { name: "게시글 등록" }));
  expect(api.createPost).toHaveBeenCalledTimes(1);
  await act(async () => reject(new CommunityApiError("서버 저장 실패", 500)));
  expect(await screen.findByRole("alert")).toHaveTextContent("서버 저장 실패");
  expect(screen.getByLabelText("제목")).toHaveValue("보존할 제목");
  expect(screen.getByLabelText("내용")).toHaveValue("보존할 내용");
});

test("401 refreshes session and preserves the failed draft", async () => {
  const api = fakeApi(member);
  api.session.mockResolvedValueOnce(member).mockResolvedValue(guest);
  api.createPost.mockRejectedValue(new CommunityApiError("로그인이 필요합니다.", 401));
  const user = setup(api, "#community/new");
  await user.type(await screen.findByLabelText("제목"), "세션 만료 전 제목");
  await user.type(screen.getByLabelText("내용"), "작성한 내용");
  await user.click(screen.getByRole("button", { name: "게시글 등록" }));
  await waitFor(() => expect(api.session).toHaveBeenCalledTimes(2));
  expect(screen.getByLabelText("제목")).toHaveValue("세션 만료 전 제목");
  expect(screen.getByRole("button", { name: "게시글 등록" })).toBeDisabled();
});

test("owners can edit while other members cannot manage the same content", async () => {
  const api = fakeApi(member);
  const user = setup(api, "#community/post/p1");
  await user.click(await screen.findByRole("link", { name: "게시글 수정" }));
  const title = await screen.findByLabelText("제목");
  await user.clear(title);
  await user.type(title, "수정된 제목");
  await user.click(screen.getByRole("button", { name: "변경 저장" }));
  await waitFor(() => expect(api.updatePost).toHaveBeenCalledWith("p1", { title: "수정된 제목", content: post.content }));
  api.session.mockResolvedValue({ ...member, userId: 20 });
  await user.click(screen.getByRole("button", { name: "로그인 상태 새로고침" }));
  await waitFor(() => expect(screen.queryByRole("link", { name: "게시글 수정" })).not.toBeInTheDocument());
  expect(screen.queryByRole("button", { name: "댓글 삭제" })).not.toBeInTheDocument();
});

test("notice owner needs admin role to edit or delete", async () => {
  const api = fakeApi(member);
  api.getPost.mockResolvedValue({ ...post, notice: true });
  const user = setup(api, "#community/post/p1");
  await screen.findByRole("heading", { name: post.title });
  expect(screen.queryByRole("link", { name: "게시글 수정" })).not.toBeInTheDocument();
  api.session.mockResolvedValue(admin);
  await user.click(screen.getByRole("button", { name: "로그인 상태 새로고침" }));
  expect(await screen.findByRole("link", { name: "게시글 수정" })).toBeInTheDocument();
});

test("post deletion requires confirmation and returns to list", async () => {
  const api = fakeApi(member);
  const user = setup(api, "#community/post/p1");
  await user.click(await screen.findByRole("button", { name: "게시글 삭제" }));
  expect(api.deletePost).not.toHaveBeenCalled();
  const dialog = within(screen.getByRole("dialog"));
  await user.click(dialog.getByRole("button", { name: "삭제하기" }));
  await waitFor(() => expect(api.deletePost).toHaveBeenCalledWith("p1"));
  expect(await screen.findByRole("heading", { name: "이웃들의 이야기" })).toBeInTheDocument();
});

test("member creates, edits and deletes a comment", async () => {
  const api = fakeApi(member);
  const user = setup(api, "#community/post/p1");
  await screen.findByText(comment.content);
  await user.type(screen.getByLabelText("댓글 작성"), "새 댓글");
  await user.click(screen.getByRole("button", { name: "댓글 등록" }));
  await waitFor(() => expect(api.createComment).toHaveBeenCalledWith("p1", "새 댓글"));
  expect(screen.getByLabelText("댓글 작성")).toHaveValue("");
  await user.click(await screen.findByRole("button", { name: "댓글 수정" }));
  await user.clear(screen.getByLabelText("댓글 수정 내용"));
  await user.type(screen.getByLabelText("댓글 수정 내용"), "수정 댓글");
  await user.click(screen.getByRole("button", { name: "댓글 변경 저장" }));
  await waitFor(() => expect(api.updateComment).toHaveBeenCalledWith("p1", "c1", "수정 댓글"));
  await user.click(await screen.findByRole("button", { name: "댓글 삭제" }));
  await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "삭제하기" }));
  await waitFor(() => expect(api.deleteComment).toHaveBeenCalledWith("p1", "c1"));
});

test("like registration and cancellation display server state", async () => {
  const api = fakeApi(member);
  api.setLike.mockImplementation(async (_id, liked) => ({ postId: "p1", count: liked ? 3 : 2, liked }));
  const user = setup(api, "#community/post/p1");
  const like = await screen.findByRole("button", { name: /좋아요 2/ });
  await waitFor(() => expect(like).toBeEnabled());
  await user.click(like);
  const liked = await screen.findByRole("button", { name: /좋아요 3/ });
  expect(liked).toHaveAttribute("aria-pressed", "true");
  await user.click(liked);
  await screen.findByRole("button", { name: /좋아요 2/ });
  expect(api.setLike.mock.calls).toEqual([["p1", true], ["p1", false]]);
});

test("empty and failed lists have distinct states and can retry", async () => {
  const api = fakeApi();
  api.listPosts.mockRejectedValueOnce(new CommunityApiError("서버 연결 실패")).mockResolvedValue(page([]));
  const user = setup(api);
  expect(await screen.findByRole("alert")).toHaveTextContent("서버 연결 실패");
  await user.click(screen.getByRole("button", { name: "게시글 다시 불러오기" }));
  expect(await screen.findByText("아직 등록된 이야기가 없어요.")).toBeInTheDocument();
});

test("late detail responses do not overwrite the newer route and HTML remains text", async () => {
  const api = fakeApi();
  let resolveFirst;
  api.getPost.mockImplementation(id => id === "p1" ? new Promise(resolve => { resolveFirst = resolve; })
    : Promise.resolve({ ...post, id: "p2", title: "두 번째 게시글", content: "<img src=x onerror=alert(1)>" }));
  setup(api, "#community/post/p1");
  await waitFor(() => expect(api.getPost).toHaveBeenCalled());
  await go("#community/post/p2");
  expect(await screen.findByRole("heading", { name: "두 번째 게시글" })).toBeInTheDocument();
  await act(async () => resolveFirst(post));
  expect(screen.queryByRole("heading", { name: post.title })).not.toBeInTheDocument();
  expect(screen.getByText("<img src=x onerror=alert(1)>")).toBeInTheDocument();
  expect(document.querySelector(".community-body img")).toBeNull();
});

test("community route remains accessible when listing API fails", async () => {
  history.replaceState(null, "", "/#community");
  vi.stubGlobal("fetch", vi.fn(async url => ({ ok: true, status: 200, json: async () => String(url).endsWith("/session") ? guest : page([]) })));
  const repository = { mode: "listing-api", initialState: () => ({ rooms: [], reviews: [], visits: {} }), load: vi.fn().mockRejectedValue(new Error("매물 서버 오류")) };
  try {
    render(<RoomProvider repository={repository}><App /></RoomProvider>);
    expect(await screen.findByText("아직 등록된 이야기가 없어요.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "커뮤니티" })).toHaveAttribute("aria-current", "page");
    expect(screen.queryByText("매물 서버 오류")).not.toBeInTheDocument();
  } finally { vi.unstubAllGlobals(); }
});

test("authentication changes refresh community permissions without resetting the current route", async () => {
  history.replaceState(null, "", "/#community/post/p1");
  const api = fakeApi();
  const { rerender } = render(<CommunityPage api={api} authRevision={0} />);
  expect(await screen.findByRole("heading", { name: post.title })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "게시글 수정" })).not.toBeInTheDocument();
  api.session.mockResolvedValue(member);
  rerender(<CommunityPage api={api} authRevision={1} />);
  expect(await screen.findByRole("link", { name: "게시글 수정" })).toBeInTheDocument();
  api.session.mockResolvedValue(guest);
  rerender(<CommunityPage api={api} authRevision={2} />);
  await waitFor(() => expect(screen.queryByRole("link", { name: "게시글 수정" })).not.toBeInTheDocument());
  expect(location.hash).toBe("#community/post/p1");
});

import { expect, test, vi } from "vitest";
import { createCommunityApi } from "../src/features/community/api.js";

const response = (value, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => value });

test("community API sends only editable post fields and server session cookies", async () => {
  const fetchImpl = vi.fn().mockResolvedValue(response({ id: "p1" }, 201));
  const api = createCommunityApi({ fetchImpl });
  await api.createPost({ title: "제목", content: "내용", authorId: 999, role: "ADMIN", notice: true });
  const [url, options] = fetchImpl.mock.calls[0];
  expect(url).toBe("/api/community/posts");
  expect(options.credentials).toBe("include");
  expect(JSON.parse(options.body)).toEqual({ title: "제목", content: "내용" });
  await api.createPost({ title: "공지", content: "내용" }, true);
  expect(fetchImpl.mock.calls[1][0]).toBe("/api/community/notices");
});

test("like and comment routes encode IDs and match the backend contract", async () => {
  const fetchImpl = vi.fn().mockResolvedValue(response({}));
  const api = createCommunityApi({ fetchImpl });
  await api.setLike("p/1", true);
  expect(fetchImpl.mock.calls[0][0]).toBe("/api/community/posts/p%2F1/likes");
  expect(fetchImpl.mock.calls[0][1].method).toBe("PUT");
  await api.setLike("p/1", false);
  expect(fetchImpl.mock.calls[1][1].method).toBe("DELETE");
  await api.updateComment("p1", "c/1", "수정 댓글");
  expect(fetchImpl.mock.calls[2][0]).toBe("/api/community/posts/p1/comments/c%2F1");
  expect(JSON.parse(fetchImpl.mock.calls[2][1].body)).toEqual({ content: "수정 댓글" });
});

test("204 deletion does not require a JSON response", async () => {
  const fetchImpl = vi.fn().mockResolvedValue({ status: 204, ok: true, json: vi.fn() });
  await expect(createCommunityApi({ fetchImpl }).deletePost("p1")).resolves.toBeUndefined();
  expect((await fetchImpl.mock.results[0].value).json).not.toHaveBeenCalled();
});

test("API errors retain the HTTP status and server message", async () => {
  const fetchImpl = vi.fn().mockResolvedValue(response({ message: "권한이 없습니다." }, 403));
  await expect(createCommunityApi({ fetchImpl }).getPost("p1")).rejects.toMatchObject({ status: 403, message: "권한이 없습니다." });
});

test("HTML responses and invalid list or session shapes fail closed", async () => {
  const fetchImpl = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => { throw new Error("HTML"); } });
  const api = createCommunityApi({ fetchImpl });
  await expect(api.listPosts()).rejects.toThrow("응답 형식");
  fetchImpl.mockResolvedValue(response({ content: [] }));
  await expect(api.listPosts()).rejects.toThrow("목록 응답 형식");
  fetchImpl.mockResolvedValue(response({ authenticated: true, userId: "15", administrator: true }));
  await expect(api.session()).rejects.toThrow("로그인 상태");
  fetchImpl.mockResolvedValue(response({ authenticated: false, userId: 15, administrator: true }));
  await expect(api.session()).resolves.toEqual({ authenticated: false, userId: null, administrator: false });
});

test("unavailable servers show connection errors without browser data fallback", async () => {
  const api = createCommunityApi({ fetchImpl: vi.fn().mockRejectedValue(new TypeError("offline")) });
  await expect(api.getPost("p1")).rejects.toThrow("서버에 연결하지 못했습니다");
});

test("timeouts abort the request and external cancellation remains distinguishable", async () => {
  const fetchImpl = vi.fn((_url, { signal }) => new Promise((_resolve, reject) => {
    signal.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")), { once: true });
  }));
  await expect(createCommunityApi({ fetchImpl, timeoutMs: 10 }).getPost("p1")).rejects.toThrow("응답 시간이 초과");
  const controller = new AbortController();
  const pending = createCommunityApi({ fetchImpl }).getPost("p1", { signal: controller.signal });
  controller.abort();
  await expect(pending).rejects.toMatchObject({ name: "AbortError" });
});

/** 서버가 반환한 상태를 화면에서 구분하기 위한 API 오류다. */
export class CommunityApiError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = "CommunityApiError";
    this.status = status;
  }
}

/**
 * 커뮤니티 API 클라이언트를 생성한다. 매물의 브라우저 저장 모드와 관계없이 서버를 사용한다.
 * @param {Object} [options] API 기본 경로, 요청 함수와 제한 시간
 * @return {Object} 게시글·댓글·좋아요 및 인증 상태 요청 함수
 */
export function createCommunityApi({ baseUrl = "/api", fetchImpl = globalThis.fetch, timeoutMs = 15000 } = {}) {
  const base = `${baseUrl.replace(/\/$/, "")}/community`;
  const path = id => `/posts/${encodeURIComponent(id)}`;

  async function request(route, method = "GET", body, { signal } = {}) {
    const controller = new AbortController();
    let timedOut = false;
    const abort = () => controller.abort();
    if (signal?.aborted) abort();
    signal?.addEventListener("abort", abort, { once: true });
    const timer = setTimeout(() => { timedOut = true; abort(); }, timeoutMs);
    try {
      const headers = { Accept: "application/json" };
      if (body !== undefined) headers["Content-Type"] = "application/json";
      // 인증은 쿠키로 전달한다. Spring Security가 해당 CSRF 쿠키 방식을 사용하면 토큰도 전달한다.
      const csrfCookie = globalThis.document?.cookie.split("; ").find(value => value.startsWith("XSRF-TOKEN="));
      if (method !== "GET" && csrfCookie) headers["X-XSRF-TOKEN"] = decodeURIComponent(csrfCookie.slice(11));
      const response = await fetchImpl(`${base}${route}`, {
        method, headers, credentials: "include", signal: controller.signal,
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
      if (response.status === 204) return undefined;
      let value;
      try { value = await response.json(); } catch {
        throw new CommunityApiError("커뮤니티 서버의 응답 형식이 올바르지 않습니다.", response.status);
      }
      if (!response.ok) throw new CommunityApiError(value?.message || "요청을 처리하지 못했습니다.", response.status);
      return value;
    } catch (error) {
      if (error instanceof CommunityApiError || (signal?.aborted && !timedOut)) throw error;
      throw new CommunityApiError(timedOut ? "응답 시간이 초과되었습니다. 다시 시도해 주세요."
        : "커뮤니티 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      clearTimeout(timer);
      signal?.removeEventListener("abort", abort);
    }
  }

  async function page(route, options) {
    const value = await request(route, "GET", undefined, options);
    if (!Array.isArray(value?.content) || !Number.isInteger(value.number) || !Number.isInteger(value.totalPages)
        || !Number.isInteger(value.totalElements) || value.totalPages < 0 || value.totalElements < 0) {
      throw new CommunityApiError("커뮤니티 목록 응답 형식이 올바르지 않습니다.");
    }
    return value;
  }

  return {
    async session(options) {
      const value = await request("/session", "GET", undefined, options);
      if (typeof value?.authenticated !== "boolean" || typeof value.administrator !== "boolean"
          || (value.authenticated && (!Number.isSafeInteger(value.userId) || value.userId <= 0))) {
        throw new CommunityApiError("로그인 상태를 확인하지 못했습니다.");
      }
      return value.authenticated ? value : { authenticated: false, userId: null, administrator: false };
    },
    listPosts: (number = 0, options) => page(`/posts?page=${number}&size=10`, options),
    getPost: (id, options) => request(path(id), "GET", undefined, options),
    createPost: (values, notice = false) => request(notice ? "/notices" : "/posts", "POST", {
      title: values.title, content: values.content,
    }),
    updatePost: (id, values) => request(path(id), "PATCH", { title: values.title, content: values.content }),
    deletePost: id => request(path(id), "DELETE"),
    listComments: (id, number = 0, options) => page(`${path(id)}/comments?page=${number}&size=10`, options),
    createComment: (id, content) => request(`${path(id)}/comments`, "POST", { content }),
    updateComment: (id, commentId, content) => request(`${path(id)}/comments/${encodeURIComponent(commentId)}`, "PATCH", { content }),
    deleteComment: (id, commentId) => request(`${path(id)}/comments/${encodeURIComponent(commentId)}`, "DELETE"),
    getLikes: (id, options) => request(`${path(id)}/likes`, "GET", undefined, options),
    setLike: (id, liked) => request(`${path(id)}/likes`, liked ? "PUT" : "DELETE"),
  };
}

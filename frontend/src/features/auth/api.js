/** 인증 요청의 HTTP 상태를 화면에 전달한다. 비밀번호나 요청 본문은 보관하지 않는다. */
export class AuthApiError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.status = status;
  }
}

/**
 * 기존 회원 인증 API를 쿠키 세션과 연결한다. 인증 정보는 브라우저 저장소에 기록하지 않는다.
 * @param {Object} [options] 테스트용 요청 함수와 요청 제한 시간
 * @return {Object} 본인 조회·가입·로그인·로그아웃 요청 함수
 */
export function createAuthApi({ fetchImpl = globalThis.fetch, timeoutMs = 15000 } = {}) {
  function member(value) {
    if (!Number.isSafeInteger(value?.id) || value.id <= 0
        || !["loginId", "nickname", "email"].every(key => typeof value[key] === "string" && value[key].length > 0)
        || !["USER", "ADMIN"].includes(value.role)) {
      throw new AuthApiError("회원 정보 응답 형식이 올바르지 않습니다.");
    }
    return { id: value.id, loginId: value.loginId, nickname: value.nickname, email: value.email, role: value.role };
  }
  async function request(path, options = {}) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await fetchImpl(`/api/auth/${path}`, {
        ...options, credentials: "include", signal: controller.signal,
        headers: { Accept: "application/json", ...options.headers },
      });
      if (response.status === 204) return null;
      const value = await response.json().catch(() => null);
      if (!response.ok) throw new AuthApiError(value?.message || "인증 요청을 처리하지 못했습니다.", response.status);
      if (!value) throw new AuthApiError("인증 서버의 응답을 확인하지 못했습니다.");
      return value;
    } catch (error) {
      if (error instanceof AuthApiError) throw error;
      throw new AuthApiError(controller.signal.aborted
        ? "응답 시간이 초과되었습니다. 다시 시도해 주세요."
        : "인증 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      clearTimeout(timer);
    }
  }

  async function write(path, body, contentType) {
    await request("csrf");
    const cookie = document.cookie.split("; ").find(value => value.startsWith("XSRF-TOKEN="));
    if (!cookie) throw new AuthApiError("인증 요청을 준비하지 못했습니다. 쿠키 설정을 확인해 주세요.");
    return request(path, { method: "POST", body, headers: {
      "X-XSRF-TOKEN": decodeURIComponent(cookie.slice(11)),
      ...(contentType ? { "Content-Type": contentType } : {}),
    } });
  }

  return {
    async me() {
      try { return member(await request("me")); } catch (error) {
        if (error.status === 401) return null;
        throw error;
      }
    },
    signup: ({ loginId, password, nickname, email }) => write("signup",
      JSON.stringify({ loginId, password, nickname, email }), "application/json"),
    async login(loginId, password) {
      await write("login", new URLSearchParams({ username: loginId, password }).toString(),
        "application/x-www-form-urlencoded");
      // 로그인으로 이전 CSRF 토큰이 폐기되므로 커뮤니티 쓰기 전에 새 쿠키를 준비한다.
      await request("csrf");
      return member(await request("me"));
    },
    async logout() {
      await write("logout");
    },
  };
}

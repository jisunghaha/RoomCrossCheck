/** 서버 API 요청 실패의 HTTP 상태를 화면에 전달한다. 0이면 연결 실패다. */
export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

const defaultMessages = {
  401: "로그인이 필요해요.",
  403: "접근 권한이 없어요.",
  404: "자료를 찾지 못했어요. 새로고침 후 확인해 주세요.",
  409: "다른 변경과 충돌했어요. 새로고침 후 확인해 주세요.",
  429: "요청이 많아요. 잠시 후 다시 시도해 주세요.",
};

function csrfToken() {
  const cookie = globalThis.document?.cookie.split("; ").find(value => value.startsWith("XSRF-TOKEN="));
  return cookie ? decodeURIComponent(cookie.slice(11)) : null;
}

/**
 * 쿠키 세션과 CSRF 토큰을 붙여 회원 API를 호출하는 함수를 만든다.
 * GET이 아닌 요청 전에 CSRF 쿠키가 없으면 /auth/csrf로 먼저 발급받는다.
 * @param {Object} [options] API 기본 경로, 요청 함수와 제한 시간
 * @return {function(string, Object=): Promise<*>} 경로와 {method, json, form, headers}를 받아 JSON 응답을 돌려주는 함수
 */
export function createApiClient({ baseUrl = "/api", fetchImpl = (...args) => globalThis.fetch(...args), timeoutMs = 15000 } = {}) {
  // 기본 요청 함수는 호출 시점의 fetch를 사용해 테스트 대역과 브라우저 확장 교체를 따른다.
  const base = baseUrl.replace(/\/$/, "");
  return async function request(path, { method = "GET", json, form, headers = {} } = {}) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const finalHeaders = { Accept: "application/json", ...headers };
      if (method !== "GET") {
        if (!csrfToken()) await fetchImpl(`${base}/auth/csrf`, { credentials: "include", signal: controller.signal });
        const token = csrfToken();
        if (token) finalHeaders["X-XSRF-TOKEN"] = token;
      }
      if (json !== undefined) finalHeaders["Content-Type"] = "application/json";
      const response = await fetchImpl(`${base}${path}`, {
        method, credentials: "include", signal: controller.signal, headers: finalHeaders,
        ...(json !== undefined ? { body: JSON.stringify(json) } : form !== undefined ? { body: form } : {}),
      });
      if (response.status === 204) return undefined;
      let value = null;
      try { value = await response.json(); } catch { /* 프록시 오류 페이지는 기본 안내를 쓴다. */ }
      if (!response.ok) {
        throw new ApiError(value?.message || defaultMessages[response.status]
          || `서버 요청에 실패했어요. (${response.status})`, response.status);
      }
      return value;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError("서버에 연결하지 못했어요. 연결을 확인한 뒤 다시 시도해 주세요.");
    } finally {
      clearTimeout(timer);
    }
  };
}

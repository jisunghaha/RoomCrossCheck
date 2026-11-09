import { afterEach, expect, test, vi } from "vitest";
import { createAuthApi } from "../src/features/auth/api.js";

const member = { id: 7, loginId: "member.one", nickname: "준비하는 사람", email: "one@example.com", role: "USER" };
const response = (value, status = 200) => ({ ok: status < 400, status, json: async () => value });
afterEach(() => { document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/"; });

test("login uses form fields and cookies, renews CSRF before exposing the member", async () => {
  const fetchImpl = vi.fn(async path => {
    if (path.endsWith("/csrf")) { document.cookie = "XSRF-TOKEN=fresh-token; path=/"; return response({ ready: true }); }
    if (path.endsWith("/me")) return response(member);
    return response({ status: 200 });
  });
  const api = createAuthApi({ fetchImpl });
  expect(await api.login("member.one", "test & password")).toEqual(member);
  expect(fetchImpl.mock.calls.map(([path]) => path)).toEqual([
    "/api/auth/csrf", "/api/auth/login", "/api/auth/csrf", "/api/auth/me",
  ]);
  const options = fetchImpl.mock.calls[1][1];
  expect(options.headers["Content-Type"]).toBe("application/x-www-form-urlencoded");
  expect(options.headers["X-XSRF-TOKEN"]).toBe("fresh-token");
  expect(Object.fromEntries(new URLSearchParams(options.body))).toEqual({ username: "member.one", password: "test & password" });
  expect(options.credentials).toBe("include");
  expect(localStorage.length).toBe(0);
});

test("signup sends only the four allowed fields, excluding confirmation and role", async () => {
  document.cookie = "XSRF-TOKEN=token; path=/";
  const fetchImpl = vi.fn(async path => response(path.endsWith("signup") ? member : { ready: true }));
  await createAuthApi({ fetchImpl }).signup({ ...member, password: "password123", confirmPassword: "password123", role: "ADMIN" });
  expect(JSON.parse(fetchImpl.mock.calls[1][1].body)).toEqual({ loginId: member.loginId, password: "password123", nickname: member.nickname, email: member.email });
  expect(fetchImpl).toHaveBeenCalledTimes(2);
});

test("guest is distinct from connection failure or a malformed member response", async () => {
  expect(await createAuthApi({ fetchImpl: async () => response({ message: "로그인이 필요합니다." }, 401) }).me()).toBeNull();
  await expect(createAuthApi({ fetchImpl: async () => response({}) }).me()).rejects.toThrow("회원 정보 응답 형식");
  await expect(createAuthApi({ fetchImpl: async () => { throw new TypeError(); } }).me()).rejects.toThrow("연결하지 못했습니다");
});

test("missing CSRF prevents writes, and login failures preserve the server error", async () => {
  const missing = vi.fn(async () => response({ ready: true }));
  await expect(createAuthApi({ fetchImpl: missing }).login("member.one", "wrong")).rejects.toThrow("쿠키 설정");
  expect(missing).toHaveBeenCalledTimes(1);
  document.cookie = "XSRF-TOKEN=token; path=/";
  const denied = vi.fn(async path => path.endsWith("csrf") ? response({ ready: true }) : response({ message: "아이디 또는 비밀번호가 올바르지 않습니다." }, 401));
  await expect(createAuthApi({ fetchImpl: denied }).login("member.one", "wrong")).rejects.toMatchObject({ status: 401 });
});

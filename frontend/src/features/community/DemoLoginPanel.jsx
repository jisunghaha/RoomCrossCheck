import { useEffect, useRef, useState } from "react";

/** 서버의 시연 프로필에서만 비밀번호 로그인·로그아웃을 제공하며 자격 증명은 저장하지 않는다. */
export default function DemoLoginPanel({ session, onChanged, fetchImpl = globalThis.fetch }) {
  const [enabled, setEnabled] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const lock = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    fetchImpl("/api/auth/config", { credentials: "include", signal: controller.signal })
      .then(response => response.ok ? response.json() : null)
      .then(value => { if (!controller.signal.aborted) setEnabled(value?.demoEnabled === true); })
      .catch(() => {});
    return () => controller.abort();
  }, [fetchImpl]);

  async function submit(event) {
    event?.preventDefault();
    if (lock.current) return;
    lock.current = true; setBusy(true); setError("");
    const loggingOut = session.authenticated;
    try {
      const csrfResponse = await fetchImpl("/api/auth/csrf", { credentials: "include" });
      if (!csrfResponse.ok) throw new Error("로그인 준비에 실패했습니다. 다시 시도해 주세요.");
      const csrfCookie = document.cookie.split("; ").find(value => value.startsWith("XSRF-TOKEN="));
      if (!csrfCookie) throw new Error("보안 쿠키를 확인하지 못했습니다. 쿠키 설정을 확인해 주세요.");
      const response = await fetchImpl(`/api/auth/${loggingOut ? "logout" : "login"}`, {
        method: "POST", credentials: "include",
        headers: { "Content-Type": "application/x-www-form-urlencoded", "X-XSRF-TOKEN": decodeURIComponent(csrfCookie.slice(11)) },
        ...(loggingOut ? {} : { body: new URLSearchParams({ username, password }).toString() }),
      });
      if (!response.ok) throw new Error(response.status === 401 ? "아이디 또는 비밀번호가 올바르지 않습니다."
        : response.status === 403 ? "보안 토큰이 만료됐습니다. 다시 시도해 주세요." : "로그인 상태를 변경하지 못했습니다.");
      // 인증 성공·로그아웃 시 이전 CSRF 토큰이 폐기되므로 새 쿠키를 받는다.
      await fetchImpl("/api/auth/csrf", { credentials: "include" });
      onChanged();
    } catch (failure) {
      setError(failure instanceof TypeError ? "서버에 연결하지 못했습니다. 다시 시도해 주세요." : failure.message);
    } finally { setPassword(""); lock.current = false; setBusy(false); }
  }

  if (!enabled) return null;
  return <section className="panel community-demo-login" aria-label="시연용 로그인">
    <h2>시연용 로그인</h2>
    <p className="helper">커뮤니티 권한 확인을 위한 별도 시연 계정입니다. 정식 회원가입·Google 로그인은 제공하지 않습니다.</p>
    {session.authenticated ? <div>
      <p>{session.administrator ? "관리자" : "일반 회원"} · 회원 번호 {session.userId}</p>
      <button type="button" className="secondary" disabled={busy} onClick={() => submit()}>{busy ? "처리 중…" : "로그아웃"}</button>
    </div> : <form onSubmit={submit}>
      <fieldset disabled={busy}>
        <label className="field">시연 아이디<input name="username" autoComplete="username" required maxLength={64}
          value={username} onChange={event => setUsername(event.target.value)} /></label>
        <label className="field">시연 비밀번호<input name="password" type="password" autoComplete="current-password" required maxLength={72}
          value={password} onChange={event => setPassword(event.target.value)} /></label>
        <button className="primary" type="submit">{busy ? "로그인 중…" : "로그인"}</button>
      </fieldset>
    </form>}
    {error && <p className="community-error" role="alert">{error}</p>}
  </section>;
}

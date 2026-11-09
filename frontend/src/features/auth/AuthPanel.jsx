import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Modal from "../../shared/ui/Modal.jsx";
import { createAuthApi } from "./api.js";
import "./auth.css";

/** 왼쪽 계정 카드와 인증 모달을 관리하고, 인증 변경을 커뮤니티 화면에 알린다. */
export default function AuthPanel({ onSessionChange, api: providedApi }) {
  const api = useMemo(() => providedApi || createAuthApi(), [providedApi]);
  const [member, setMember] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [dialog, setDialog] = useState(null);
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const revision = useRef(0);
  const refresh = useCallback(async () => {
    const current = ++revision.current;
    try {
      const value = await api.me();
      if (current !== revision.current) return;
      setMember(value);
      if (!value) setDialog(currentDialog => currentDialog?.type === "profile" ? null : currentDialog);
      setError("");
      onSessionChange?.();
    } catch (failure) {
      if (current === revision.current) setError(failure.message);
    } finally {
      if (current === revision.current) setLoading(false);
    }
  }, [api, onSessionChange]);
  useEffect(() => {
    refresh();
    const focus = () => { if (!lock.current) refresh(); };
    window.addEventListener("focus", focus);
    return () => { revision.current++; window.removeEventListener("focus", focus); };
  }, [refresh]);

  async function logout() {
    if (lock.current) return;
    lock.current = true;
    revision.current++;
    setBusy(true);
    setError("");
    try {
      await api.logout();
      setMember(null);
      setDialog(null);
      onSessionChange?.();
    } catch (failure) {
      await refresh();
      setError(failure.message);
    } finally { lock.current = false; setBusy(false); }
  }

  return <>
    <section className="workspace auth-workspace" aria-label="내 계정">
      <div className="auth-workspace-heading">
        <span className="workspace-icon" aria-hidden="true">{member ? Array.from(member.nickname)[0] : "나"}</span>
        <div><strong>나의 자취 준비</strong><small>{member ? `${member.nickname}님, 안녕하세요` : "첫 자취, 꼼꼼하게"}</small></div>
      </div>
      {loading ? <p className="auth-card-note" role="status">로그인 상태 확인 중…</p> :
        <div className="auth-card-actions">
          <button className="auth-card-primary" disabled={busy} onClick={() => setDialog({ type: member ? "profile" : "login" })}>{member ? "내 정보" : "로그인"}</button>
          <button className="auth-card-secondary" disabled={busy} onClick={() => member ? logout() : setDialog({ type: "signup" })}>{member ? (busy ? "로그아웃 중…" : "로그아웃") : "회원가입"}</button>
        </div>}
      {error && <div className="auth-card-note" role="alert">{error}<button className="text-button" onClick={refresh} disabled={busy}>상태 다시 확인</button></div>}
    </section>
    {dialog && <Modal title={{ login: "로그인", signup: "회원가입", profile: "내 정보" }[dialog.type]} onClose={() => { if (!lock.current) setDialog(null); }}>
      {dialog.type === "profile" && member ? <section className="auth-dialog">
        <h2>내 정보</h2><p className="auth-intro">내 계정에 등록된 정보를 확인하세요.</p>
        <dl className="auth-profile">{[["아이디", member.loginId], ["닉네임", member.nickname], ["이메일", member.email]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
        <button className="primary auth-submit" onClick={() => setDialog(null)}>확인</button>
      </section> : <AuthForm key={dialog.type} mode={dialog.type} initialId={dialog.loginId || ""} message={dialog.message}
        onSwitch={type => setDialog({ type })} api={api} lock={lock}
        onLogin={value => {
          revision.current++;
          setMember(value); setError(""); setDialog(null); onSessionChange?.();
        }} onSignup={loginId => setDialog({ type: "login", loginId, message: "회원가입이 완료됐어요. 새 계정으로 로그인해 주세요." })} />}
    </Modal>}
  </>;
}

function AuthForm({ mode, initialId, message, api, lock, onSwitch, onLogin, onSignup }) {
  const signup = mode === "signup";
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [visible, setVisible] = useState(false);
  async function submit(event) {
    event.preventDefault();
    if (lock.current) return;
    const values = Object.fromEntries(new FormData(event.currentTarget));
    if (signup && values.password !== values.confirmPassword) {
      setError("비밀번호가 일치하지 않습니다."); return;
    }
    if (signup && new TextEncoder().encode(values.password).length > 72) {
      setError("비밀번호는 UTF-8 기준 72바이트 이하로 입력해 주세요."); return;
    }
    lock.current = true;
    setBusy(true); setError("");
    try {
      if (signup) { await api.signup(values); onSignup(values.loginId); }
      else { onLogin(await api.login(values.loginId, values.password)); }
    } catch (failure) { setError(failure.message); }
    finally { lock.current = false; setBusy(false); }
  }
  return <section className="auth-dialog">
    <span className="auth-kicker">{signup ? "START YOUR NEXT CHAPTER" : "WELCOME BACK"}</span>
    <h2>{signup ? "회원가입" : "로그인"}</h2>
    <p className="auth-intro">{signup ? "첫 자취 준비, 함께 시작해요." : "나의 자취 준비를 이어가세요."}</p>
    {message && <p className="auth-success" role="status">{message}</p>}
    <form onSubmit={submit}>
      <fieldset disabled={busy}>
        <label htmlFor="auth-login-id">아이디</label>
        <input id="auth-login-id" name="loginId" autoComplete="username" autoCapitalize="none" spellCheck={false} autoFocus required
          defaultValue={initialId} maxLength={30} minLength={signup ? 4 : undefined} pattern={signup ? "[a-zA-Z0-9_.\\-]{4,30}" : undefined} aria-describedby={signup ? "auth-id-help" : undefined} />
        {signup && <p id="auth-id-help" className="auth-field-help">영문·숫자·밑줄(_), 점(.), 하이픈(-) 4~30자</p>}
        <label htmlFor="auth-password">비밀번호</label>
        <div className="auth-password-field"><input id="auth-password" name="password" type={visible ? "text" : "password"} autoComplete={signup ? "new-password" : "current-password"} required minLength={signup ? 8 : undefined} aria-describedby={signup ? "auth-password-help" : undefined} />
          <button type="button" aria-label={visible ? "비밀번호 숨기기" : "비밀번호 보기"} aria-pressed={visible} onClick={() => setVisible(value => !value)}>{visible ? "숨기기" : "보기"}</button></div>
        {signup && <><p id="auth-password-help" className="auth-field-help">8자 이상, 영문·숫자 기준 최대 72자 (한글은 더 짧을 수 있어요)</p>
          <label htmlFor="auth-confirm-password">비밀번호 확인</label><input id="auth-confirm-password" name="confirmPassword" type="password" autoComplete="new-password" required />
          <label htmlFor="auth-nickname">닉네임</label><input id="auth-nickname" name="nickname" autoComplete="nickname" required maxLength={30} />
          <p className="auth-field-help">커뮤니티에서 사용할 이름이에요. 최대 30자</p>
          <label htmlFor="auth-email">이메일</label><input id="auth-email" name="email" type="email" autoComplete="email" required maxLength={254} />
        </>}
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="primary auth-submit" type="submit">{busy ? "처리 중…" : signup ? "회원가입" : "로그인"}</button>
      </fieldset>
    </form>
    <div className="auth-switch"><span>{signup ? "이미 계정이 있으신가요?" : "아직 계정이 없으신가요?"}</span>
      <button type="button" disabled={busy} onClick={() => onSwitch(signup ? "login" : "signup")}>{signup ? "로그인" : "회원가입"}</button></div>
  </section>;
}

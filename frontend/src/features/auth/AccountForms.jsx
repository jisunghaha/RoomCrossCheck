import { useState } from "react";

const tooLong = value => new TextEncoder().encode(value).length > 72;

/**
 * 비밀번호 찾기: 가입 이메일로 받은 6자리 코드를 확인하고 새 비밀번호를 정한다.
 * 가입하지 않은 이메일도 같은 안내를 보여 가입 여부를 드러내지 않는다.
 */
export function PasswordResetForm({ api, lock, onDone, onBack }) {
  const [step, setStep] = useState("email");
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  async function run(action) {
    if (lock.current) return;
    lock.current = true; setBusy(true); setError("");
    try { await action(); } catch (failure) { setError(failure.message); }
    finally { lock.current = false; setBusy(false); }
  }
  function send(event) {
    event.preventDefault();
    run(async () => {
      const value = await api.requestPasswordReset(email.trim());
      setNotice(value?.message || "인증 코드를 보냈습니다.");
      setStep("code");
    });
  }
  function confirm(event) {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));
    if (values.newPassword !== values.confirmPassword) { setError("새 비밀번호가 일치하지 않습니다."); return; }
    if (tooLong(values.newPassword)) { setError("비밀번호는 UTF-8 기준 72바이트 이하로 입력해 주세요."); return; }
    run(async () => {
      await api.confirmPasswordReset({ email: email.trim(), code: values.code.trim(), newPassword: values.newPassword });
      onDone("비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.");
    });
  }
  return <section className="auth-dialog">
    <span className="auth-kicker">FORGOT PASSWORD</span>
    <h2>비밀번호 찾기</h2>
    <p className="auth-intro">{step === "email" ? "가입할 때 쓴 이메일로 인증 코드를 보내 드려요." : "메일로 받은 6자리 코드와 새 비밀번호를 입력하세요."}</p>
    {notice && <p className="auth-success" role="status">{notice}</p>}
    {/* 단계마다 key를 달리해 이메일 입력칸의 값이 같은 자리의 인증 코드 칸으로 이어지지 않게 한다. */}
    {step === "email" ? <form key="email" onSubmit={send}>
      <fieldset disabled={busy}>
        <label htmlFor="reset-email">가입 이메일</label>
        <input id="reset-email" type="email" autoComplete="email" required maxLength={254} autoFocus value={email}
          onChange={event => setEmail(event.target.value)} />
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="primary auth-submit" type="submit">{busy ? "보내는 중…" : "인증 코드 받기"}</button>
      </fieldset>
    </form> : <form key="code" onSubmit={confirm}>
      <fieldset disabled={busy}>
        <label htmlFor="reset-code">인증 코드 (6자리)</label>
        <input id="reset-code" name="code" inputMode="numeric" autoComplete="one-time-code" required pattern="\d{6}" maxLength={6} autoFocus />
        <label htmlFor="reset-new-password">새 비밀번호</label>
        <input id="reset-new-password" name="newPassword" type="password" autoComplete="new-password" required minLength={8} />
        <label htmlFor="reset-confirm-password">새 비밀번호 확인</label>
        <input id="reset-confirm-password" name="confirmPassword" type="password" autoComplete="new-password" required />
        <p className="auth-field-help">코드는 10분 동안 유효하고 5번까지 입력할 수 있어요.</p>
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="primary auth-submit" type="submit">{busy ? "확인 중…" : "비밀번호 바꾸기"}</button>
        <button className="text-button" type="button" onClick={() => { setStep("email"); setError(""); }}>코드 다시 받기</button>
      </fieldset>
    </form>}
    <div className="auth-switch"><span>비밀번호가 생각났나요?</span>
      <button type="button" disabled={busy} onClick={onBack}>로그인</button></div>
  </section>;
}

/** 로그인한 회원의 비밀번호 변경. 현재 비밀번호를 확인한다. */
export function PasswordChangeForm({ api, lock, onDone, onBack }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event) {
    event.preventDefault();
    if (lock.current) return;
    const values = Object.fromEntries(new FormData(event.currentTarget));
    if (values.newPassword !== values.confirmPassword) { setError("새 비밀번호가 일치하지 않습니다."); return; }
    if (tooLong(values.newPassword)) { setError("비밀번호는 UTF-8 기준 72바이트 이하로 입력해 주세요."); return; }
    lock.current = true; setBusy(true); setError("");
    try {
      await api.changePassword(values.currentPassword, values.newPassword);
      onDone("비밀번호를 바꿨어요.");
    } catch (failure) { setError(failure.message); }
    finally { lock.current = false; setBusy(false); }
  }
  return <section className="auth-dialog">
    <h2>비밀번호 변경</h2>
    <form onSubmit={submit}>
      <fieldset disabled={busy}>
        <label htmlFor="change-current">현재 비밀번호</label>
        <input id="change-current" name="currentPassword" type="password" autoComplete="current-password" required autoFocus />
        <label htmlFor="change-new">새 비밀번호</label>
        <input id="change-new" name="newPassword" type="password" autoComplete="new-password" required minLength={8} />
        <label htmlFor="change-confirm">새 비밀번호 확인</label>
        <input id="change-confirm" name="confirmPassword" type="password" autoComplete="new-password" required />
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="primary auth-submit" type="submit">{busy ? "변경 중…" : "비밀번호 변경"}</button>
        <button className="text-button" type="button" onClick={onBack}>내 정보로 돌아가기</button>
      </fieldset>
    </form>
  </section>;
}

/**
 * 회원 탈퇴. 현재 비밀번호와 확인 문구를 받는다.
 * 매물·광고·방문 기록·설정은 삭제되고, 게시글·댓글·후기는 '탈퇴한 회원'으로 남는다.
 */
export function WithdrawForm({ api, lock, onDone, onBack }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [agreed, setAgreed] = useState(false);
  async function submit(event) {
    event.preventDefault();
    if (lock.current || !agreed) return;
    const values = Object.fromEntries(new FormData(event.currentTarget));
    lock.current = true; setBusy(true); setError("");
    try {
      await api.withdraw(values.password);
      onDone();
    } catch (failure) { setError(failure.message); }
    finally { lock.current = false; setBusy(false); }
  }
  return <section className="auth-dialog">
    <h2>회원 탈퇴</h2>
    <div className="notice">
      탈퇴하면 <strong>저장한 매물·광고 원본·방문 기록·체크리스트·비교 선택</strong>이 모두 삭제되며 되돌릴 수 없어요.
      작성한 게시글·댓글·거주 후기는 다른 이용자를 위해 남고, 작성자는 '탈퇴한 회원'으로 표시돼요.
    </div>
    <form onSubmit={submit}>
      <fieldset disabled={busy}>
        <label htmlFor="withdraw-password">비밀번호 확인</label>
        <input id="withdraw-password" name="password" type="password" autoComplete="current-password" required autoFocus />
        <label className="auth-agree"><input type="checkbox" checked={agreed} onChange={event => setAgreed(event.target.checked)} />
          위 내용을 확인했고 탈퇴에 동의합니다.</label>
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="primary auth-submit danger" type="submit" disabled={!agreed}>{busy ? "탈퇴 처리 중…" : "탈퇴하기"}</button>
        <button className="text-button" type="button" onClick={onBack}>취소</button>
      </fieldset>
    </form>
  </section>;
}

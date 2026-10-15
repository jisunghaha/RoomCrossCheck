import { useState } from "react";
import { Field } from "../../../shared/ui/UI.jsx";

/** 인증 서버 연결 전의 입력 검증 화면이다. 비밀번호를 저장하거나 인증 성공을 가장하지 않는다. */
export default function AuthPage({ mode, navigate }) {
  const signup = mode === "signup";
  const [values, setValues] = useState({ username: "", name: "", password: "", confirm: "" });
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const bind = (key) => ({ value: values[key], onChange: (e) => { setValues(v => ({ ...v, [key]: e.target.value })); setMessage(""); setError(""); } });
  function submit(event) {
    event.preventDefault();
    setMessage("");
    if (!/^[a-zA-Z0-9_]{4,30}$/.test(values.username)) return setError("아이디는 영문·숫자·밑줄 4~30자로 입력해 주세요.");
    if (signup && !values.name.trim()) return setError("이름을 입력해 주세요.");
    if (signup && values.password.length < 8) return setError("비밀번호는 8자 이상 입력해 주세요.");
    if (signup && values.password !== values.confirm) return setError("비밀번호 확인이 일치하지 않습니다.");
    setError("");
    setValues(v => ({ ...v, password: "", confirm: "" }));
    setMessage("입력 형식을 확인했습니다. 인증 서버 연동 전이므로 계정 생성·로그인은 처리되지 않았습니다.");
  }
  return <section className="auth-layout">
    <div className="auth-intro"><div className="eyebrow">MY ONEROOM CHECK</div><h1>{signup ? "나의 자취 준비를\n시작해 볼까요?" : "다시 만나 반가워요."}</h1><p>관심 매물과 방문 기록을 나만의 공간에서.<br />공개 글과 후기는 로그인 없이 둘러볼 수 있습니다.</p><button className="text-button" onClick={() => navigate("community")}>공개 커뮤니티 둘러보기 →</button></div>
    <div className="panel auth-panel">
      <h2>{signup ? "일반 회원가입" : "로그인"}</h2><p className="helper">화면 미리보기 · 실제 회원 인증은 연동 준비 중입니다.</p>
      <form onSubmit={submit}>
        <Field label="아이디"><input {...bind("username")} autoComplete="username" required maxLength={30} placeholder="영문·숫자·밑줄 4~30자" /></Field>
        {signup && <Field label="이름"><input {...bind("name")} autoComplete="name" required maxLength={100} /></Field>}
        <Field label="비밀번호"><input {...bind("password")} type="password" autoComplete={signup ? "new-password" : "current-password"} required maxLength={128} placeholder={signup ? "8자 이상 입력해 주세요" : "비밀번호를 입력하세요"} /></Field>
        {signup && <Field label="비밀번호 확인"><input {...bind("confirm")} type="password" autoComplete="new-password" required maxLength={128} /></Field>}
        {error && <p className="form-error" role="alert">{error}</p>}
        {message && <p className="notice" role="status">{message}</p>}
        <button type="submit" className="primary auth-submit">{signup ? "회원가입" : "로그인"}</button>
      </form>
      <button className="secondary auth-submit" disabled>Google 로그인 · 추후 제공</button>
      <p className="auth-switch">{signup ? "이미 계정이 있나요?" : "처음 방문하셨나요?"} <button className="text-button" onClick={() => navigate(signup ? "login" : "signup")}>{signup ? "로그인 화면" : "회원가입 화면"}</button></p>
    </div>
  </section>;
}

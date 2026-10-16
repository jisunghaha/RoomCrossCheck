import { useState } from "react";
import { demoAccounts, demoProfile } from "./demoAccounts.js";

export const DEMO_SESSION_KEY = "oneroom.demo-session.v1";

/**
 * local 모드에서만 시연 계정을 선택하고 같은 탭의 새로고침 동안 상태를 유지한다.
 * 저장하는 값은 가상 계정 ID 하나이며, 실제 로그인 세션이나 접근 권한을 만들지 않는다.
 */
export function useDemoAuth(enabled) {
  const [selected, setSelected] = useState(() => {
    if (!enabled) return null;
    try {
      return demoProfile(demoAccounts.find(a => a.id === window.sessionStorage.getItem(DEMO_SESSION_KEY)));
    } catch {
      return null;
    }
  });

  function login(username, password) {
    if (!enabled) return false;
    const account = demoAccounts.find(a => a.username === username.trim() && a.password === password);
    if (!account) return false;
    try { window.sessionStorage.setItem(DEMO_SESSION_KEY, account.id); }
    catch { /* 저장소를 사용할 수 없어도 현재 화면의 시연은 계속할 수 있다. */ }
    setSelected(demoProfile(account));
    return true;
  }

  function logout() {
    try { window.sessionStorage.removeItem(DEMO_SESSION_KEY); }
    catch { /* 저장소 접근 실패와 관계없이 현재 화면의 계정 상태를 해제한다. */ }
    setSelected(null);
  }

  return { user: enabled ? selected : null, login, logout };
}

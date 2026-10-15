// 누구나 확인할 수 있는 가상 계정이다. 실제 인증·서버 권한 검사에 사용하지 않는다.
export const demoAccounts = [
  { id: "demo-admin", username: "admin", password: "admin1234", name: "테스트 관리자", role: "ADMIN" },
  { id: "demo-user", username: "user", password: "user1234", name: "테스트 사용자", role: "USER" },
];

/** 화면에 필요한 공개 정보만 반환하여 비밀번호가 로그인 상태나 저장소로 넘어가지 않게 한다. */
export function demoProfile(account) {
  if (!account) return null;
  const { id, username, name, role } = account;
  return { id, username, name, role };
}

/**
 * 무작위 UUID(v4)를 만든다.
 * 브라우저는 crypto.randomUUID를 HTTPS·localhost에서만 제공하므로, HTTP 주소에서는
 * 어디서나 쓸 수 있는 crypto.getRandomValues로 같은 형식의 값을 만든다.
 * 요청 중복 방지 키와 화면용 임시 ID에 쓰며 보안 토큰으로 쓰지 않는다.
 * @return {string} 8-4-4-4-12 형식의 UUID 문자열
 */
export function randomId() {
  const cryptoApi = globalThis.crypto;
  if (typeof cryptoApi?.randomUUID === "function") return cryptoApi.randomUUID();
  const bytes = new Uint8Array(16);
  cryptoApi.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // 버전 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // RFC 4122 변형
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

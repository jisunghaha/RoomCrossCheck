import { JSDOM } from "jsdom";
import "@testing-library/jest-dom/vitest";
import { afterEach, beforeEach, vi } from "vitest";
import { cleanup } from "@testing-library/react";
// Node 25 exposes its own localStorage; use the browser implementation in tests.
const browserStorage = new JSDOM("", { url: "http://localhost" }).window
  .localStorage;
Object.defineProperty(globalThis, "localStorage", {
  configurable: true,
  value: browserStorage,
});
Object.defineProperty(window, "localStorage", {
  configurable: true,
  value: browserStorage,
});
beforeEach(() => {
  localStorage.clear();
  // 서버 쓰기 요청은 CSRF 쿠키가 있어야 한다. 쿠키 발급 요청이 각 테스트의 fetch 순서에 끼지 않도록 미리 둔다.
  document.cookie = "XSRF-TOKEN=test-csrf; path=/";
  history.replaceState(null, "", "/#rooms");
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
HTMLDialogElement.prototype.showModal = function () {
  this.setAttribute("open", "");
};
HTMLDialogElement.prototype.close = function () {
  this.removeAttribute("open");
};

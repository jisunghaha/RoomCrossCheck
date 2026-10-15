import { JSDOM } from "jsdom";
import "@testing-library/jest-dom/vitest";
import { afterEach, beforeEach, vi } from "vitest";
import { cleanup } from "@testing-library/react";
// Node의 기본 localStorage와 충돌하지 않도록 테스트에서는 브라우저 구현을 사용한다.
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
  window.sessionStorage.clear();
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

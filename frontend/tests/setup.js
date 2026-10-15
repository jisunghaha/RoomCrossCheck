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

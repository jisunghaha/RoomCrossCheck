import React from "react";
import { test, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { randomId } from "../src/shared/lib/id.js";
import AdDraftPanel from "../src/features/extraction/components/AdDraftPanel.jsx";
import ErrorBoundary from "../src/app/ErrorBoundary.jsx";

// HTTP 주소(보안 컨텍스트 아님)에서는 브라우저가 crypto.randomUUID를 제공하지 않는다.
function withoutRandomUUID(run) {
  const original = globalThis.crypto.randomUUID;
  Object.defineProperty(globalThis.crypto, "randomUUID", { value: undefined, configurable: true });
  try { return run(); } finally {
    Object.defineProperty(globalThis.crypto, "randomUUID", { value: original, configurable: true });
  }
}

test("randomId makes a v4 UUID even without crypto.randomUUID", () => {
  withoutRandomUUID(() => {
    const id = randomId();
    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    expect(randomId()).not.toBe(id);
  });
});

test("ad import panel renders on an HTTP page without crypto.randomUUID", async () => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: true, status: 200, json: async () => [] });
  await withoutRandomUUID(async () => {
    render(<AdDraftPanel />);
    expect(screen.getByLabelText("광고 이미지 (JPG·PNG, 최대 10MB)")).toBeInTheDocument();
  });
});

test("a rendering error shows a recovery message instead of a blank page", () => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  const Broken = () => { throw new Error("broken"); };
  render(<ErrorBoundary><Broken /></ErrorBoundary>);
  expect(screen.getByRole("alert")).toHaveTextContent("문제가 생겼어요");
  expect(screen.getByRole("button", { name: "새로고침" })).toBeInTheDocument();
});

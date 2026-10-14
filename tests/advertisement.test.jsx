import React from "react";
import { test, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdDraftPanel from "../src/components/AdDraftPanel.jsx";

test("ad draft keeps its retry key and can be confirmed after saving", async () => {
  const draft = { id: "draft-1", status: "DRAFT", originalText: "월세 42", addressHint: "주소", imageName: null };
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce({ ok: true, json: async () => [] })
    .mockResolvedValueOnce({ ok: true, json: async () => draft })
    .mockResolvedValueOnce({ ok: true, json: async () => [draft] })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ ...draft, status: "EXTRACTION_PENDING" }) })
    .mockResolvedValueOnce({ ok: true, json: async () => [{ ...draft, status: "EXTRACTION_PENDING" }] });
  const user = userEvent.setup();
  render(<AdDraftPanel />);
  await user.type(screen.getByLabelText("광고 글"), "월세 42");
  await user.click(screen.getByRole("button", { name: "광고 원본 임시 저장" }));
  expect(await screen.findByText(/원본을 임시 저장했어요/)).toBeInTheDocument();
  expect(fetchMock.mock.calls[1][0]).toBe("/api/advertisements");
  expect(fetchMock.mock.calls[1][1].headers["Idempotency-Key"]).toBeTruthy();
  expect(fetchMock.mock.calls[1][1].body.get("text")).toBe("월세 42");
  await user.click(screen.getByRole("button", { name: "원본 확정" }));
  expect(await screen.findByText(/원본을 확정했어요/)).toBeInTheDocument();
  expect(fetchMock.mock.calls[3][0]).toBe("/api/advertisements/draft-1/confirm");
});

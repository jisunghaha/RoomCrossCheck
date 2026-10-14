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

test("shows Gemini results for user correction before saving reviewed values", async () => {
  const ready = {
    id: "ad-2", status: "EXTRACTION_READY", originalText: "월세 42만원", imageName: null,
    extraction: { rawText: "월세 42만원", address: null, depositWon: null,
      monthlyRentWon: 420000, maintenanceFeeWon: null, areaSqm: null, floor: null },
  };
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce({ ok: true, json: async () => [ready] })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ ...ready, status: "REVIEWED" }) })
    .mockResolvedValueOnce({ ok: true, json: async () => [{ ...ready, status: "REVIEWED" }] });
  const user = userEvent.setup();
  render(<AdDraftPanel />);
  const rent = await screen.findByLabelText("월세 (원)");
  await user.clear(rent);
  await user.type(rent, "430000");
  await user.click(screen.getByRole("button", { name: "확인한 값 저장" }));
  expect(await screen.findByText("확인한 추출 값을 저장했어요.")).toBeInTheDocument();
  expect(fetchMock.mock.calls[1][0]).toBe("/api/advertisements/ad-2/extraction");
  expect(JSON.parse(fetchMock.mock.calls[1][1].body).monthlyRentWon).toBe(430000);
});

test("missing Gemini configuration is shown without pretending extraction succeeded", async () => {
  const pending = { id: "ad-3", status: "EXTRACTION_PENDING", originalText: "월세 42만원" };
  vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce({ ok: true, json: async () => [pending] })
    .mockResolvedValueOnce({ ok: false, status: 503 })
    .mockResolvedValueOnce({ ok: true, json: async () => [pending] });
  const user = userEvent.setup();
  render(<AdDraftPanel />);
  await user.click(await screen.findByRole("button", { name: "Gemini 추출 시작" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Gemini 추출 설정이 없습니다");
  expect(screen.getByText("추출 대기")).toBeInTheDocument();
});

import React from "react";
import { test, expect, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../src/app/App.jsx";
import { RoomProvider } from "../src/app/state/RoomContext.jsx";
import { createListingRepository } from "../src/app/data/repository.js";
import { reviewedAdvertisementValues } from "../src/features/extraction/model/reviewedAdvertisement.js";

const draft = {
  id: "reviewed-ad", status: "REVIEWED", addressHint: "사용하면 안 되는 힌트",
  originalText: "가상 광고 원문", reviewedAt: "2026-10-14T00:00:00Z",
  extraction: { monthlyRentWon: 990000, address: "AI 주소" },
  reviewed: { rawText: "확인한 OCR 글", address: null, depositWon: null,
    monthlyRentWon: 123456, maintenanceFeeWon: 0, areaSqm: 20, floor: -20 },
};

async function openAdvertisements(user = userEvent) {
  await user.click(await screen.findByRole("button", { name: "새 매물 등록" }));
  await user.click(screen.getByRole("button", { name: "광고 글·사진에서 가져오기" }));
}

test("only persisted reviewed values can prefill; hints and official data are excluded", () => {
  expect(reviewedAdvertisementValues({ ...draft, status: "EXTRACTION_READY" })).toBeNull();
  expect(reviewedAdvertisementValues({ ...draft, reviewed: null })).toBeNull();
  expect(reviewedAdvertisementValues(draft)).toEqual({ name: "", address: "", deposit: null,
    rent: 123456, fee: 0, area: 20, areaType: "미상", floor: -20, original: "가상 광고 원문" });
  expect(reviewedAdvertisementValues({ ...draft, originalText: "", reviewed: { ...draft.reviewed, rawText: "이미지 OCR" } }).original).toBe("이미지 OCR");
});

test("reviewed ad opens editable listing form, preserves failed input, and saves only on user submit", async () => {
  const requests = [];
  let failSave = true;
  vi.spyOn(globalThis, "fetch").mockImplementation(async (url, options = {}) => {
    requests.push([url, options]);
    if (url === "/api/advertisements") return { ok: true, json: async () => [draft] };
    if (url === "/api/listings" && options.method === "POST") {
      if (failSave) return { ok: false, status: 500 };
      return { ok: true, status: 201, json: async () => ({ ...JSON.parse(options.body), id: "new-room", building: "new-building", createdAt: "2026-10-14" }) };
    }
    return { ok: true, status: 200, json: async () => [] };
  });
  const user = userEvent.setup();
  render(<RoomProvider repository={createListingRepository()}><App /></RoomProvider>);
  await openAdvertisements(user);
  // An unsaved edit in the review form must not replace persisted reviewed data.
  const reviewRent = await screen.findByLabelText("월세 (원)");
  await user.clear(reviewRent);
  await user.type(reviewRent, "999999");
  await user.click(await screen.findByRole("button", { name: "확인한 값으로 매물 등록" }));
  const dialog = within(screen.getByRole("main"));
  expect(dialog.getByLabelText("월세 (만원)")).toHaveValue(12.3456);
  expect(dialog.getByLabelText("고정 관리비 (만원)")).toHaveValue(0);
  expect(dialog.getByLabelText("보증금 (만원)")).toHaveValue(null);
  expect(dialog.getByLabelText("주소 / 건물명 *")).toHaveValue("");
  expect(dialog.getByLabelText("층수")).toHaveValue(-20);
  expect(dialog.getByText(/광고 값은 건축물대장 공식 정보가 아닙니다/)).toBeInTheDocument();
  expect(requests.filter(([, o]) => o.method === "POST")).toHaveLength(0);
  await user.type(dialog.getByLabelText("매물 이름 *"), "가상 확인 매물");
  await user.type(dialog.getByLabelText("주소 / 건물명 *"), "가상 주소");
  await user.clear(dialog.getByLabelText("면적 (㎡)"));
  await user.type(dialog.getByLabelText("면적 (㎡)"), "21");
  await user.click(dialog.getByRole("button", { name: "최종 확인으로 이동" }));
  expect(requests.filter(([, o]) => o.method === "POST")).toHaveLength(0);
  await user.click(dialog.getByRole("button", { name: "확정 저장" }));
  expect(await screen.findByText(/매물 서버 요청에 실패/)).toBeInTheDocument();
  await user.click(dialog.getByRole("button", { name: "내용 수정" }));
  expect(dialog.getByLabelText("면적 (㎡)")).toHaveValue(21);
  failSave = false;
  await user.click(dialog.getByRole("button", { name: "최종 확인으로 이동" }));
  await user.click(dialog.getByRole("button", { name: "확정 저장" }));
  expect(await screen.findByRole("button", { name: "가상 확인 매물" })).toBeInTheDocument();
  const payload = JSON.parse(requests.filter(([, o]) => o.method === "POST").at(-1)[1].body);
  expect(payload).toMatchObject({ deposit: null, rent: 123456, fee: 0, floor: -20, area: 21, areaType: "미상", original: "가상 광고 원문" });
  expect(payload).not.toHaveProperty("official");
  expect(payload).not.toHaveProperty("userId");
  expect(requests.some(([url]) => url.endsWith("/extract"))).toBe(false);
});

test("cancel never creates a listing and long reviewed text is retained", async () => {
  const longDraft = { ...draft, originalText: "가".repeat(10000) };
  const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async url => ({
    ok: true, status: 200, json: async () => url === "/api/advertisements" ? [longDraft] : [],
  }));
  render(<RoomProvider repository={createListingRepository()}><App /></RoomProvider>);
  await openAdvertisements();
  await userEvent.click(await screen.findByRole("button", { name: "확인한 값으로 매물 등록" }));
  const dialog = within(screen.getByRole("main"));
  expect(dialog.getByLabelText("광고 원문").value).toHaveLength(10000);
  await userEvent.click(dialog.getByRole("button", { name: "등록 취소 · 목록으로" }));
  expect(screen.getByRole("button", { name: "새 매물 등록" })).toBeInTheDocument();
  expect(fetchMock.mock.calls.some(([, o]) => o?.method === "POST")).toBe(false);
});

test("an AI result awaiting review cannot open listing registration", async () => {
  vi.spyOn(globalThis, "fetch").mockImplementation(async url => ({
    ok: true, status: 200, json: async () => url === "/api/advertisements"
      ? [{ ...draft, status: "EXTRACTION_READY", reviewed: null }] : [],
  }));
  render(<RoomProvider repository={createListingRepository()}><App /></RoomProvider>);
  await openAdvertisements();
  await screen.findByText("추출 결과 확인 필요");
  expect(screen.queryByRole("button", { name: "확인한 값으로 매물 등록" })).not.toBeInTheDocument();
});

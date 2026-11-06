import React from "react";
import { test, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { parseOptions, optionsSummary } from "../src/shared/lib/options.js";
import { createListingRepository } from "../src/app/data/repository.js";
import { RoomProvider } from "../src/app/state/RoomContext.jsx";
import App from "../src/app/App.jsx";
import AdDraftPanel from "../src/features/extraction/components/AdDraftPanel.jsx";

test("option input distinguishes unknown and none, retains full-option wording and rejects oversized input", () => {
  expect(parseOptions("")).toBeNull();
  expect(parseOptions("", true)).toEqual([]);
  expect(parseOptions(" 풀옵션 \n냉장고\n냉장고")).toEqual(["풀옵션", "냉장고"]);
  expect(optionsSummary(null)).toBe("정보 없음");
  expect(optionsSummary([])).toBe("옵션 없음으로 확인");
  expect(() => parseOptions("가".repeat(101))).toThrow("100자");
  expect(() => parseOptions(Array.from({ length: 31 }, (_, i) => "옵션" + i).join("\n"))).toThrow("30개");
});

test("persisted reviewed options are used and final edits survive preview and saving", async () => {
  const draft = { id: "options-ad", status: "REVIEWED", originalText: "냉장고·세탁기 포함",
    reviewed: { rawText: "광고", address: "부산 가상 주소", options: ["냉장고", "세탁기"] } };
  const requests = [];
  vi.spyOn(globalThis, "fetch").mockImplementation(async (url, opts = {}) => {
    requests.push([url, opts]);
    if (url === "/api/advertisements") return { ok: true, json: async () => [draft] };
    if (opts.method === "POST") return { ok: true, status: 201, json: async () => ({ ...JSON.parse(opts.body), id: "options-room", building: "building" }) };
    return { ok: true, json: async () => [] };
  });
  const user = userEvent.setup();
  render(<RoomProvider repository={createListingRepository()}><App /></RoomProvider>);
  await user.click(await screen.findByRole("button", { name: "새 매물 등록" }));
  await user.click(screen.getByRole("button", { name: "광고 글·사진에서 가져오기" }));
  await user.clear(await screen.findByLabelText("옵션 (한 줄에 하나)"));
  await user.type(screen.getByLabelText("옵션 (한 줄에 하나)"), "저장하지 않은 TV");
  expect(screen.getByRole("button", { name: "확인한 값으로 매물 등록" })).toBeDisabled();
  await user.clear(screen.getByLabelText("옵션 (한 줄에 하나)"));
  await user.type(screen.getByLabelText("옵션 (한 줄에 하나)"), "냉장고\n세탁기");
  await user.click(screen.getByRole("button", { name: "확인한 값으로 매물 등록" }));
  expect(screen.getByLabelText("옵션 (한 줄에 하나)")).toHaveValue("냉장고\n세탁기");
  await user.clear(screen.getByLabelText("옵션 (한 줄에 하나)"));
  await user.type(screen.getByLabelText("옵션 (한 줄에 하나)"), "에어컨");
  await user.type(screen.getByLabelText("매물 이름 *"), "옵션 확인 매물");
  await user.click(screen.getByRole("button", { name: "최종 확인으로 이동" }));
  expect(screen.getByText("에어컨", { exact: true })).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "내용 수정" }));
  expect(screen.getByLabelText("옵션 (한 줄에 하나)")).toHaveValue("에어컨");
  await user.click(screen.getByRole("button", { name: "최종 확인으로 이동" }));
  await user.click(screen.getByRole("button", { name: "확정 저장" }));
  await screen.findByRole("button", { name: "옵션 확인 매물", exact: true });
  const payload = JSON.parse(requests.find(([url, opts]) => url === "/api/listings" && opts.method === "POST")[1].body);
  expect(payload.options).toEqual(["에어컨"]);
  expect(payload.advertisementId).toBe(draft.id);
});

test("listing adapter preserves omitted options and transmits explicit unknown and none", async () => {
  const fetchImpl = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: "room", building: "building" }) });
  const repo = createListingRepository({ fetchImpl });
  await repo.saveRoom({ name: "기존 요청" }, "room");
  expect(JSON.parse(fetchImpl.mock.calls[0][1].body)).not.toHaveProperty("options");
  await repo.saveRoom({ name: "미확인", options: null }, "room");
  expect(JSON.parse(fetchImpl.mock.calls[1][1].body).options).toBeNull();
  await repo.saveRoom({ name: "없음", options: [] }, "room");
  expect(JSON.parse(fetchImpl.mock.calls[2][1].body).options).toEqual([]);
});

test("invalid review options do not reach the server and explicit none disables the text input", async () => {
  const ready = { id: "ready", status: "EXTRACTION_READY", originalText: "옵션 광고", extraction: { options: ["냉장고"] } };
  const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: true, json: async () => [ready] });
  render(<AdDraftPanel />);
  const user = userEvent.setup();
  const input = await screen.findByLabelText("옵션 (한 줄에 하나)");
  await user.clear(input);
  await user.type(input, "가".repeat(101));
  await user.click(screen.getByRole("button", { name: "확인한 값 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("100자");
  expect(fetchMock.mock.calls.some(([, opts]) => opts?.method === "PUT")).toBe(false);
  await user.click(screen.getByLabelText("옵션 없음으로 확인"));
  expect(input).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "확인한 값 저장" }));
  await screen.findByText("확인한 추출 값을 저장했어요.");
  expect(JSON.parse(fetchMock.mock.calls.find(([, opts]) => opts?.method === "PUT")[1].body).options).toEqual([]);
});

test("malformed server option data fails without being displayed or substituted with demo values", async () => {
  for (const options of ["냉장고", [null], [123], [""], ["가".repeat(101)]]) {
    const repo = createListingRepository({ fetchImpl: async () => ({ ok: true, json: async () => [{ id: "bad", building: "building", options }] }) });
    await expect(repo.load()).rejects.toThrow("서버 응답 형식");
  }
});

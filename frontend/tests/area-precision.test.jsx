import React from "react";
import { test, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RoomProvider } from "../src/app/state/RoomContext.jsx";
import { createListingRepository } from "../src/app/data/repository.js";
import App from "../src/app/App.jsx";

async function openReviewedArea(area) {
  const draft = { id: "area-source", status: "REVIEWED", originalText: "면적 검증 원본",
    reviewed: { address: "부산 가상 주소", areaSqm: area, areaType: "전용", maintenanceFeeWon: 0, options: ["세탁기"] } };
  const requests = [];
  vi.spyOn(globalThis, "fetch").mockImplementation(async (url, opts = {}) => {
    requests.push([url, opts]);
    if (url === "/api/advertisements") return { ok: true, json: async () => [draft] };
    if (opts.method === "POST") return { ok: true, status: 201, json: async () => ({ ...JSON.parse(opts.body), id: "saved-area", building: "building" }) };
    return { ok: true, json: async () => [] };
  });
  const user = userEvent.setup();
  render(<RoomProvider repository={createListingRepository()}><App /></RoomProvider>);
  await user.click(await screen.findByRole("button", { name: "새 매물 등록" }));
  await user.click(screen.getByRole("button", { name: "광고 글·사진에서 가져오기" }));
  await user.click(await screen.findByRole("button", { name: "확인한 값으로 매물 등록" }));
  await user.type(screen.getByLabelText("매물 이름 *"), "소수 면적 매물");
  return { user, requests };
}

test.each([19.212, 14.876, 23.7501])("reviewed area %s survives preview and save without manual rounding", async area => {
  const { user, requests } = await openReviewedArea(area);
  const input = screen.getByLabelText("면적 (㎡)");
  expect(input).toHaveValue(area);
  expect(input).toHaveAttribute("step", "0.0001");
  expect(input.validity.stepMismatch).toBe(false);
  await user.click(screen.getByRole("button", { name: "최종 확인으로 이동" }));
  await user.click(screen.getByRole("button", { name: "내용 수정" }));
  expect(screen.getByLabelText("면적 (㎡)")).toHaveValue(area);
  await user.click(screen.getByRole("button", { name: "최종 확인으로 이동" }));
  await user.click(screen.getByRole("button", { name: "확정 저장" }));
  await screen.findByRole("button", { name: "소수 면적 매물", exact: true });
  const payload = JSON.parse(requests.find(([url, opts]) => url === "/api/listings" && opts.method === "POST")[1].body);
  expect(payload).toMatchObject({ area, fee: 0, deposit: null, options: ["세탁기"], advertisementId: "area-source" });
});

test("unrepresentable fifth decimal requires explicit correction and never posts a rounded value", async () => {
  const { user, requests } = await openReviewedArea(19.21201);
  expect(screen.getByLabelText("면적 (㎡)").validity.stepMismatch).toBe(true);
  await user.click(screen.getByRole("button", { name: "최종 확인으로 이동" }));
  expect(screen.getByLabelText("면적 (㎡)")).toHaveValue(19.21201);
  expect(requests.some(([, opts]) => opts.method === "POST")).toBe(false);
  expect(screen.getByText(/소수점 넷째 자리까지 저장/)).toBeInTheDocument();
});

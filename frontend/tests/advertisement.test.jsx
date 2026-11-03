import React from "react";
import { test, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdDraftPanel from "../src/features/extraction/components/AdDraftPanel.jsx";

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
      monthlyRentWon: 420000, maintenanceFeeWon: null, areaSqm: null, floor: null, areaType: "전용" },
  };
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce({ ok: true, json: async () => [ready] })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ ...ready, status: "REVIEWED" }) })
    .mockResolvedValueOnce({ ok: true, json: async () => [{ ...ready, status: "REVIEWED" }] });
  const user = userEvent.setup();
  render(<AdDraftPanel />);
  const rent = await screen.findByLabelText("월세 (원)");
  expect(screen.getByLabelText("면적 종류")).toHaveValue("전용");
  await user.selectOptions(screen.getByLabelText("면적 종류"), "공급");
  await user.clear(rent);
  await user.type(rent, "430000");
  await user.click(screen.getByRole("button", { name: "확인한 값 저장" }));
  expect(await screen.findByText("확인한 추출 값을 저장했어요.")).toBeInTheDocument();
  expect(fetchMock.mock.calls[1][0]).toBe("/api/advertisements/ad-2/extraction");
  expect(JSON.parse(fetchMock.mock.calls[1][1].body).monthlyRentWon).toBe(430000);
  expect(JSON.parse(fetchMock.mock.calls[1][1].body).areaType).toBe("공급");
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

test.each([
  [400, { message: "JPG·PNG 파일의 확장자, 형식, 내용이 일치해야 합니다." }, "확장자, 형식, 내용이 일치"],
  [413, null, "10MB 이하"],
  [500, { message: "/private/storage database password should never appear" }, "잠시 후 다시 시도"],
])("upload %s explains the failure and preserves input and retry key", async (status, body, expected) => {
  const draft = { id: "retried", status: "DRAFT", originalText: "원본 광고" };
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce({ ok: true, json: async () => [] })
    .mockResolvedValueOnce({ ok: false, status, json: async () => { if (!body) throw new Error("HTML response"); return body; } })
    .mockResolvedValueOnce({ ok: true, json: async () => draft })
    .mockResolvedValueOnce({ ok: true, json: async () => [draft] });
  const user = userEvent.setup();
  render(<AdDraftPanel />);
  await user.type(screen.getByLabelText("광고 글"), "원본 광고");
  await user.click(screen.getByRole("button", { name: "광고 원본 임시 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(expected);
  expect(screen.queryByText(/private\/storage/)).not.toBeInTheDocument();
  expect(screen.getByLabelText("광고 글")).toHaveValue("원본 광고");
  await user.click(screen.getByRole("button", { name: "광고 원본 임시 저장" }));
  expect(await screen.findByText(/원본을 임시 저장했어요/)).toBeInTheDocument();
  expect(fetchMock.mock.calls[1][1].headers["Idempotency-Key"])
    .toBe(fetchMock.mock.calls[2][1].headers["Idempotency-Key"]);
});

test("network failure preserves selected image and allows retry", async () => {
  const draft = { id: "image-retry", status: "DRAFT", imageName: "room.png" };
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce({ ok: true, json: async () => [] })
    .mockRejectedValueOnce(new TypeError("Failed to fetch"))
    .mockResolvedValueOnce({ ok: true, json: async () => draft })
    .mockResolvedValueOnce({ ok: true, json: async () => [draft] });
  const user = userEvent.setup();
  render(<AdDraftPanel />);
  const input = screen.getByLabelText("광고 이미지 (JPG·PNG, 최대 10MB)");
  const image = new File(["test"], "room.png", { type: "image/png" });
  await user.upload(input, image);
  await user.click(screen.getByRole("button", { name: "광고 원본 임시 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("서버에 연결하지 못했습니다");
  expect(input.files[0]).toBe(image);
  await user.click(screen.getByRole("button", { name: "광고 원본 임시 저장" }));
  expect(await screen.findByText(/원본을 임시 저장했어요/)).toBeInTheDocument();
  expect(fetchMock.mock.calls[1][1].headers["Idempotency-Key"])
    .toBe(fetchMock.mock.calls[2][1].headers["Idempotency-Key"]);
});

test("oversized image is explained before transmitting it", async () => {
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce({ ok: true, json: async () => [] });
  const user = userEvent.setup();
  render(<AdDraftPanel />);
  const image = new File(["test"], "large.png", { type: "image/png" });
  Object.defineProperty(image, "size", { value: 10 * 1024 * 1024 + 1 });
  const input = screen.getByLabelText("광고 이미지 (JPG·PNG, 최대 10MB)");
  await user.upload(input, image);
  await user.click(screen.getByRole("button", { name: "광고 원본 임시 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("10MB 이하");
  expect(input.files[0]).toBe(image);
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

import React from "react";
import { test, expect, vi } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AdDraftPanel from "../src/features/extraction/components/AdDraftPanel.jsx";
import { useHashRoute } from "../src/app/routing/useHashRoute.js";

const draft = { id: "safe-ad", status: "REVIEWED", reviewVersion: 2,
  extraction: { rawText: "월세 35만원", monthlyRentWon: 350000 },
  reviewed: { rawText: "월세 35만원", monthlyRentWon: 360000 }, reviewedAt: "2026-11-05T01:00:00Z" };

test("refreshing the advertisement list for another upload preserves unsaved edits and their original version", async () => {
  const updatedElsewhere = { ...draft, reviewVersion: 3, reviewedAt: "2026-11-06T01:00:00Z",
    reviewed: { ...draft.reviewed, monthlyRentWon: 999999 } };
  const uploaded = { id: "new-upload", status: "DRAFT", originalText: "추가 광고" };
  const request = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce({ ok: true, json: async () => [draft] })
    .mockResolvedValueOnce({ ok: true, json: async () => uploaded })
    .mockResolvedValueOnce({ ok: true, json: async () => [updatedElsewhere, uploaded] })
    .mockResolvedValueOnce({ ok: false, status: 409 });
  const user = userEvent.setup(); render(<AdDraftPanel />);
  const rent = await screen.findByLabelText("월세 (원)");
  await user.clear(rent); await user.type(rent, "380000");
  await user.type(screen.getByLabelText("광고 글"), "추가 광고");
  await user.click(screen.getByRole("button", { name: "광고 원본 임시 저장" }));
  await screen.findByText(/원본을 임시 저장했어요/);
  expect(screen.getByLabelText("월세 (원)")).toHaveValue(380000);
  await user.click(screen.getByRole("button", { name: "확인한 값 저장" }));
  await screen.findByRole("alert");
  expect(request.mock.calls[3][1].headers["X-Review-Version"]).toBe("2");
});

test("restoring option fields to their original state clears the unsaved warning", async () => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: true, json: async () => [draft] });
  const user = userEvent.setup(); render(<AdDraftPanel onRegister={vi.fn()} />);
  const none = await screen.findByRole("checkbox", { name: "옵션 없음으로 확인" });
  await user.click(none);
  expect(screen.getByRole("button", { name: "확인한 값으로 매물 등록" })).toBeDisabled();
  await user.click(none);
  expect(screen.getByRole("button", { name: "확인한 값으로 매물 등록" })).toBeEnabled();
});

test("conflicting save keeps edits, explicitly reloads the latest version, and saves with that version", async () => {
  const latest = { ...draft, reviewVersion: 3, reviewed: { ...draft.reviewed, monthlyRentWon: 370000 } };
  const request = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce({ ok: true, json: async () => [draft] })
    .mockResolvedValueOnce({ ok: false, status: 409 })
    .mockResolvedValueOnce({ ok: true, json: async () => latest })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ ...latest, reviewVersion: 4 }) });
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  const register = vi.fn();
  const user = userEvent.setup();
  render(<AdDraftPanel onRegister={register} />);
  const rent = await screen.findByLabelText("월세 (원)");
  await user.clear(rent); await user.type(rent, "380000");
  expect(screen.getByRole("button", { name: "확인한 값으로 매물 등록" })).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "확인한 값 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("입력은 유지됩니다");
  expect(rent).toHaveValue(380000);
  expect(request.mock.calls[1][1].headers["X-Review-Version"]).toBe("2");
  await user.click(screen.getByRole("button", { name: "최신값 불러오기" }));
  expect(request).toHaveBeenCalledTimes(2);
  confirm.mockReturnValue(true);
  await user.click(screen.getByRole("button", { name: "최신값 불러오기" }));
  await waitFor(() => expect(screen.getByLabelText("월세 (원)")).toHaveValue(370000));
  await user.click(screen.getByRole("button", { name: "확인한 값 저장" }));
  await screen.findByText("확인한 추출 값을 저장했어요.");
  expect(request.mock.calls[3][1].headers["X-Review-Version"]).toBe("3");
  expect(screen.getByRole("button", { name: "확인한 값으로 매물 등록" })).toBeEnabled();
  expect(register).not.toHaveBeenCalled();
});

test("dirty review protects in-app navigation, hash navigation and browser unload", async () => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue({ ok: true, json: async () => [draft] });
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  history.replaceState(null, "", "#register");
  function Harness() {
    const [page, navigate] = useHashRoute();
    return <><a href="#rooms">목록 링크</a><button onClick={() => navigate("rooms")}>목록 버튼</button>
      {page === "register" ? <AdDraftPanel /> : <p>목록 화면</p>}</>;
  }
  const user = userEvent.setup(); render(<Harness />);
  await user.clear(await screen.findByLabelText("월세 (원)"));
  const unload = new Event("beforeunload", { cancelable: true });
  expect(window.dispatchEvent(unload)).toBe(false);
  await user.click(screen.getByText("목록 링크"));
  expect(location.hash).toBe("#register");
  await user.click(screen.getByText("목록 버튼"));
  expect(screen.queryByText("목록 화면")).not.toBeInTheDocument();
  history.replaceState(null, "", "#rooms");
  fireEvent(window, new HashChangeEvent("hashchange"));
  expect(location.hash).toBe("#register");
  confirm.mockReturnValue(true);
  await user.click(screen.getByText("목록 링크"));
  expect(screen.getByText("목록 화면")).toBeInTheDocument();
  expect(window.dispatchEvent(new Event("beforeunload", { cancelable: true }))).toBe(true);
});

test("history is loaded on demand and shows zero and unknown without executing stored text", async () => {
  const request = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce({ ok: true, json: async () => [draft] })
    .mockResolvedValueOnce({ ok: true, json: async () => [{ version: 2, reviewedAt: draft.reviewedAt,
      reviewed: { maintenanceFeeWon: 0, rawText: "<script>private()</script>", options: [] } }] });
  const user = userEvent.setup(); render(<AdDraftPanel />);
  await screen.findByLabelText("월세 (원)");
  expect(request).toHaveBeenCalledTimes(1);
  await user.click(screen.getByRole("button", { name: "수정 이력 보기" }));
  expect(await screen.findByText(/저장 버전 2/)).toBeInTheDocument();
  expect(request.mock.calls[1][0]).toBe("/api/advertisements/safe-ad/reviews");
  expect(screen.getByText("<script>private()</script>")).toBeInTheDocument();
  expect(document.querySelector("script")).toBeNull();
  expect(screen.getByText("0", { exact: true })).toBeInTheDocument();
});

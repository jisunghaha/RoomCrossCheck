import React from "react";
import { test, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createApiRepository, createLocalRepository } from "../src/app/data/repository.js";
import { RoomProvider } from "../src/app/state/RoomContext.jsx";
import App from "../src/app/App.jsx";

test("API encodes IDs, sends cookies and uses server-generated IDs", async () => {
  const saved = { id: "mysql-42", building: "b42", tags: [], name: "saved" };
  const fetchImpl = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => saved });
  const repo = createApiRepository({ baseUrl: "/api/", fetchImpl });
  expect(await repo.saveRoom({ name: "saved", fee: null, rent: 0 })).toEqual(saved);
  expect(fetchImpl.mock.calls[0][0]).toBe("/api/rooms");
  expect(fetchImpl.mock.calls[0][1]).toMatchObject({ method: "POST", credentials: "include", body: '{"name":"saved","fee":null,"rent":0}' });
  fetchImpl.mockResolvedValue({ ok: true, status: 204 });
  await repo.deleteRoom("a/b");
  expect(fetchImpl.mock.calls[1][0]).toBe("/api/rooms/a%2Fb");
});
test("API failures never fall back to browser demo data", async () => {
  const repo = createApiRepository({ fetchImpl: async () => ({ ok: false, status: 401 }) });
  expect(repo.initialState()).toEqual({ rooms: [], reviews: [], visits: {} });
  await expect(repo.load()).rejects.toThrow("로그인");
});
test("local storage failure leaves prior state intact", async () => {
  const repo = createLocalRepository({ getItem: () => null, setItem: () => { throw new Error("quota"); } });
  const before = repo.initialState();
  await expect(repo.deleteRoom(before.rooms[0].id)).rejects.toThrow("저장하지 못했어요");
  expect(await repo.load()).toEqual(before);
});
test("failed async save keeps form input and does not announce success", async () => {
  const user = userEvent.setup();
  const repo = createLocalRepository();
  repo.saveRoom = vi.fn().mockRejectedValue(new Error("서버 저장 실패"));
  render(<RoomProvider repository={repo}><App /></RoomProvider>);
  await user.click(screen.getByRole("button", { name: "새 매물 등록" }));
  await user.type(screen.getByLabelText("주소 / 건물명 *"), "주소");
  await user.click(screen.getByRole("button", { name: "값 확인으로 이동" }));
  await user.type(screen.getByLabelText("매물 이름 *"), "보존할 입력");
  await user.click(screen.getByRole("button", { name: "최종 확인으로 이동" }));
  await user.click(screen.getByRole("button", { name: "확정 저장" }));
  expect(await screen.findByText("서버 저장 실패")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "내용 수정" }));
  expect(screen.getByLabelText("매물 이름 *")).toHaveValue("보존할 입력");
  expect(screen.getByRole("heading", { name: "매물 정보를 확인하고 수정하세요" })).toBeInTheDocument();
});
test("API initial load can recover by retry without displaying demo", async () => {
  const repo = { mode: "api", initialState: () => ({ rooms: [], reviews: [], visits: {} }),
    load: vi.fn().mockRejectedValueOnce(new Error("연결 실패")).mockResolvedValue({ rooms: [], reviews: [], visits: {} }) };
  render(<RoomProvider repository={repo}><App /></RoomProvider>);
  expect(await screen.findByText("연결 실패")).toHaveAttribute("role", "alert");
  await userEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
  expect(await screen.findByRole("button", { name: "새 매물 등록" })).toBeInTheDocument();
  expect(screen.queryByText("햇살이 머무는 첫 자취방")).not.toBeInTheDocument();
});

test("listing bridge loads member data from the server and keeps only demo reviews in the browser", async () => {
  const { createListingRepository } = await import("../src/app/data/repository.js");
  const saved = { id: "db-1", building: "addr:주소", name: "DB 방", address: "주소", createdAt: "2026-10-14" };
  const serverReview = { id: "rv-1", building: "addr:주소", scores: { 방음: 4 }, mine: false };
  const stats = { "addr:주소": { count: 1, items: [] } };
  const fetchImpl = vi.fn(async (url, options = {}) => {
    const json = url === "/api/listings" && options.method === "POST" ? saved
      : url === "/api/listings" ? [saved]
      : url === "/api/visits" ? { "db-1": { checks: { 0: true }, note: "", visitedOn: "2026-11-01" } }
      : url === "/api/visit-checklist" ? { items: [{ id: "c-1", label: "보일러", tip: "" }] }
      : url === "/api/comparison" ? { listingIds: ["db-1"] }
      : url.startsWith("/api/reviews?") ? { reviews: [serverReview], stats } : null;
    return { ok: true, status: 200, json: async () => json };
  });
  localStorage.setItem("oneroom.frontend.v1", JSON.stringify({ rooms: [], visits: {}, reviews: [
    { id: "review-demo:addr:주소:0", building: "addr:주소", scores: {}, demo: true },
    { id: "local-only", building: "addr:주소", scores: {}, demo: false },
  ] }));
  const repo = createListingRepository({ fetchImpl });
  const loaded = await repo.load();
  expect(loaded.rooms[0]).toMatchObject({ id: "db-1", source: "server", official: null });
  expect(loaded.reviews.map(r => r.id)).toEqual(["review-demo:addr:주소:0", "rv-1"]);
  expect(loaded).toMatchObject({ reviewStats: stats, comparison: ["db-1"], checklist: [{ id: "c-1" }] });
  expect(fetchImpl.mock.calls.find(([url]) => url.startsWith("/api/reviews?"))[0]).toBe("/api/reviews?building=addr%3A%EC%A3%BC%EC%86%8C");
  await repo.saveRoom({ name: "DB 방", address: "주소", fee: null });
  const [, post] = fetchImpl.mock.calls.find(([url, o]) => url === "/api/listings" && o.method === "POST");
  expect(post.headers["X-XSRF-TOKEN"]).toBe("test-csrf");
  expect(JSON.parse(post.body)).toMatchObject({ name: "DB 방", address: "주소", fee: null });
});
test("listing bridge asks for login instead of showing demo data when the session is missing", async () => {
  const { createListingRepository } = await import("../src/app/data/repository.js");
  const fetchImpl = vi.fn(async () => ({ ok: false, status: 401, json: async () => ({ message: "로그인이 필요합니다." }) }));
  render(<RoomProvider repository={createListingRepository({ fetchImpl })}><App /></RoomProvider>);
  expect(await screen.findByRole("heading", { name: "로그인하면 내 매물을 볼 수 있어요" })).toBeInTheDocument();
  expect(screen.queryByText("햇살이 머무는 첫 자취방")).not.toBeInTheDocument();
});

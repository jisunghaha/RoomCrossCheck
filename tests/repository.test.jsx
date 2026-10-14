import React from "react";
import { test, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createApiRepository, createLocalRepository } from "../src/services/repository.js";
import { RoomProvider } from "../src/context/RoomContext.jsx";
import App from "../src/App.jsx";

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
  await user.type(screen.getByLabelText("매물 이름 *"), "보존할 입력");
  await user.type(screen.getByLabelText("주소 / 건물명 *"), "주소");
  await user.click(screen.getByRole("button", { name: "매물 저장" }));
  expect(await screen.findByText("서버 저장 실패")).toBeInTheDocument();
  expect(screen.getByLabelText("매물 이름 *")).toHaveValue("보존할 입력");
  expect(screen.getByRole("dialog")).toBeInTheDocument();
});
test("API initial load can recover by retry without displaying demo", async () => {
  const repo = { mode: "api", initialState: () => ({ rooms: [], reviews: [], visits: {} }),
    load: vi.fn().mockRejectedValueOnce(new Error("연결 실패")).mockResolvedValue({ rooms: [], reviews: [], visits: {} }) };
  render(<RoomProvider repository={repo}><App /></RoomProvider>);
  expect(await screen.findByRole("alert")).toHaveTextContent("연결 실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
  expect(await screen.findByRole("button", { name: "새 매물 등록" })).toBeInTheDocument();
  expect(screen.queryByText("햇살이 머무는 첫 자취방")).not.toBeInTheDocument();
});

test("listing bridge loads only DB rooms and saves through listings API", async () => {
  const { createListingRepository } = await import("../src/services/repository.js");
  const saved = { id: "db-1", building: "building-1", name: "DB 방", address: "주소", createdAt: "2026-10-14" };
  const fetchImpl = vi.fn().mockResolvedValueOnce({ ok: true, status: 200, json: async () => [saved] })
    .mockResolvedValueOnce({ ok: true, status: 201, json: async () => saved });
  const repo = createListingRepository({ fetchImpl });
  const loaded = await repo.load();
  expect(loaded.rooms).toHaveLength(1);
  expect(loaded.rooms[0]).toMatchObject({ id: "db-1", source: "server", official: null });
  expect(loaded.reviews.length).toBeGreaterThan(0);
  await repo.saveRoom({ name: "DB 방", address: "주소", fee: null });
  expect(fetchImpl.mock.calls[1][0]).toBe("/api/listings");
  expect(fetchImpl.mock.calls[1][1]).toMatchObject({ method: "POST" });
});

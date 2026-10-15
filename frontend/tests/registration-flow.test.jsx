import React from "react";
import { test, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../src/app/App.jsx";
import { RoomProvider } from "../src/app/state/RoomContext.jsx";
import { createLocalRepository } from "../src/app/data/repository.js";

test("manual registration needs no ad, keeps edits when going back, and saves only at final confirmation", async () => {
  const repository = createLocalRepository();
  const save = vi.spyOn(repository, "saveRoom");
  const user = userEvent.setup();
  render(<RoomProvider repository={repository}><App /></RoomProvider>);
  await user.click(screen.getByRole("button", { name: "새 매물 등록" }));
  expect(location.hash).toBe("#register");
  await user.type(screen.getByLabelText("주소 / 건물명 *"), "가상 직접 입력 주소");
  await user.click(screen.getByRole("button", { name: "값 확인으로 이동" }));
  await user.type(screen.getByLabelText("매물 이름 *"), "단계형 매물");
  await user.type(screen.getByLabelText("월세 (만원)"), "0");
  await user.click(screen.getByRole("button", { name: "이전 단계" }));
  await user.click(screen.getByRole("button", { name: "값 확인으로 이동" }));
  expect(screen.getByLabelText("매물 이름 *")).toHaveValue("단계형 매물");
  expect(screen.getByLabelText("월세 (만원)")).toHaveValue(0);
  await user.click(screen.getByRole("button", { name: "최종 확인으로 이동" }));
  expect(save).not.toHaveBeenCalled();
  await user.click(screen.getByRole("button", { name: "확정 저장" }));
  expect(await screen.findByRole("button", { name: "단계형 매물" })).toBeInTheDocument();
  expect(save).toHaveBeenCalledOnce();
  expect(save.mock.calls[0][0]).toMatchObject({ rent: 0, deposit: null, fee: null, original: "" });
});

test("local mode never fabricates an extraction result or posts an advertisement", async () => {
  const fetch = vi.spyOn(globalThis, "fetch");
  render(<RoomProvider repository={createLocalRepository()}><App /></RoomProvider>);
  await userEvent.click(screen.getByRole("button", { name: "새 매물 등록" }));
  await userEvent.click(screen.getByRole("button", { name: "광고 글·사진에서 가져오기" }));
  expect(screen.getByText(/현재 모드에서는 직접 입력/)).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Gemini 추출 시작" })).not.toBeInTheDocument();
  expect(fetch).not.toHaveBeenCalled();
});

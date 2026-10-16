import { createLocalRepository } from "../src/app/data/localRepository.js";
import React from "react";
import { test, expect } from "vitest";
import { render, screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../src/app/App.jsx";
import { RoomProvider } from "../src/app/state/RoomContext.jsx";
import { STORAGE_KEY } from "../src/app/data/localStorage.js";
function setup() {
  const user = userEvent.setup();
  const view = render(
    <RoomProvider repository={createLocalRepository()}>
      <App />
    </RoomProvider>,
  );
  return { user, ...view };
}
test("React registration, editing and reload preserve null and zero", async () => {
  const { user, unmount } = setup();
  await user.click(screen.getByRole("button", { name: "새 매물 등록" }));
  await user.type(screen.getByLabelText("주소 / 건물명 *"), "가상 주소");
  await user.type(screen.getByLabelText("광고 글"), "월세 40만원의 가상 광고");
  await user.click(screen.getByRole("button", { name: "값 확인으로 이동" }));
  await user.type(screen.getByLabelText("매물 이름 *"), "리액트 검증 매물");
  await user.type(screen.getByLabelText("월세 (만원)"), "40");
  await user.click(screen.getByRole("button", { name: "확정 내용 확인" }));
  expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  await user.click(screen.getByRole("button", { name: "확정 저장" }));
  await user.click(screen.getByRole("button", { name: "리액트 검증 매물" }));
  let dialog = within(screen.getByRole("dialog"));
  expect(dialog.getAllByText("정보 없음").length).toBeGreaterThan(0);
  await user.click(dialog.getByRole("button", { name: "정보 수정" }));
  dialog = within(screen.getByRole("dialog"));
  await user.type(dialog.getByLabelText("고정 관리비 (만원)"), "0");
  await user.click(dialog.getByRole("button", { name: "변경 저장" }));
  await waitFor(() =>
    expect(
      JSON.parse(localStorage.getItem(STORAGE_KEY)).rooms.find(
        (r) => r.name === "리액트 검증 매물",
      ).fee,
    ).toBe(0),
  );
  unmount();
  render(
    <RoomProvider repository={createLocalRepository()}>
      <App />
    </RoomProvider>,
  );
  expect(
    screen.getByRole("button", { name: "리액트 검증 매물" }),
  ).toBeInTheDocument();
});
test("comparison requires two listings and displays separate actual match counts", async () => {
  const { user } = setup();
  await user.click(
    screen.getByRole("checkbox", { name: "햇살이 머무는 첫 자취방 비교 선택" }),
  );
  expect(screen.getByRole("button", { name: "비교하기" })).toBeDisabled();
  await user.click(
    screen.getByRole("checkbox", { name: "학교 앞, 여유로운 복층 비교 선택" }),
  );
  await user.click(screen.getByRole("button", { name: "비교하기" }));
  expect(await screen.findByRole("table")).toBeInTheDocument();
  expect(screen.getByText("47만")).toBeInTheDocument();
  expect(screen.getByText("100% · 비교 가능 2개 항목")).toBeInTheDocument();
  expect(screen.getByText("50% · 비교 가능 2개 항목")).toBeInTheDocument();
});
test("review creates updated aggregate; deletion restores existing samples", async () => {
  const { user } = setup();
  await user.click(screen.getByRole("link", { name: "커뮤니티" }));
  await user.click(screen.getByRole("link", { name: "거주 후기" }));
  await user.click(await screen.findByRole("button", { name: "후기 작성" }));
  const d = within(screen.getByRole("dialog"));
  await user.type(d.getByLabelText("거주 시기 *"), "2026년 상반기");
  await user.selectOptions(d.getByLabelText("방음"), "5");
  await user.type(
    d.getByLabelText("후기 내용 *"),
    "리액트 폼으로 작성하는 테스트 후기입니다.",
  );
  await user.click(d.getByRole("button", { name: "후기 저장" }));
  expect(
    screen.getByText("리액트 폼으로 작성하는 테스트 후기입니다."),
  ).toBeInTheDocument();
  let panel = screen
    .getByRole("heading", { name: "가상 대학가 · 햇살하우스" })
    .closest("section");
  expect(within(panel).getByText("3건", { selector: "span.badge" })).toBeInTheDocument();
  await user.click(within(panel).getByRole("button", { name: "삭제" }));
  expect(
    screen.queryByText("리액트 폼으로 작성하는 테스트 후기입니다."),
  ).not.toBeInTheDocument();
});
test("visit checkbox and note survive navigation; deleting private listing keeps public reviews", async () => {
  const { user } = setup();
  await user.click(screen.getByRole("link", { name: "방문 체크리스트" }));
  await user.click(await screen.findByRole("checkbox", { name: /방음/ }));
  await user.type(screen.getByLabelText("나만의 방문 메모"), "수압 직접 확인");
  await user.click(screen.getByRole("button", { name: "방문 기록 저장" }));
  await user.click(screen.getByRole("link", { name: /관심 매물/ }));
  await user.click(
    await screen.findByRole("button", { name: "햇살이 머무는 첫 자취방" }),
  );
  await user.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "방문 기록하기",
    }),
  );
  expect(await screen.findByRole("checkbox", { name: /방음/ })).toBeChecked();
  expect(screen.getByLabelText("나만의 방문 메모")).toHaveValue(
    "수압 직접 확인",
  );
  await user.click(screen.getByRole("link", { name: /관심 매물/ }));
  await user.click(
    await screen.findByRole("button", { name: "햇살이 머무는 첫 자취방" }),
  );
  await user.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "매물 삭제",
    }),
  );
  await user.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "삭제하기",
    }),
  );
  await waitFor(() => {
    const s = JSON.parse(localStorage.getItem(STORAGE_KEY));
    expect(s.rooms).toHaveLength(2);
    expect(s.reviews).toHaveLength(3);
    expect(s.visits.r1).toBeUndefined();
  });
});

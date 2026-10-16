import React from "react";
import { test, expect } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../src/app/App.jsx";
import { RoomProvider } from "../src/app/state/RoomContext.jsx";
import { createLocalRepository } from "../src/app/data/localRepository.js";
import { STORAGE_KEY } from "../src/app/data/localStorage.js";

function setup(hash = "#home") {
  history.replaceState(null, "", `/${hash}`);
  render(<RoomProvider repository={createLocalRepository()}><App /></RoomProvider>);
  return userEvent.setup();
}

test("home links to public posts and returns to the correct community category", async () => {
  const user = setup();
  expect(screen.getByRole("heading", { name: /내 첫 방을 고르는 일/ })).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "커뮤니티 둘러보기" }));
  await user.click(screen.getByRole("link", { name: /원룸체크 이용 안내와 공개 후기 원칙/ }));
  expect(screen.getByRole("heading", { name: "원룸체크 이용 안내와 공개 후기 원칙" })).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: /공지사항 목록/ }));
  expect(screen.getByRole("link", { name: "공지사항" })).toHaveAttribute("aria-current", "page");
});

test("backtracking through registration preserves entered amounts without saving early", async () => {
  const user = setup("#register");
  await user.type(screen.getByLabelText("주소 / 건물명 *"), "가상 주소");
  await user.type(screen.getByLabelText("광고 글"), "입력 보존 확인용 광고");
  await user.click(screen.getByRole("button", { name: "값 확인으로 이동" }));
  await user.type(screen.getByLabelText("매물 이름 *"), "뒤로 가도 남는 매물");
  await user.type(screen.getByLabelText("월세 (만원)"), "42");
  await user.click(screen.getByRole("button", { name: "이전 단계" }));
  await user.click(screen.getByRole("button", { name: "값 확인으로 이동" }));
  expect(screen.getByLabelText("매물 이름 *")).toHaveValue("뒤로 가도 남는 매물");
  expect(screen.getByLabelText("월세 (만원)")).toHaveValue(42);
  expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
});

test("signup validates confirmation and never saves credentials or claims authentication", async () => {
  const user = setup("#signup");
  await user.type(screen.getByLabelText("아이디"), "roomuser");
  await user.type(screen.getByLabelText("이름"), "테스트");
  await user.type(screen.getByLabelText("비밀번호", { exact: true }), "password123");
  await user.type(screen.getByLabelText("비밀번호 확인"), "different123");
  await user.click(screen.getByRole("button", { name: "회원가입", exact: true }));
  expect(screen.getByRole("alert")).toHaveTextContent("일치하지 않습니다");
  await user.clear(screen.getByLabelText("비밀번호 확인"));
  await user.type(screen.getByLabelText("비밀번호 확인"), "password123");
  await user.click(screen.getByRole("button", { name: "회원가입", exact: true }));
  expect(screen.getByText(/인증 서버 연동 전이므로 계정 생성·로그인은 처리되지 않았습니다/)).toHaveAttribute("role", "status");
  expect(screen.getByLabelText("비밀번호", { exact: true })).toHaveValue("");
  expect(localStorage.length).toBe(0);
});

test("manual registration saves conditions without a photo or advertising text", async () => {
  const user = setup("#register");
  expect(screen.getByRole("button", { name: "직접 입력", exact: true })).toHaveAttribute("aria-pressed", "true");
  expect(screen.queryByLabelText("광고 이미지")).not.toBeInTheDocument();
  await user.type(screen.getByLabelText("주소 / 건물명 *"), "사진 없는 가상 주소");
  await user.click(screen.getByRole("button", { name: "값 확인으로 이동" }));
  await user.type(screen.getByLabelText("매물 이름 *"), "직접 입력한 방");
  await user.type(screen.getByLabelText("월세 (만원)"), "42");
  await user.type(screen.getByLabelText("고정 관리비 (만원)"), "0");
  await user.click(screen.getByRole("button", { name: "확정 내용 확인" }));
  await user.click(screen.getByRole("button", { name: "확정 저장" }));
  const saved = JSON.parse(localStorage.getItem(STORAGE_KEY)).rooms[0];
  expect(saved).toMatchObject({ registrationMethod: "manual", original: "", attachmentName: "", rent: 420000, fee: 0, deposit: null });
});

test("photo registration requires an image, while switching to manual preserves the address", async () => {
  const user = setup("#register");
  await user.click(screen.getByRole("button", { name: "사진으로 등록", exact: true }));
  expect(screen.queryByLabelText("광고 글")).not.toBeInTheDocument();
  await user.type(screen.getByLabelText("주소 / 건물명 *"), "등록 방식 전환 주소");
  await user.click(screen.getByRole("button", { name: "값 확인으로 이동" }));
  expect(screen.getByRole("alert")).toHaveTextContent("광고 이미지를 선택");
  fireEvent.change(screen.getByLabelText("광고 이미지"), { target: { files: [new File(["invalid"], "document.pdf", { type: "application/pdf" })] } });
  expect(screen.getByRole("alert")).toHaveTextContent("JPG·PNG");
  await user.click(screen.getByRole("button", { name: "직접 입력", exact: true }));
  expect(screen.getByLabelText("주소 / 건물명 *")).toHaveValue("등록 방식 전환 주소");
  await user.click(screen.getByRole("button", { name: "값 확인으로 이동" }));
  expect(screen.getByRole("heading", { name: "매물 정보를 직접 입력하세요" })).toBeInTheDocument();
});

test("drafts resume registration, preserve source metadata, and cannot be compared", async () => {
  const user = setup("#register");
  await user.type(screen.getByLabelText("주소 / 건물명 *"), "확인 전 가상 주소");
  await user.type(screen.getByLabelText("광고 글"), "보증금 500만원의 광고 원문");
  await user.type(screen.getByLabelText("호수", { exact: true }), "301");
  await user.click(screen.getByRole("button", { name: "임시 저장" }));
  const saved = JSON.parse(localStorage.getItem(STORAGE_KEY)).rooms[0];
  expect(saved).toMatchObject({ status: "DRAFT", unitNumber: "301" });
  expect(screen.getByRole("checkbox", { name: "확인 중인 매물 비교 선택" })).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "확인 중인 매물" }));
  await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "등록 이어가기" }));
  expect(screen.getByLabelText("광고 글")).toHaveValue(saved.original);
  expect(screen.getByLabelText("호수", { exact: true })).toHaveValue("301");
});

test("detail separates seven candidate rows and local visit content from public review tab", async () => {
  const user = setup("#rooms");
  await user.click(screen.getByRole("button", { name: "햇살이 머무는 첫 자취방" }));
  const dialog = within(screen.getByRole("dialog"));
  await user.click(dialog.getByRole("button", { name: "공식 대조", exact: true }));
  expect(dialog.getByText("시연 자료 있음")).toBeInTheDocument();
  expect(dialog.getByText("사용승인일")).toBeInTheDocument();
  await user.click(dialog.getByRole("button", { name: "방문 기록", exact: true }));
  expect(dialog.getByText(/아직 저장한 방문 기록/)).toBeInTheDocument();
  await user.click(dialog.getByRole("button", { name: "건물 후기", exact: true }));
  await user.click(dialog.getByRole("button", { name: /커뮤니티에서 후기 보기/ }));
  expect(screen.getByRole("link", { name: "거주 후기" })).toHaveAttribute("aria-current", "page");
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});

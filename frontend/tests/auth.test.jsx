import React from "react";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import AuthPanel from "../src/features/auth/AuthPanel.jsx";

const member = { id: 7, loginId: "member.one", nickname: "준비하는 사람", email: "one@example.com", role: "USER" };
function setup(initialMember = null) {
  const api = { me: vi.fn().mockResolvedValue(initialMember), login: vi.fn().mockResolvedValue(member), signup: vi.fn().mockResolvedValue(member), logout: vi.fn().mockResolvedValue() };
  const changed = vi.fn();
  render(<AuthPanel api={api} onSessionChange={changed} />);
  return { api, changed, user: userEvent.setup() };
}

test("login updates the card, shows only profile details and logout restores guest controls", async () => {
  const { api, changed, user } = setup();
  await user.click(await screen.findByRole("button", { name: "로그인", exact: true }));
  const dialog = within(screen.getByRole("dialog", { name: "로그인" }));
  await user.type(dialog.getByLabelText("아이디"), member.loginId);
  await user.type(dialog.getByLabelText("비밀번호", { exact: true }), "password123");
  await user.click(dialog.getByRole("button", { name: "로그인", exact: true }));
  expect(api.login).toHaveBeenCalledWith(member.loginId, "password123");
  expect(await screen.findByText(`${member.nickname}님, 안녕하세요`)).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "내 정보" }));
  const profile = within(screen.getByRole("dialog", { name: "내 정보" }));
  expect(profile.getByText(member.email)).toBeInTheDocument();
  expect(profile.queryByText("비밀번호")).not.toBeInTheDocument();
  await user.click(profile.getByRole("button", { name: "확인" }));
  await user.click(screen.getByRole("button", { name: "로그아웃", exact: true }));
  expect(await screen.findByRole("button", { name: "로그인", exact: true })).toBeInTheDocument();
  expect(api.logout).toHaveBeenCalledOnce();
  expect(changed).toHaveBeenCalledTimes(3);
});

test("signup validates confirmation and moves to login without automatic authentication", async () => {
  const { api, user } = setup();
  await user.click(await screen.findByRole("button", { name: "회원가입", exact: true }));
  const dialog = within(screen.getByRole("dialog", { name: "회원가입" }));
  await user.type(dialog.getByLabelText("아이디"), member.loginId);
  await user.type(dialog.getByLabelText("비밀번호", { exact: true }), "password123");
  await user.type(dialog.getByLabelText("비밀번호 확인"), "different");
  await user.type(dialog.getByLabelText("닉네임"), member.nickname);
  await user.type(dialog.getByLabelText("이메일"), member.email);
  await user.click(dialog.getByRole("button", { name: "회원가입", exact: true }));
  expect(await dialog.findByRole("alert")).toHaveTextContent("일치하지 않습니다");
  expect(api.signup).not.toHaveBeenCalled();
  await user.clear(dialog.getByLabelText("비밀번호 확인"));
  await user.type(dialog.getByLabelText("비밀번호 확인"), "password123");
  await user.click(dialog.getByRole("button", { name: "회원가입", exact: true }));
  expect(await screen.findByRole("dialog", { name: "로그인" })).toBeInTheDocument();
  expect(screen.getByLabelText("아이디")).toHaveValue(member.loginId);
  expect(screen.getByLabelText("비밀번호", { exact: true })).toHaveValue("");
  expect(api.signup).toHaveBeenCalledOnce();
  expect(api.login).not.toHaveBeenCalled();
});

test("login error preserves input, prevents duplicate submission and clears secrets on switching", async () => {
  const { api, user } = setup();
  let rejectLogin;
  api.login.mockImplementation(() => new Promise((_resolve, reject) => { rejectLogin = reject; }));
  await user.click(await screen.findByRole("button", { name: "로그인", exact: true }));
  const dialog = within(screen.getByRole("dialog", { name: "로그인" }));
  await user.type(dialog.getByLabelText("아이디"), member.loginId);
  await user.type(dialog.getByLabelText("비밀번호", { exact: true }), "wrong-pass");
  fireEvent.submit(dialog.getByRole("button", { name: "로그인", exact: true }).closest("form"));
  fireEvent.submit(dialog.getByLabelText("아이디").closest("form"));
  expect(api.login).toHaveBeenCalledOnce();
  rejectLogin(new Error("아이디 또는 비밀번호가 올바르지 않습니다."));
  expect(await dialog.findByRole("alert")).toHaveTextContent("올바르지 않습니다");
  expect(dialog.getByLabelText("아이디")).toHaveValue(member.loginId);
  await user.click(dialog.getByRole("button", { name: "회원가입", exact: true }));
  expect(screen.getByRole("dialog", { name: "회원가입" })).toBeInTheDocument();
  expect(screen.getByLabelText("비밀번호", { exact: true })).toHaveValue("");
});

test("an existing server session is restored and losing it in another tab resets the card", async () => {
  const { api, changed } = setup(member);
  expect(await screen.findByRole("button", { name: "내 정보" })).toBeInTheDocument();
  api.me.mockResolvedValue(null);
  fireEvent(window, new Event("focus"));
  expect(await screen.findByRole("button", { name: "로그인", exact: true })).toBeInTheDocument();
  await waitFor(() => expect(changed).toHaveBeenCalledTimes(2));
});

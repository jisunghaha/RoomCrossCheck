import React from "react";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import AuthPanel from "../src/features/auth/AuthPanel.jsx";

const member = { id: 7, loginId: "member.one", nickname: "준비하는 사람", email: "one@example.com", role: "USER" };
function setup(initialMember = null) {
  const api = {
    me: vi.fn().mockResolvedValue(initialMember), login: vi.fn(), signup: vi.fn(), logout: vi.fn(),
    requestPasswordReset: vi.fn().mockResolvedValue({ message: "가입한 계정이 있으면 인증 코드를 보냈습니다." }),
    confirmPasswordReset: vi.fn().mockResolvedValue({}),
    changePassword: vi.fn().mockResolvedValue({}),
    withdraw: vi.fn().mockResolvedValue({}),
  };
  const changed = vi.fn();
  render(<AuthPanel api={api} onSessionChange={changed} />);
  return { api, changed, user: userEvent.setup() };
}

test("forgot password sends a code to the email and sets a new password", async () => {
  const { api, user } = setup();
  await user.click(await screen.findByRole("button", { name: "로그인", exact: true }));
  await user.click(screen.getByRole("button", { name: "비밀번호를 잊으셨나요?" }));
  const dialog = within(screen.getByRole("dialog", { name: "비밀번호 찾기" }));
  await user.type(dialog.getByLabelText("가입 이메일"), "one@example.com");
  await user.click(dialog.getByRole("button", { name: "인증 코드 받기" }));
  expect(api.requestPasswordReset).toHaveBeenCalledWith("one@example.com");
  expect(await dialog.findByText("가입한 계정이 있으면 인증 코드를 보냈습니다.")).toBeInTheDocument();
  await user.type(dialog.getByLabelText("인증 코드 (6자리)"), "123456");
  await user.type(dialog.getByLabelText("새 비밀번호"), "new-password-1");
  await user.type(dialog.getByLabelText("새 비밀번호 확인"), "different-1");
  await user.click(dialog.getByRole("button", { name: "비밀번호 바꾸기" }));
  expect(dialog.getByRole("alert")).toHaveTextContent("일치하지 않습니다");
  await user.clear(dialog.getByLabelText("새 비밀번호 확인"));
  await user.type(dialog.getByLabelText("새 비밀번호 확인"), "new-password-1");
  await user.click(dialog.getByRole("button", { name: "비밀번호 바꾸기" }));
  expect(api.confirmPasswordReset).toHaveBeenCalledWith({ email: "one@example.com", code: "123456", newPassword: "new-password-1" });
  expect(await screen.findByText(/새 비밀번호로 로그인해 주세요/)).toBeInTheDocument();
  expect(screen.getByRole("dialog", { name: "로그인" })).toBeInTheDocument();
});

test("profile changes the password and withdrawal requires consent then logs out", async () => {
  const { api, changed, user } = setup(member);
  await user.click(await screen.findByRole("button", { name: "내 정보" }));
  await user.click(screen.getByRole("button", { name: "비밀번호 변경" }));
  let dialog = within(screen.getByRole("dialog", { name: "비밀번호 변경" }));
  await user.type(dialog.getByLabelText("현재 비밀번호"), "old-password-1");
  await user.type(dialog.getByLabelText("새 비밀번호"), "new-password-1");
  await user.type(dialog.getByLabelText("새 비밀번호 확인"), "new-password-1");
  await user.click(dialog.getByRole("button", { name: "비밀번호 변경" }));
  expect(api.changePassword).toHaveBeenCalledWith("old-password-1", "new-password-1");
  expect(await screen.findByText("비밀번호를 바꿨어요.")).toBeInTheDocument();

  await user.click(screen.getByRole("button", { name: "회원 탈퇴" }));
  dialog = within(screen.getByRole("dialog", { name: "회원 탈퇴" }));
  expect(dialog.getByRole("button", { name: "탈퇴하기" })).toBeDisabled();
  await user.type(dialog.getByLabelText("비밀번호 확인"), "new-password-1");
  await user.click(dialog.getByLabelText(/탈퇴에 동의합니다/));
  await user.click(dialog.getByRole("button", { name: "탈퇴하기" }));
  expect(api.withdraw).toHaveBeenCalledWith("new-password-1");
  expect(await screen.findByText("탈퇴가 완료됐어요. 이용해 주셔서 감사합니다.")).toBeInTheDocument();
  expect(changed).toHaveBeenLastCalledWith(null);
});

import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import DemoLoginPanel from "../src/features/community/DemoLoginPanel.jsx";

const guest = { authenticated: false, userId: null, administrator: false };
const ok = () => ({ ok: true, json: async () => ({}) });

test("demo login is hidden when the server profile is disabled", async () => {
  const fetchImpl = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ demoEnabled: false }) });
  render(<DemoLoginPanel session={guest} onChanged={vi.fn()} fetchImpl={fetchImpl} />);
  await waitFor(() => expect(fetchImpl).toHaveBeenCalledTimes(1));
  expect(screen.queryByRole("heading", { name: "시연용 로그인" })).not.toBeInTheDocument();
});

test("login sends a form with CSRF, clears the password, and refreshes session state", async () => {
  document.cookie = "XSRF-TOKEN=csrf-test; path=/";
  const fetchImpl = vi.fn().mockResolvedValueOnce({ ok: true, json: async () => ({ demoEnabled: true }) })
    .mockResolvedValueOnce(ok()).mockResolvedValueOnce(ok()).mockResolvedValueOnce(ok());
  const changed = vi.fn();
  const user = userEvent.setup(); render(<DemoLoginPanel session={guest} onChanged={changed} fetchImpl={fetchImpl} />);
  await user.type(await screen.findByLabelText("시연 아이디"), "member1");
  await user.type(screen.getByLabelText("시연 비밀번호"), "test-only-password");
  await user.click(screen.getByRole("button", { name: "로그인", exact: true }));
  await waitFor(() => expect(changed).toHaveBeenCalledTimes(1));
  expect(fetchImpl.mock.calls[2][0]).toBe("/api/auth/login");
  expect(fetchImpl.mock.calls[2][1]).toMatchObject({ credentials: "include", method: "POST",
    headers: { "X-XSRF-TOKEN": "csrf-test" } });
  expect(new URLSearchParams(fetchImpl.mock.calls[2][1].body).get("username")).toBe("member1");
  expect(screen.getByLabelText("시연 비밀번호")).toHaveValue("");
  expect(localStorage.length).toBe(0);
  document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
});

test("incorrect credentials show a safe error and do not preserve the password", async () => {
  document.cookie = "XSRF-TOKEN=csrf-test; path=/";
  const fetchImpl = vi.fn().mockResolvedValueOnce({ ok: true, json: async () => ({ demoEnabled: true }) })
    .mockResolvedValueOnce(ok()).mockResolvedValueOnce({ ok: false, status: 401 });
  const user = userEvent.setup(); render(<DemoLoginPanel session={guest} onChanged={vi.fn()} fetchImpl={fetchImpl} />);
  await user.type(await screen.findByLabelText("시연 아이디"), "member1");
  await user.type(screen.getByLabelText("시연 비밀번호"), "wrong-password");
  await user.click(screen.getByRole("button", { name: "로그인", exact: true }));
  expect(await screen.findByRole("alert")).toHaveTextContent("아이디 또는 비밀번호가 올바르지 않습니다");
  expect(screen.getByLabelText("시연 비밀번호")).toHaveValue("");
  document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
});

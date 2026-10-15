import React from "react";
import { expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "../src/app/App.jsx";
import { RoomProvider } from "../src/app/state/RoomContext.jsx";
import { createLocalRepository } from "../src/app/data/localRepository.js";
import { DEMO_SESSION_KEY } from "../src/features/auth/model/useDemoAuth.js";

function renderApp(repository = createLocalRepository()) {
  return render(<RoomProvider repository={repository}><App /></RoomProvider>);
}

test.each([
  ["admin", "admin1234", "demo-admin", "관리자 · 시연"],
  ["user", "user1234", "demo-user", "일반 사용자 · 시연"],
])("demo account %s can select its role, reload, and log out", async (username, password, id, role) => {
  history.replaceState(null, "", "/#login");
  const view = renderApp();
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("아이디"), username);
  await user.type(screen.getByLabelText("비밀번호"), password);
  await user.click(screen.getByRole("button", { name: "로그인", exact: true }));
  expect(screen.getByText(role)).toBeInTheDocument();
  expect(window.sessionStorage.getItem(DEMO_SESSION_KEY)).toBe(id);
  expect(localStorage.length).toBe(0);
  expect(JSON.stringify(window.sessionStorage)).not.toContain(password);
  view.unmount();
  renderApp();
  expect(screen.getByText(role)).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "로그아웃" }));
  expect(screen.queryByText(role)).not.toBeInTheDocument();
  expect(window.sessionStorage.getItem(DEMO_SESSION_KEY)).toBeNull();
  expect(screen.getByRole("link", { name: "로그인" })).toBeInTheDocument();
});

test("incorrect credentials do not create a demo session", async () => {
  history.replaceState(null, "", "/#login");
  renderApp();
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("아이디"), "admin");
  await user.type(screen.getByLabelText("비밀번호"), "wrong-password");
  await user.click(screen.getByRole("button", { name: "로그인", exact: true }));
  expect(screen.getByRole("alert")).toHaveTextContent("맞지 않습니다");
  expect(window.sessionStorage.getItem(DEMO_SESSION_KEY)).toBeNull();
  expect(screen.getByLabelText("비밀번호")).toHaveValue("");
});

test("API mode neither displays demo credentials nor accepts the demo session", async () => {
  window.sessionStorage.setItem(DEMO_SESSION_KEY, "demo-admin");
  history.replaceState(null, "", "/#login");
  renderApp({ mode: "api", initialState: () => ({ rooms: [], reviews: [], visits: {} }), load: async () => ({ rooms: [], reviews: [], visits: {} }) });
  expect(await screen.findByLabelText("아이디")).toBeInTheDocument();
  expect(screen.queryByText("관리자 · 시연")).not.toBeInTheDocument();
  expect(screen.queryByText("테스트 계정으로 둘러보기")).not.toBeInTheDocument();
});

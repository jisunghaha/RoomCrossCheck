import React from "react";
import { test, expect, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RoomProvider } from "../src/app/state/RoomContext.jsx";
import { createLocalRepository } from "../src/app/data/repository.js";
import App from "../src/app/App.jsx";
import ReportsPage from "../src/features/report/ReportsPage.jsx";
import ReportButton from "../src/features/report/ReportButton.jsx";

test("members can add a checklist item, record a visit date and see it in their visit history", async () => {
  const user = userEvent.setup();
  history.replaceState(null, "", "/#visits");
  const repo = createLocalRepository();
  render(<RoomProvider repository={repo}><App /></RoomProvider>);
  await user.click(screen.getByRole("button", { name: "체크리스트 구성" }));
  const editor = within(screen.getByRole("region", { name: "체크리스트 구성" }));
  await user.click(editor.getByRole("button", { name: /항목 추가/ }));
  await user.type(editor.getByLabelText("항목 10 이름"), "보일러 연식");
  await user.click(editor.getByRole("button", { name: "주차 삭제" }));
  await user.click(editor.getByRole("button", { name: "구성 저장" }));
  expect(screen.queryByRole("region", { name: "체크리스트 구성" })).not.toBeInTheDocument();
  await user.click(screen.getByLabelText(/보일러 연식/));
  expect(screen.queryByText("주차")).not.toBeInTheDocument();
  await user.type(screen.getByLabelText("방문일"), "2026-11-20");
  await user.click(screen.getByRole("button", { name: "방문 기록 저장" }));
  const records = within(screen.getByRole("region", { name: "내 방문 기록" }));
  expect(records.getByText(/2026-11-20 방문 · 확인 1 \/ 9개/)).toBeInTheDocument();
  const stored = JSON.parse(localStorage.getItem("oneroom.frontend.v1"));
  expect(stored.checklist.map(item => item.label)).toContain("보일러 연식");
  expect(Object.values(stored.visits)[0].visitedOn).toBe("2026-11-20");
});

test("report button sends the selected reason and shows the receipt", async () => {
  const user = userEvent.setup();
  const api = { report: vi.fn().mockResolvedValue({ id: "r1", status: "PENDING" }) };
  render(<ReportButton targetType="COMMENT" targetId="c1" postId="p1" api={api} />);
  await user.click(screen.getByRole("button", { name: "신고" }));
  await user.selectOptions(screen.getByLabelText("신고 사유 *"), "OTHER");
  await user.click(screen.getByRole("button", { name: "신고하기" }));
  expect(screen.getByRole("alert")).toHaveTextContent("기타 사유는 설명");
  await user.type(screen.getByLabelText("설명 *"), "연락처 노출");
  await user.click(screen.getByRole("button", { name: "신고하기" }));
  expect(await screen.findByText("신고를 접수했어요")).toBeInTheDocument();
  expect(api.report).toHaveBeenCalledWith({ targetType: "COMMENT", targetId: "c1", postId: "p1", reason: "OTHER", detail: "연락처 노출" });
});

test("admin report page lists pending reports and resolves them; members are refused", async () => {
  const user = userEvent.setup();
  const pending = { id: "r1", targetType: "REVIEW", targetId: "v1", reason: "ABUSE", targetPreview: "문제 후기",
    status: "PENDING", createdAt: "2026-11-20T00:00:00Z", targetReportCount: 2, targetAuthorId: 7, targetExists: true };
  const page = content => ({ content, number: 0, totalPages: 1, first: true, last: true });
  const api = { list: vi.fn().mockResolvedValueOnce(page([pending])).mockResolvedValue(page([])), resolve: vi.fn().mockResolvedValue({}) };
  const { unmount } = render(<ReportsPage member={{ role: "USER" }} api={api} />);
  expect(screen.getByText("관리자만 볼 수 있는 화면이에요")).toBeInTheDocument();
  expect(api.list).not.toHaveBeenCalled();
  unmount();
  render(<ReportsPage member={{ role: "ADMIN" }} api={api} />);
  expect(await screen.findByRole("heading", { name: "문제 후기" })).toBeInTheDocument();
  expect(screen.getByText(/같은 대상 신고 2건/)).toBeInTheDocument();
  await user.type(screen.getByLabelText("처리 메모 (선택)"), "욕설");
  await user.click(screen.getByRole("button", { name: "대상 삭제" }));
  expect(api.resolve).toHaveBeenCalledWith("r1", "DELETE_TARGET", "욕설");
  expect(await screen.findByText("처리 대기 신고가 없어요.")).toBeInTheDocument();
});

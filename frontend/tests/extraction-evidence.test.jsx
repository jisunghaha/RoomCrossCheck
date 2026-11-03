import React from "react";
import { test, expect, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ExtractionEvidence from "../src/features/extraction/components/ExtractionEvidence.jsx";
import AdDraftPanel from "../src/features/extraction/components/AdDraftPanel.jsx";

const extraction = { rawText: "월세 35만원 관리비 0원", monthlyRentWon: 350000, maintenanceFeeWon: 0, areaType: "미상",
  evidence: { monthlyRentWon: { quote: "월세 35만원", source: "INPUT_TEXT", status: "PRESENT" },
    maintenanceFeeWon: { quote: "관리비 0원", source: "IMAGE_READ_TEXT", status: "PRESENT" },
    depositWon: { quote: null, source: null, status: "NOT_STATED" },
    areaType: { quote: null, source: null, status: "UNCERTAIN", reason: "면적 종류를 확인해 주세요." } } };

test("shows AI provenance, uncertainty and legacy absence separately without claiming accuracy", () => {
  render(<ExtractionEvidence draft={{ extraction, imageName: "ad.png", originalText: "월세 35만원" }} />);
  const rows=screen.getAllByRole("row");
  expect(within(rows.find(row=>within(row).queryByRole("rowheader",{name:"관리비"}))).getByText("0원")).toBeInTheDocument();
  expect(screen.getByText("AI 판단: 미기재")).toBeInTheDocument();
  expect(screen.getByText("확인 필요")).toBeInTheDocument();
  expect(screen.getAllByText("근거 기록 없음").length).toBeGreaterThan(0);
  expect(screen.getByText(/AI가 읽은 이미지 글에서 찾은 문구/)).toBeInTheDocument();
  expect(screen.getByText(/정확성을 보증하지 않습니다/)).toBeInTheDocument();
});

test("correction saves values without changing or submitting original AI evidence", async () => {
  const ready = { id: "evidence-ad", status: "EXTRACTION_READY", originalText: "월세 35만원", extraction };
  const fetchMock=vi.spyOn(globalThis,"fetch").mockResolvedValueOnce({ok:true,json:async()=>[ready]})
    .mockResolvedValueOnce({ok:true,json:async()=>({...ready,status:"REVIEWED"})})
    .mockResolvedValueOnce({ok:true,json:async()=>[{...ready,status:"REVIEWED",reviewed:{...extraction,monthlyRentWon:380000}}]});
  const user=userEvent.setup();render(<AdDraftPanel />);
  const rent=await screen.findByLabelText("월세 (원)");await user.clear(rent);await user.type(rent,"380000");
  expect(screen.getByText("350,000원")).toBeInTheDocument();
  await user.click(screen.getByRole("button",{name:"확인한 값 저장"}));
  await screen.findByText("확인한 추출 값을 저장했어요.");
  const sent=JSON.parse(fetchMock.mock.calls[1][1].body);expect(sent.monthlyRentWon).toBe(380000);expect(sent).not.toHaveProperty("evidence");
  expect(screen.getByText("350,000원")).toBeInTheDocument();
  expect(screen.getByLabelText("월세 (원)")).toHaveValue(380000);
});

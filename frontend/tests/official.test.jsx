import { test, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react";
import OfficialLookup from "../src/features/listing/components/OfficialLookup.jsx";
import { audit } from "../src/shared/lib/domain.js";
const mocks = vi.hoisted(() => ({ searchOfficial: vi.fn(), saveOfficial: vi.fn() }));
vi.mock("../src/app/state/RoomContext.jsx", () => ({ useRooms: () => ({ dataMode: "listing-api", ...mocks }) }));

test("official audit shows address discrepancy and amount of area/floor difference", () => {
  const result = audit({ address: "다른 주소", area: 23, areaType: "전용", floor: 3, official: { buildingId: "b", address: "공식 주소", addressMatch: false, scope: "호실", area: 20, areaType: "전용", floor: -1 } });
  expect(result.rate).toBe(0);
  expect(result.rows[0].match).toBe(false);
  expect(result.rows.find(r => r.label === "면적").reason).toContain("3.00㎡");
  expect(result.rows.find(r => r.label === "층수").reason).toContain("4개 층");
});
test("a different selected unit cannot be presented as all matching", () => {
  const result = audit({ address: "서울 강남구 삼성로 212, 26동 801호", area: 84.43, areaType: "전용", floor: 8,
    official: { buildingId: "b", address: "서울특별시 강남구 삼성로 212", addressMatch: true, scope: "호실", dong: "26", ho: "808호", area: 84.43, areaType: "전용", floor: 8 } });
  expect(result.rate).toBe(75);
  expect(result.rows.find(r => r.label === "동·호실").match).toBe(false);
});
test("building-only data does not claim a room area or floor match", () => {
  const result = audit({ address: "주소", area: 20, areaType: "전용", floor: 3, official: { buildingId: "b", address: "주소", addressMatch: true, scope: "건물", area: null, floor: null } });
  expect(result.count).toBe(1);
  expect(result.rows.filter(r => r.comparable).map(r => r.label)).toEqual(["주소"]);
});
test("manual lookup selects a building and unit then saves only server identifiers", async () => {
  cleanup(); mocks.searchOfficial.mockReset(); mocks.saveOfficial.mockReset();
  const b = { id: "b1", name: "테스트 건물", address: "공식 주소", dong: "1" };
  mocks.searchOfficial.mockResolvedValueOnce({ searchId: "s1", buildings: [b], units: [], message: "건물 선택" })
    .mockResolvedValueOnce({ searchId: "s2", buildings: [b], units: [{ id: "u1", dong: "1", ho: "301호", area: 20, floor: 3 }], message: "호실 선택" });
  mocks.saveOfficial.mockResolvedValue(true);
  render(<OfficialLookup room={{ id: "room1", address: "등록 주소" }} />);
  fireEvent.change(screen.getByLabelText("법정동코드 10자리"), { target: { value: "1168010100" } });
  fireEvent.change(screen.getByLabelText("지번 본번"), { target: { value: "737" } });
  fireEvent.click(screen.getByRole("button", { name: "건축물대장 조회" }));
  await screen.findByText("건물 선택");
  fireEvent.change(screen.getByLabelText("비교할 호실"), { target: { value: "301호" } });
  fireEvent.click(screen.getByRole("button", { name: "선택한 건물의 호실 조회" }));
  await screen.findByText("호실 선택");
  fireEvent.click(screen.getByRole("button", { name: "이 호실로 비교 결과 저장" }));
  await waitFor(() => expect(mocks.saveOfficial).toHaveBeenCalledWith("room1", { searchId: "s2", buildingId: "b1", unitId: "u1" }));
  cleanup();
});
test("lookup failure preserves input and provides retry", async () => {
  mocks.searchOfficial.mockRejectedValueOnce(new Error("공식 서버 연결 실패"));
  render(<OfficialLookup room={{ id: "room1", address: "주소" }} />);
  fireEvent.change(screen.getByLabelText("법정동코드 10자리"), { target: { value: "1168010100" } });
  fireEvent.change(screen.getByLabelText("지번 본번"), { target: { value: "737" } });
  fireEvent.click(screen.getByRole("button", { name: "건축물대장 조회" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("공식 서버 연결 실패");
  expect(screen.getByLabelText("지번 본번")).toHaveValue("737");
  expect(screen.getByRole("button", { name: "건축물대장 조회" })).toBeEnabled();
  cleanup();
});
test("address search uses registered address and can reopen with an already loaded script", async () => {
  const embeds = [];
  const constructors = [];
  window.kakao = { Postcode: class {
    constructor(options) { constructors.push(options); }
    embed(element, options) { expect(element).toBeInstanceOf(HTMLElement); embeds.push(options); }
  } };
  mocks.searchOfficial.mockResolvedValueOnce({ searchId: "s", buildings: [], units: [], message: "자료 없음" });
  render(<OfficialLookup room={{ id: "r", address: "서울특별시 강남구 삼성로 212, 26동 808호" }} />);
  fireEvent.click(screen.getByRole("button", { name: "등록 주소로 검색" }));
  await waitFor(() => expect(embeds).toHaveLength(1));
  expect(embeds[0].q).toBe("서울특별시 강남구 삼성로 212");
  fireEvent.click(screen.getByRole("button", { name: "주소 검색 닫기" }));
  fireEvent.click(screen.getByRole("button", { name: "등록 주소로 검색" }));
  await waitFor(() => expect(embeds).toHaveLength(2));
  const { act } = await import("@testing-library/react");
  act(() => constructors[1].oncomplete({ bcode: "1168010600", jibunAddress: "서울 강남구 대치동 316", roadAddress: "서울 강남구 삼성로 212" }));
  fireEvent.click(screen.getByRole("button", { name: "건축물대장 조회" }));
  await screen.findByText("자료 없음");
  expect(mocks.searchOfficial).toHaveBeenLastCalledWith("r", { legalCode: "1168010600", bun: "316", ji: "0", landType: "0" });
  delete window.kakao;
  cleanup();
});

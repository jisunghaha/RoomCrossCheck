import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { chromium } from "playwright-core";

const browser = await chromium.launch({
  executablePath: process.env.CHROMIUM_PATH || "/usr/bin/chromium",
  headless: true, args: ["--no-sandbox"],
});
try {
  const page = await browser.newPage({ baseURL: process.env.SFR_BROWSER_URL, viewport: { width: 1280, height: 1000 } });
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.goto(process.env.SFR_BROWSER_URL);
  await page.getByRole("button", { name: "새 매물 등록", exact: true }).click();
  await page.getByRole("button", { name: "광고 글·사진에서 가져오기", exact: true }).click();
  await page.getByLabel("광고 글", { exact: true }).fill("개인정보 없는 브라우저 검증 광고: 월세 42만원, 관리비 0원, 면적 23.75㎡, 지하 2층. 보증금과 주소 미기재.");
  await page.getByRole("button", { name: "광고 원본 임시 저장" }).click();
  await page.getByText(/원본을 임시 저장했어요/).waitFor();
  await page.getByRole("button", { name: "원본 확정", exact: true }).click();
  await page.getByRole("button", { name: "Gemini 추출 시작", exact: true }).click();
  const rent = page.getByLabel("월세 (원)", { exact: true });
  await rent.waitFor();
  assert.equal(await rent.inputValue(), "420000");
  assert.equal(await page.getByRole("button", { name: "확인한 값으로 매물 등록" }).count(), 0);
  await rent.fill("430000");
  await page.getByRole("button", { name: "확인한 값 저장", exact: true }).click();
  await page.getByRole("button", { name: "확인한 값으로 매물 등록" }).click();
  const dialog = page.getByRole("main");
  assert.equal(await dialog.getByLabel("월세 (만원)").inputValue(), "43");
  assert.equal(await dialog.getByLabel("고정 관리비 (만원)").inputValue(), "0");
  assert.equal(await dialog.getByLabel("보증금 (만원)").inputValue(), "");
  assert.equal(await dialog.getByLabel("주소 / 건물명 *").inputValue(), "");
  assert.equal(await dialog.getByLabel("층수").inputValue(), "-2");
  assert.equal(await dialog.getByLabel("면적 종류").inputValue(), "미상");
  await dialog.getByText(/광고 값은 건축물대장 공식 정보가 아닙니다/).waitFor();
  assert.deepEqual(await (await page.request.get("/api/listings")).json(), []);
  await dialog.getByLabel("매물 이름 *").fill("가상 브라우저 검증 매물");
  await dialog.getByLabel("주소 / 건물명 *").fill("가상 검증 주소");
  await dialog.getByLabel("월세 (만원)").fill("44");
  await dialog.getByRole("button", { name: "최종 확인으로 이동", exact: true }).click();
  assert.deepEqual(await (await page.request.get("/api/listings")).json(), []);
  await dialog.getByRole("button", { name: "확정 저장", exact: true }).click();
  await page.getByRole("button", { name: "가상 브라우저 검증 매물", exact: true }).waitFor();
  await page.reload();
  await page.getByRole("button", { name: "가상 브라우저 검증 매물", exact: true }).waitFor();
  const listings = await (await page.request.get("/api/listings")).json();
  assert.equal(listings.length, 1);
  const saved = listings[0];
  for (const [key, value] of Object.entries({ deposit: null, rent: 440000, fee: 0, area: 23.75, floor: -2, areaType: "미상" })) {
    assert.equal(saved[key], value, key);
  }
  assert.equal(Object.hasOwn(saved, "official"), false);
  const [ad] = await (await page.request.get("/api/advertisements")).json();
  assert.equal(ad.status, "REVIEWED");
  assert.equal(ad.extraction.monthlyRentWon, 420000);
  assert.equal(ad.reviewed.monthlyRentWon, 430000);
  assert.deepEqual(errors, []);
  await page.getByRole("button", { name: "가상 브라우저 검증 매물", exact: true }).click();
  await page.getByRole("dialog").waitFor();
  const screenshot = resolve(".runtime/sfr003-browser-flow.png");
  await mkdir(dirname(screenshot), { recursive: true });
  await page.screenshot({ path: screenshot, fullPage: true });
  const result = { scope: "Chromium UI -> Vite proxy -> normal Compose Spring Boot -> local extraction stub -> real MySQL",
    externalGeminiGenerationCalls: 0, browser: "Chromium", allTransitionsPassed: true,
    reviewedAndListingCorrectionsPersisted: true, nullZeroBasementPreserved: true,
    officialInformationNotInjected: true, listingReloadPassed: true, pageErrors: errors.length,
    registrationRoute: "#register", noListingBeforeFinalConfirmation: true,
    screenshot };
  await writeFile("docs/sfr003/browser-flow-result.json", JSON.stringify(result, null, 2) + "\n");
  console.log(JSON.stringify(result));
} finally {
  await browser.close();
}

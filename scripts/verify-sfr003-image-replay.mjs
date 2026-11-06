import assert from "node:assert/strict";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { resolve } from "node:path";
import { chromium } from "playwright-core";

// 외부 Gemini 대신 Python 실행기가 제공하는 저장 응답을 사용한다.
const record = JSON.parse(await readFile("docs/sfr003/image-001-result.json", "utf8"));
const resultPath = "docs/sfr003/image-001-replay-result.json";
const afterRestart = process.argv.includes("--after-restart");
const name = "서교동 이미지 재생 검증 매물";
const reviewed = { ...record.extraction, monthlyRentWon: 510000 };
const screenshot = resolve(`.runtime/sfr003-image-001/${afterRestart ? "after-restart" : "saved"}.png`);
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || "/usr/bin/chromium",
  headless: true, args: ["--no-sandbox"] });
try {
  const page = await browser.newPage({ baseURL: process.env.SFR_BROWSER_URL, viewport: { width: 1280, height: 1000 } });
  const errors = [];
  const externalRequests = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.route("**/*", async route => {
    const host = new URL(route.request().url()).hostname;
    if (!["127.0.0.1", "localhost"].includes(host)) {
      externalRequests.push(host);
      await route.abort();
    } else await route.continue();
  });
  const get = async path => {
    const response = await page.request.get(`/api/${path}`);
    assert.equal(response.status(), 200);
    return response.json();
  };
  await page.goto(process.env.SFR_BROWSER_URL);
  if (!afterRestart) {
    assert.deepEqual(await get("listings"), []);
    await page.getByRole("button", { name: "새 매물 등록", exact: true }).click();
    await page.getByRole("button", { name: "광고 글·사진에서 가져오기", exact: true }).click();
    await page.getByLabel("광고 이미지 (JPG·PNG, 최대 10MB)").setInputFiles(resolve(record.localSourcePath));
    assert.equal(await page.getByLabel("광고 글", { exact: true }).inputValue(), "");
    await page.getByRole("button", { name: "광고 원본 임시 저장", exact: true }).click();
    await page.getByText(/원본을 임시 저장했어요/).waitFor();
    let [ad] = await get("advertisements");
    assert.equal(ad.status, "DRAFT");
    assert.equal(ad.imageSize, (await readFile(record.localSourcePath)).length);
    assert.ok(!ad.originalText);
    await page.getByRole("button", { name: "원본 확정", exact: true }).click();
    await page.getByRole("button", { name: "Gemini 추출 시작", exact: true }).click();
    const rent = page.getByLabel("월세 (원)", { exact: true });
    await rent.waitFor();
    assert.equal(await rent.inputValue(), "500000");
    [ad] = await get("advertisements");
    assert.equal(ad.status, "EXTRACTION_READY");
    assert.deepEqual(ad.extraction, record.extraction);
    const cached = await page.request.post(`/api/advertisements/${ad.id}/extract`);
    assert.equal(cached.status(), 200);
    assert.deepEqual((await cached.json()).extraction, record.extraction);
    assert.equal(await page.getByRole("button", { name: "확인한 값으로 매물 등록", exact: true }).count(), 0);
    await rent.fill("510000"); // 수정 보존 검증용 값이며 실제 광고의 정답은 50만원이다.
    await page.getByRole("button", { name: "확인한 값 저장", exact: true }).click();
    await page.getByRole("button", { name: "확인한 값으로 매물 등록", exact: true }).click();
    const main = page.getByRole("main");
    for (const [label, value] of Object.entries({ "보증금 (만원)": "1000", "월세 (만원)": "51",
      "고정 관리비 (만원)": "5", "면적 (㎡)": "17.5", "층수": "1", "면적 종류": "미상",
      "주소 / 건물명 *": "마포구 서교동", "광고 원문": record.extraction.rawText })) {
      assert.equal(await main.getByLabel(label).inputValue(), value, label);
    }
    await main.getByText(/광고 값은 건축물대장 공식 정보가 아닙니다/).waitFor();
    assert.deepEqual(await get("listings"), []);
    await main.getByLabel("매물 이름 *", { exact: true }).fill(name);
    await main.getByLabel("월세 (만원)", { exact: true }).fill("52");
    await main.getByRole("button", { name: "최종 확인으로 이동", exact: true }).click();
    assert.deepEqual(await get("listings"), []);
    await main.getByRole("button", { name: "내용 수정", exact: true }).click();
    assert.equal(await main.getByLabel("월세 (만원)", { exact: true }).inputValue(), "52");
    await main.getByRole("button", { name: "최종 확인으로 이동", exact: true }).click();
    assert.deepEqual(await get("listings"), []);
    await main.getByRole("button", { name: "확정 저장", exact: true }).click();
  }
  await page.getByRole("button", { name, exact: true }).waitFor();
  await page.reload();
  await page.getByRole("button", { name, exact: true }).waitFor();
  const listings = await get("listings");
  assert.equal(listings.length, 1);
  const saved = listings[0];
  for (const [key, value] of Object.entries({ name, address: "마포구 서교동", deposit: 10000000,
    rent: 520000, fee: 50000, area: 17.5, floor: 1, areaType: "미상", original: record.extraction.rawText })) {
    assert.equal(saved[key], value, key);
  }
  assert.equal(Object.hasOwn(saved, "official"), false);
  const ads = await get("advertisements");
  assert.equal(ads.length, 1);
  const [ad] = ads;
  assert.equal(ad.status, "REVIEWED");
  assert.deepEqual(ad.extraction, record.extraction);
  assert.deepEqual(ad.reviewed, reviewed);
  assert.ok(!ad.originalText);
  const blocked = await page.request.post(`/api/advertisements/${ad.id}/extract`);
  assert.equal(blocked.status(), 409); // 확인 완료 광고는 재추출하지 않는다.
  await page.getByRole("button", { name, exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByText("공식 조회 미실시", { exact: true }).waitFor();
  assert.equal(await dialog.getByText("가상 공식정보", { exact: true }).count(), 0);
  await mkdir(resolve(".runtime/sfr003-image-001"), { recursive: true });
  await page.screenshot({ path: screenshot, fullPage: true });
  assert.deepEqual(errors, []);
  // 기존 외부 글꼴도 차단하여 브라우저가 외부 네트워크를 사용하지 않게 한다.
  assert.ok(externalRequests.every(host => host === "fonts.googleapis.com"));
  const result = afterRestart ? JSON.parse(await readFile(resultPath, "utf8")) : {
    status: "browser_passed_restart_pending", scope: "Saved real image-001 response replay -> Chromium -> Spring Boot -> real MySQL",
    verifiedAt: new Date().toISOString(), sampleId: record.sampleId, sourceSha256: record.sourceSha256,
    responseOrigin: "docs/sfr003/image-001-result.json:extraction", externalGeminiGenerationCalls: 0,
    browser: "Chromium", originalImageUploaded: true, allTransitionsPassed: true,
    noListingBeforeFinalConfirmation: true, officialInformationNotInjected: true,
    aiMonthlyRentWon: 500000, reviewedMonthlyRentWon: 510000, listingMonthlyRentWon: 520000,
    editValuesAreTestOnly: true, originalAdGroundTruthChanged: false,
    listingReloadPassed: true, pageErrors: errors.length, blockedExternalBrowserRequests: externalRequests,
    externalBrowserRequestsAllowed: 0,
    savedScreenshot: screenshot };
  if (afterRestart) Object.assign(result, { browserAfterRestartPassed: true, freshBrowserContextAfterRestart: true,
    afterRestartScreenshot: screenshot });
  await writeFile(resultPath, JSON.stringify(result, null, 2) + "\n");
  console.log(afterRestart ? "Fresh Chromium context after restart passed." : "Image response replay browser registration passed.");
} finally {
  await browser.close();
}

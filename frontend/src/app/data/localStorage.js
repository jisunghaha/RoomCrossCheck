import { seedRooms, seedReviews } from "./seed.js";
// 기존 저장 키를 유지하여 사용자의 브라우저 기록을 보존한다.
export const STORAGE_KEY = "oneroom.frontend.v1";
export function initialState() {
  return {
    rooms: structuredClone(seedRooms),
    reviews: structuredClone(seedReviews),
    visits: {},
  };
}
export function loadState(storage = window.localStorage) {
  try {
    const value = JSON.parse(storage.getItem(STORAGE_KEY));
    if (
      value &&
      Array.isArray(value.rooms) &&
      value.rooms.every(
        (r) => typeof r.id === "string" && Array.isArray(r.tags),
      ) &&
      Array.isArray(value.reviews) &&
      value.reviews.every((r) => r.scores && typeof r.scores === "object")
    )
      return { ...value, visits: value.visits || {} };
  } catch {
    /* 저장값을 읽을 수 없으면 브라우저 모드의 초기 예시를 사용한다. */
  }
  return initialState();
}

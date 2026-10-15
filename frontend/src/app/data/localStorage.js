import { seedRooms, seedReviews } from "./seed.js";
// Preserve the original prototype's key so existing browser records survive migration.
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
    /* Unavailable or invalid browser storage falls back to the demo. */
  }
  return initialState();
}

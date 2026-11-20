import { seedReviews } from "./seed.js";
import { loadState, STORAGE_KEY } from "./localStorage.js";
import { createApiClient } from "../../shared/lib/http.js";
import { isDemoReview } from "../../shared/lib/domain.js";

const emptyState = () => ({ rooms: [], reviews: [], visits: {} });
const invalidResponse = () => new Error("서버 응답 형식이 맞지 않습니다. API 계약을 확인해 주세요.");
function entity(value) {
  if (!value || typeof value.id !== "string") throw invalidResponse();
  return value;
}
function room(value) {
  entity(value);
  if (!Array.isArray(value.tags) || typeof value.building !== "string") throw invalidResponse();
  return value;
}
function review(value) {
  entity(value);
  if (!value.scores || typeof value.scores !== "object") throw invalidResponse();
  return value;
}
function visit(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw invalidResponse();
  return value;
}

// Backend adapter: IDs, ownership and timestamps are assigned by the server.
export function createApiRepository({ baseUrl = "/api", fetchImpl = globalThis.fetch, timeoutMs = 15000 } = {}) {
  async function request(path, method = "GET", body) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await fetchImpl(`${baseUrl.replace(/\/$/, "")}${path}`, {
        method, credentials: "include", signal: controller.signal,
        headers: { Accept: "application/json", ...(body === undefined ? {} : { "Content-Type": "application/json" }) },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
      if (!response.ok) {
        const messages = {
          401: "로그인이 필요해요.", 403: "접근 권한이 없어요.",
          404: "자료를 찾지 못했어요. 새로고침 후 확인해 주세요.",
          409: "다른 변경과 충돌했어요. 새로고침 후 확인해 주세요.",
          429: "요청이 많아요. 잠시 후 다시 시도해 주세요.",
        };
        throw new Error(messages[response.status] || `서버 요청에 실패했어요. (${response.status})`);
      }
      if (response.status === 204) return undefined;
      return await response.json();
    } catch (error) {
      if (error.name === "AbortError" || error instanceof TypeError) {
        throw new Error("서버 연결을 확인해 주세요. 저장 요청 중이었다면 새로고침으로 반영 여부를 확인한 뒤 다시 시도해 주세요.");
      }
      throw error;
    } finally { clearTimeout(timer); }
  }
  const idPath = id => encodeURIComponent(id);
  return {
    mode: "api", initialState: emptyState,
    async load() {
      const [rooms, reviews, visits] = await Promise.all([
        request("/rooms"), request("/reviews"), request("/visits"),
      ]);
      if (!Array.isArray(rooms) || !Array.isArray(reviews)) throw invalidResponse();
      visit(visits);
      return { rooms: rooms.map(room), reviews: reviews.map(review), visits };
    },
    async saveRoom(values, id) {
      return room(await request(id ? `/rooms/${idPath(id)}` : "/rooms", id ? "PUT" : "POST", values));
    },
    deleteRoom: id => request(`/rooms/${idPath(id)}`, "DELETE"),
    async addReview(values) { return review(await request("/reviews", "POST", values)); },
    deleteReview: id => request(`/reviews/${idPath(id)}`, "DELETE"),
    async saveVisit(id, value) { return visit(await request(`/rooms/${idPath(id)}/visit`, "PUT", value)); },
  };
}

export function createLocalRepository(storage = window.localStorage) {
  let state = loadState(storage);
  function commit(next, result) {
    // Only report success after durable browser storage succeeds.
    try { storage.setItem(STORAGE_KEY, JSON.stringify(next)); }
    catch { throw new Error("브라우저에 저장하지 못했어요. 저장 공간과 브라우저 설정을 확인해 주세요."); }
    state = next;
    return result;
  }
  return {
    mode: "local", initialState: () => state, load: async () => state,
    async saveChecklist(items) { return commit({ ...state, checklist: items }, items); },
    async saveComparison(ids) { return commit({ ...state, comparison: ids }, ids); },
    async saveRoom(values, id) {
      const old = state.rooms.find(r => r.id === id);
      const saved = {
        ...values, id: id || crypto.randomUUID(),
        building: old?.address === values.address ? old.building : crypto.randomUUID(),
        tags: old?.tags || ["직접 등록"], theme: old?.theme || "sage", official: null,
        createdAt: old?.createdAt || new Date().toISOString().slice(0, 10),
      };
      return commit({ ...state, rooms: old ? state.rooms.map(r => r.id === id ? saved : r) : [saved, ...state.rooms] }, saved);
    },
    async deleteRoom(id) {
      const visits = { ...state.visits }; delete visits[id];
      return commit({ ...state, visits, rooms: state.rooms.filter(r => r.id !== id) });
    },
    async setDemoReviews(buildings, enabled) {
      const existing = state.reviews.filter(review => !review.id.startsWith("review-demo:"));
      const generated = enabled ? [...new Set(buildings)].flatMap((building, buildingIndex) =>
        Array.from({ length: 5 }, (_, index) => {
          const template = seedReviews[index % seedReviews.length];
          return {
            ...template,
            id: `review-demo:${building}:${index}`,
            building,
            body: `[시연용 가상 후기] ${template.body}`,
            scores: Object.fromEntries(Object.entries(template.scores).map(([category, score], categoryIndex) =>
              [category, score === null ? null : 2 + ((buildingIndex + index + categoryIndex) % 4)])),
          };
        })) : [];
      const reviews = [...generated, ...existing];
      return commit({ ...state, reviews }, reviews);
    },
    async addReview(values) {
      const saved = { ...values, id: crypto.randomUUID(), demo: false, mine: true, createdAt: new Date().toISOString().slice(0, 10) };
      return commit({ ...state, reviews: [saved, ...state.reviews] }, { review: saved });
    },
    async deleteReview(id) {
      return commit({ ...state, reviews: state.reviews.filter(r => r.id !== id || r.demo) }, {});
    },
    async saveVisit(id, value) {
      return commit({ ...state, visits: { ...state.visits, [id]: value } }, value);
    },
  };
}

/**
 * 배포 서버용 저장소. 매물·광고·방문 기록·체크리스트·비교 선택·후기를 로그인 회원 기준으로 서버에 저장한다.
 * 시연용 가상 후기만 이 브라우저에 남기며 서버 후기와 섞어 표시한다.
 * @param {Object} [options] API 기본 경로, 요청 함수와 시연 후기를 둘 브라우저 저장소
 * @return {Object} RoomContext가 사용하는 저장소
 */
export function createListingRepository({ baseUrl = "/api", fetchImpl = globalThis.fetch, storage = window.localStorage } = {}) {
  const local = createLocalRepository(storage);
  const api = createApiClient({ baseUrl, fetchImpl });
  const asRoom = value => ({ ...value, tags: ["직접 등록"], theme: "sage", official: value.official ?? null, source: "server" });
  const demoReviews = () => local.initialState().reviews.filter(isDemoReview);
  const id = value => encodeURIComponent(value);
  async function reviewsFor(buildings) {
    const keys = [...new Set(buildings.filter(Boolean))].slice(0, 50);
    if (!keys.length) return { reviews: [], stats: {} };
    const value = await api(`/reviews?${keys.map(key => `building=${id(key)}`).join("&")}`);
    if (!Array.isArray(value?.reviews) || !value.stats || typeof value.stats !== "object") throw invalidResponse();
    return { reviews: value.reviews.map(review), stats: value.stats };
  }
  return {
    mode: "listing-api",
    searchOfficial: (listingId, query) => api(`/listings/${id(listingId)}/official/search`, { method: "POST", json: query }),
    async saveOfficial(listingId, selection) {
      return asRoom(await api(`/listings/${id(listingId)}/official`, { method: "PUT", json: selection }));
    },
    initialState: () => ({ rooms: [], reviews: [], visits: {}, reviewStats: {}, checklist: null, comparison: [] }),
    async load() {
      const rooms = await api("/listings");
      if (!Array.isArray(rooms)) throw invalidResponse();
      const loaded = rooms.map(value => asRoom(room({ ...value, tags: [] })));
      const [visits, checklist, comparison, server] = await Promise.all([
        api("/visits"), api("/visit-checklist"), api("/comparison"), reviewsFor(loaded.map(r => r.building)),
      ]);
      visit(visits);
      return {
        rooms: loaded, visits, reviews: [...demoReviews(), ...server.reviews], reviewStats: server.stats,
        checklist: Array.isArray(checklist?.items) ? checklist.items : null,
        comparison: Array.isArray(comparison?.listingIds) ? comparison.listingIds : [],
      };
    },
    async saveRoom(values, listingId) {
      const payload = Object.fromEntries(["name", "address", "deposit", "rent", "fee", "area", "areaType", "floor", "original"].map(key => [key, values[key] ?? null]));
      return asRoom(room({ ...await api(listingId ? `/listings/${id(listingId)}` : "/listings",
        { method: listingId ? "PATCH" : "POST", json: payload }), tags: [] }));
    },
    deleteRoom: listingId => api(`/listings/${id(listingId)}`, { method: "DELETE" }),
    setDemoReviews: (buildings, enabled) => local.setDemoReviews(buildings, enabled),
    /** 후기를 저장한 뒤 그 건물의 최신 통계를 함께 돌려준다. */
    async addReview(values) {
      const saved = review(await api("/reviews", { method: "POST", json: values }));
      const { stats } = await reviewsFor([saved.building]);
      return { review: saved, stats };
    },
    async deleteReview(reviewId, building) {
      await api(`/reviews/${id(reviewId)}`, { method: "DELETE" });
      return building ? { stats: (await reviewsFor([building])).stats } : {};
    },
    saveVisit: async (listingId, value) => visit(await api(`/listings/${id(listingId)}/visit`, { method: "PUT", json: {
      checks: Object.fromEntries(Object.entries(value.checks || {}).map(([key, checked]) => [key, Boolean(checked)])),
      note: value.note || "", visitedOn: value.visitedOn || null,
    } })),
    async saveChecklist(items) {
      const value = await api("/visit-checklist", { method: "PUT", json: { items } });
      return Array.isArray(value?.items) ? value.items : null;
    },
    async saveComparison(ids) {
      const value = await api("/comparison", { method: "PUT", json: { listingIds: ids } });
      return Array.isArray(value?.listingIds) ? value.listingIds : [];
    },
  };
}

export function createRepository() {
  const mode = import.meta.env.VITE_DATA_MODE || "local";
  if (mode === "api") return createApiRepository({ baseUrl: import.meta.env.VITE_API_BASE_URL || "/api" });
  if (mode === "listing-api") return createListingRepository({ baseUrl: import.meta.env.VITE_API_BASE_URL || "/api" });
  if (mode !== "local") throw new Error("VITE_DATA_MODE must be local, listing-api or api");
  return createLocalRepository();
}

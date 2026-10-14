import { loadState, STORAGE_KEY } from "../lib/storage.js";

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
    async addReview(values) {
      const saved = { ...values, id: crypto.randomUUID(), demo: false, createdAt: new Date().toISOString().slice(0, 10) };
      return commit({ ...state, reviews: [saved, ...state.reviews] }, saved);
    },
    async deleteReview(id) {
      return commit({ ...state, reviews: state.reviews.filter(r => r.id !== id || r.demo) });
    },
    async saveVisit(id, value) {
      return commit({ ...state, visits: { ...state.visits, [id]: value } }, value);
    },
  };
}

export function createRepository() {
  const mode = import.meta.env.VITE_DATA_MODE || "local";
  if (mode === "api") return createApiRepository({ baseUrl: import.meta.env.VITE_API_BASE_URL || "/api" });
  if (mode !== "local") throw new Error("VITE_DATA_MODE must be local or api");
  return createLocalRepository();
}

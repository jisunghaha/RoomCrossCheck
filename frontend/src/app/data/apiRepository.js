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


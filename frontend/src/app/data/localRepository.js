import { loadState, STORAGE_KEY } from "./localStorage.js";

export function createLocalRepository(storage = window.localStorage) {
  let state = loadState(storage);
  function commit(next, result) {
    // 브라우저 저장이 완료된 뒤에만 성공으로 처리한다.
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
        ...old, ...values, id: id || crypto.randomUUID(),
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


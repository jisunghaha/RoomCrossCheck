import { createContext, useContext, useEffect, useRef, useState } from "react";
import { createRepository } from "../services/repository.js";
const RoomContext = createContext(null);
export function RoomProvider({ children, repository: suppliedRepository }) {
  const [repository] = useState(() => suppliedRepository || createRepository());
  const [state, setState] = useState(() => repository.initialState());
  const [loading, setLoading] = useState(repository.mode === "api" || repository.mode === "listing-api");
  const [loadError, setLoadError] = useState("");
  const [saving, setSaving] = useState(false);
  const busy = useRef(false);
  const generation = useRef(0);
  async function reload() {
    const current = ++generation.current;
    setLoading(true);
    setLoadError("");
    try {
      const data = await repository.load();
      if (current === generation.current) setState(data);
    } catch (error) {
      if (current === generation.current) setLoadError(error.message);
    } finally {
      if (current === generation.current) setLoading(false);
    }
  }
  useEffect(() => {
    if (repository.mode === "api" || repository.mode === "listing-api") reload();
    return () => { generation.current++; };
  }, [repository]);
  async function persist(action, args, update, message) {
    if (busy.current || loading || loadError) return false;
    busy.current = true;
    setSaving(true);
    try {
      const result = await repository[action](...args);
      setState(s => update(s, result));
      notify(message);
      return true;
    } catch (error) {
      notify(error.message || "저장하지 못했어요. 다시 시도해 주세요.");
      return false;
    } finally {
      busy.current = false;
      setSaving(false);
    }
  }
  const [selected, setSelected] = useState([]);
  const [toast, setToast] = useState("");
  const timer = useRef();
  function notify(message) {
    clearTimeout(timer.current);
    setToast(message);
    timer.current = setTimeout(() => setToast(""), 3500);
  }
  useEffect(() => () => clearTimeout(timer.current), []);
  function saveRoom(values, id) {
    return persist("saveRoom", [values, id], (s, room) => ({
      ...s, rooms: id ? s.rooms.map(r => r.id === id ? room : r) : [room, ...s.rooms],
    }), "매물을 저장했어요.");
  }
  async function deleteRoom(id) {
    const success = await persist("deleteRoom", [id], s => {
      const visits = { ...s.visits };
      delete visits[id];
      return { ...s, visits, rooms: s.rooms.filter(r => r.id !== id) };
    }, "매물과 개인 방문 기록을 삭제했어요.");
    if (success) setSelected(s => s.filter(x => x !== id));
    return success;
  }
  function toggleSelected(id) {
    if (!selected.includes(id) && selected.length === 3) {
      notify("매물은 최대 3개까지 비교할 수 있어요.");
      return;
    }
    setSelected((s) =>
      s.includes(id) ? s.filter((x) => x !== id) : [...s, id],
    );
  }
  function addReview(values) {
    return persist("addReview", [values], (s, review) => ({
      ...s, reviews: [review, ...s.reviews],
    }), "후기를 저장하고 통계를 갱신했어요.");
  }
  function deleteReview(id) {
    return persist("deleteReview", [id], s => ({
      ...s, reviews: s.reviews.filter(r => r.id !== id || r.demo),
    }), "후기를 삭제했어요.");
  }
  function saveVisit(id, visit) {
    return persist("saveVisit", [id, visit], (s, saved) => ({
      ...s, visits: { ...s.visits, [id]: saved },
    }), "방문 기록을 저장했어요.");
  }
  return (
    <RoomContext.Provider
      value={{
        ...state,
        loading, loadError, saving, reload, dataMode: repository.mode,
        selected,
        toast,
        notify,
        saveRoom,
        deleteRoom,
        toggleSelected,
        clearSelected: () => setSelected([]),
        addReview,
        deleteReview,
        saveVisit,
      }}
    >
      {children}
    </RoomContext.Provider>
  );
}
export function useRooms() {
  const value = useContext(RoomContext);
  if (!value) throw new Error("RoomProvider is required");
  return value;
}

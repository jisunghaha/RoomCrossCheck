import { createContext, useContext, useEffect, useRef, useState } from "react";
import { loadState, STORAGE_KEY } from "../lib/storage.js";
const RoomContext = createContext(null);
export function RoomProvider({ children }) {
  const [state, setState] = useState(loadState);
  const [selected, setSelected] = useState([]);
  const [toast, setToast] = useState("");
  const timer = useRef();
  function notify(message) {
    clearTimeout(timer.current);
    setToast(message);
    timer.current = setTimeout(() => setToast(""), 3500);
  }
  useEffect(() => () => clearTimeout(timer.current), []);
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      notify(
        "브라우저에 저장하지 못했어요. 새로고침하면 변경이 사라질 수 있습니다.",
      );
    }
  }, [state]);
  function saveRoom(values, id) {
    const old = state.rooms.find((r) => r.id === id);
    const room = {
      ...values,
      id: id || crypto.randomUUID(),
      building:
        old?.address === values.address ? old.building : crypto.randomUUID(),
      tags: old?.tags || ["직접 등록"],
      theme: old?.theme || "sage",
      official: null,
      createdAt: old?.createdAt || new Date().toISOString().slice(0, 10),
    };
    setState((s) => ({
      ...s,
      rooms: old
        ? s.rooms.map((r) => (r.id === id ? room : r))
        : [room, ...s.rooms],
    }));
    notify("매물을 저장했어요.");
  }
  function deleteRoom(id) {
    setState((s) => {
      const visits = { ...s.visits };
      delete visits[id];
      return { ...s, visits, rooms: s.rooms.filter((r) => r.id !== id) };
    });
    setSelected((s) => s.filter((x) => x !== id));
    notify("매물과 개인 방문 기록을 삭제했어요.");
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
    setState((s) => ({
      ...s,
      reviews: [
        {
          ...values,
          id: crypto.randomUUID(),
          demo: false,
          createdAt: new Date().toISOString().slice(0, 10),
        },
        ...s.reviews,
      ],
    }));
    notify("후기를 저장하고 통계를 갱신했어요.");
  }
  function deleteReview(id) {
    setState((s) => ({
      ...s,
      reviews: s.reviews.filter((r) => r.id !== id || r.demo),
    }));
    notify("후기를 삭제했어요.");
  }
  function saveVisit(id, visit) {
    setState((s) => ({ ...s, visits: { ...s.visits, [id]: visit } }));
    notify("방문 기록을 저장했어요.");
  }
  return (
    <RoomContext.Provider
      value={{
        ...state,
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

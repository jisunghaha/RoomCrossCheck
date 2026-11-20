import { createContext, useContext, useEffect, useRef, useState } from "react";
import { createRepository } from "../data/repository.js";
import { isDemoReview } from "../../shared/lib/domain.js";
const RoomContext = createContext(null);
const serverModes = ["api", "listing-api"];
export function RoomProvider({ children, repository: suppliedRepository }) {
  const [repository] = useState(() => suppliedRepository || createRepository());
  const [state, setState] = useState(() => repository.initialState());
  const [loading, setLoading] = useState(serverModes.includes(repository.mode));
  const [loadError, setLoadError] = useState("");
  const [authRequired, setAuthRequired] = useState(false);
  const [saving, setSaving] = useState(false);
  const [selected, setSelected] = useState(() => state.comparison || []);
  const busy = useRef(false);
  const generation = useRef(0);
  async function reload() {
    const current = ++generation.current;
    setLoading(true);
    setLoadError("");
    setAuthRequired(false);
    try {
      const data = await repository.load();
      if (current === generation.current) {
        setState(data);
        setSelected(data.comparison || []);
      }
    } catch (error) {
      if (current !== generation.current) return;
      // 비로그인은 오류가 아니라 로그인 안내 상태로 보여 주고, 이전 회원의 자료를 화면에서 지운다.
      if (error.status === 401) {
        setAuthRequired(true);
        setState(repository.initialState());
        setSelected([]);
      } else setLoadError(error.message);
    } finally {
      if (current === generation.current) setLoading(false);
    }
  }
  useEffect(() => {
    if (serverModes.includes(repository.mode)) reload();
    return () => { generation.current++; };
  }, [repository]);
  async function persist(action, args, update, message) {
    if (busy.current || loading || loadError || authRequired) return false;
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
  const [toast, setToast] = useState("");
  const timer = useRef();
  function notify(message) {
    clearTimeout(timer.current);
    setToast(message);
    timer.current = setTimeout(() => setToast(""), 3500);
  }
  useEffect(() => () => clearTimeout(timer.current), []);
  const mergeStats = (s, stats) => (stats ? { ...(s.reviewStats || {}), ...stats } : s.reviewStats);
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
  /** 비교 대상 선택은 화면에 바로 반영하고 저장소에 기록한다. 저장에 실패하면 이전 선택으로 되돌린다. */
  function toggleSelected(id) {
    if (!selected.includes(id) && selected.length === 3) {
      notify("매물은 최대 3개까지 비교할 수 있어요.");
      return;
    }
    const previous = selected;
    const next = selected.includes(id) ? selected.filter(x => x !== id) : [...selected, id];
    setSelected(next);
    if (!repository.saveComparison || authRequired) return;
    repository.saveComparison(next).catch(error => {
      setSelected(previous);
      notify(error.message || "비교 선택을 저장하지 못했어요.");
    });
  }
  function clearSelected() {
    const previous = selected;
    setSelected([]);
    repository.saveComparison?.([]).catch(error => {
      setSelected(previous);
      notify(error.message || "비교 선택을 저장하지 못했어요.");
    });
  }
  function addReview(values) {
    return persist("addReview", [values], (s, result) => ({
      ...s, reviews: [result?.review ?? result, ...s.reviews], reviewStats: mergeStats(s, result?.stats),
    }), "후기를 저장하고 통계를 갱신했어요.");
  }
  function deleteReview(id) {
    const building = state.reviews.find(r => r.id === id)?.building;
    return persist("deleteReview", [id, building], (s, result) => ({
      ...s, reviews: s.reviews.filter(r => r.id !== id || r.demo), reviewStats: mergeStats(s, result?.stats),
    }), "후기를 삭제했어요.");
  }
  function saveVisit(id, visit) {
    return persist("saveVisit", [id, visit], (s, saved) => ({
      ...s, visits: { ...s.visits, [id]: saved },
    }), "방문 기록을 저장했어요.");
  }
  function saveChecklist(items) {
    return persist("saveChecklist", [items], (s, saved) => ({ ...s, checklist: saved }),
      items ? "체크리스트 구성을 저장했어요." : "기본 체크리스트로 되돌렸어요.");
  }
  const roomIds = new Set(state.rooms.map(r => r.id));
  return (
    <RoomContext.Provider
      value={{
        ...state,
        reviewStats: state.reviewStats || {},
        checklist: state.checklist || null,
        loading, loadError, authRequired, saving, reload, dataMode: repository.mode,
        selected: selected.filter(id => roomIds.has(id)),
        toast,
        notify,
        saveRoom,
        searchOfficial: (id, query) => repository.searchOfficial(id, query),
        saveOfficial: (id, selection) => persist("saveOfficial", [id, selection], (s, room) => ({ ...s, rooms: s.rooms.map(r => r.id === id ? room : r) }), "공식 정보를 저장하고 등록 정보와 비교했어요."),
        deleteRoom,
        toggleSelected,
        clearSelected,
        // 시연 후기 결과에서는 시연 후기만 받아 오고, 서버·브라우저의 실제 후기는 현재 화면 값을 유지한다.
        setDemoReviews: enabled => persist("setDemoReviews", [state.rooms.map(room => room.building), enabled],
          (s, reviews) => ({ ...s, reviews: [...reviews.filter(isDemoReview), ...s.reviews.filter(r => !isDemoReview(r))] }),
          enabled ? "건물별 시연 후기 5건을 생성했어요." : "생성한 시연 후기를 삭제했어요."),
        addReview,
        deleteReview,
        saveVisit,
        saveChecklist,
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

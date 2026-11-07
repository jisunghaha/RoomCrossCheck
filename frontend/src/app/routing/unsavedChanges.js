import { useEffect } from "react";

const eventName = "roomcrosscheck:unsaved-changes";
export function confirmLeave() {
  const check = new Event(eventName, { cancelable: true });
  return window.dispatchEvent(check) || window.confirm("저장하지 않은 확인값이 있습니다. 변경 내용을 버리고 이동할까요?");
}

export function useUnsavedChanges(dirty) {
  useEffect(() => {
    if (!dirty) return;
    const protect = event => { event.preventDefault(); };
    const unload = event => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener(eventName, protect);
    window.addEventListener("beforeunload", unload);
    return () => {
      window.removeEventListener(eventName, protect);
      window.removeEventListener("beforeunload", unload);
    };
  }, [dirty]);
}

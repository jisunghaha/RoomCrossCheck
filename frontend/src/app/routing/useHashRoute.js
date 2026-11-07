import { useEffect, useRef, useState } from "react";
import { confirmLeave } from "./unsavedChanges.js";
const routes = ["rooms", "register", "compare", "reviews", "visits"];
const current = () =>
  routes.includes(location.hash.slice(1)) ? location.hash.slice(1) : "rooms";
export function useHashRoute() {
  const [page, setPage] = useState(current);
  const active = useRef(page);
  const navigate = (next) => {
    if (next === active.current || !routes.includes(next)) return;
    if (!confirmLeave()) return;
    active.current = next;
    location.hash = next;
    setPage(next);
  };
  useEffect(() => {
    const onChange = () => {
      const next = current();
      if (next === active.current) return;
      if (!confirmLeave()) {
        history.replaceState(null, "", `#${active.current}`);
        return;
      }
      active.current = next;
      setPage(next);
    };
    const onClick = event => {
      if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      const anchor = event.target.closest?.("a[href]");
      if (!anchor || anchor.target || anchor.hasAttribute("download")) return;
      const url = new URL(anchor.href, location.href);
      if (url.origin !== location.origin || url.pathname !== location.pathname || url.search !== location.search) return;
      const next = url.hash.slice(1);
      if (!routes.includes(next)) return;
      event.preventDefault();
      navigate(next);
    };
    window.addEventListener("hashchange", onChange);
    document.addEventListener("click", onClick);
    return () => {
      window.removeEventListener("hashchange", onChange);
      document.removeEventListener("click", onClick);
    };
  }, []);
  return [page, navigate];
}

import { useEffect, useState } from "react";
const routes = ["rooms", "register", "compare", "reviews", "visits"];
const current = () =>
  routes.includes(location.hash.slice(1)) ? location.hash.slice(1) : "rooms";
export function useHashRoute() {
  const [page, setPage] = useState(current);
  useEffect(() => {
    const onChange = () => setPage(current());
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  const navigate = (next) => {
    location.hash = next;
    setPage(next);
  };
  return [page, navigate];
}

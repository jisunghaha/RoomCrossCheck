import { useEffect, useState } from "react";
const routes = ["home", "rooms", "register", "compare", "visits", "login", "signup", "community"];
/** 기존 후기 주소를 커뮤니티 탭으로 연결하고 알 수 없는 주소는 첫 화면으로 보낸다. */
function resolveRoute(value) {
  if (value === "reviews") return "community/reviews";
  if (routes.includes(value) || /^community\/(notice|tips|reviews)$/.test(value) ||
      /^post\/[a-z0-9-]+$/.test(value) || /^register\/[a-zA-Z0-9-]+$/.test(value)) return value;
  return "home";
}
const current = () => resolveRoute(location.hash.slice(1));
export function useHashRoute() {
  const [page, setPage] = useState(current);
  useEffect(() => {
    const onChange = () => setPage(current());
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  const navigate = (next) => {
    const route = resolveRoute(next);
    location.hash = route;
    setPage(route);
  };
  return [page, navigate];
}

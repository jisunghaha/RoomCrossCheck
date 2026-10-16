import { categories } from "./categories.js";
export function statistics(reviews, building) {
  let rs = reviews.filter((x) => x.building === building);
  return {
    count: rs.length,
    items: categories.map((c) => {
      let scores = rs
        .map((x) => x.scores[c])
        .filter((x) => Number.isInteger(x) && x >= 1 && x <= 5);
      return {
        label: c,
        count: scores.length,
        value: scores.length
          ? scores.reduce((a, b) => a + b, 0) / scores.length
          : null,
      };
    }),
  };
}

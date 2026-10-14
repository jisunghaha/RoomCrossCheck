export function validateComparison(ids) {
  return ids.length >= 2 && ids.length <= 3 && new Set(ids).size === ids.length;
}

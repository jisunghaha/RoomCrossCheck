export const fixedCost = (r) =>
  r.rent == null || r.fee == null ? null : r.rent + r.fee;

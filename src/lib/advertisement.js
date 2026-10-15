// Only persisted, user-reviewed ad values can start a new listing.
// Address hints and AI results are deliberately not fallback sources.
export function reviewedAdvertisementValues(draft) {
  if (draft?.status !== "REVIEWED" || !draft.reviewed) return null;
  const value = draft.reviewed;
  return {
    name: "", address: value.address ?? "",
    deposit: value.depositWon ?? null, rent: value.monthlyRentWon ?? null,
    fee: value.maintenanceFeeWon ?? null, area: value.areaSqm ?? null,
    floor: value.floor ?? null, areaType: "미상",
    original: draft.originalText || value.rawText || "",
  };
}

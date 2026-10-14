import { createApiRepository } from "./apiRepository.js";
import { createLocalRepository } from "./localRepository.js";

// Composition root: both adapters implement the same repository operations.
export function createRepository() {
  const mode = import.meta.env.VITE_DATA_MODE || "local";
  if (mode === "api") return createApiRepository({ baseUrl: import.meta.env.VITE_API_BASE_URL || "/api" });
  if (mode !== "local") throw new Error("VITE_DATA_MODE must be local or api");
  return createLocalRepository();
}

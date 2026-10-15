import { defineConfig, loadEnv } from "vite";
import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
export default defineConfig(({ mode }) => {
  const frontendDir = fileURLToPath(new URL(".", import.meta.url));
  const repositoryDir = fileURLToPath(new URL("..", import.meta.url));
  const publicEnv = { ...loadEnv(mode, repositoryDir, "VITE_"), ...loadEnv(mode, frontendDir, "VITE_") };
  return {
  plugins: [react()],
  define: {
    "import.meta.env.VITE_DATA_MODE": JSON.stringify(publicEnv.VITE_DATA_MODE || "local"),
    "import.meta.env.VITE_API_BASE_URL": JSON.stringify(publicEnv.VITE_API_BASE_URL || "/api"),
  },
  server: { port: 4173, proxy: { "/api": process.env.DEV_API_TARGET || "http://127.0.0.1:8080" } },
  test: { environment: "jsdom", setupFiles: "./tests/setup.js", css: false },
  };
});

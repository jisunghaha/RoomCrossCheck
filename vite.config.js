import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  server: { port: 4173, proxy: { "/api": process.env.DEV_API_TARGET || "http://127.0.0.1:8080" } },
  test: { environment: "jsdom", setupFiles: "./tests/setup.js", css: false },
});

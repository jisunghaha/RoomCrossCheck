import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  // npm run dev로 여는 화면의 포트를 고정한다. 사용 중이면 다른 포트로 바꾸지 않고 알린다.
  // 실행 스크립트의 --host 127.0.0.1 설정으로 이 PC에서만 접근할 수 있다.
  server: { port: 4173, strictPort: true },
  test: { environment: "jsdom", setupFiles: "./tests/setup.js", css: false },
});

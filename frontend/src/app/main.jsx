import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import { RoomProvider } from "./state/RoomContext.jsx";
import "./styles.css";
import "./report-phase-one.css";
createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <RoomProvider>
      <App />
    </RoomProvider>
  </React.StrictMode>,
);

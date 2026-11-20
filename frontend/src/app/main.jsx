import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import ErrorBoundary from "./ErrorBoundary.jsx";
import { RoomProvider } from "./state/RoomContext.jsx";
import "./styles.css";
createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <ErrorBoundary>
      <RoomProvider>
        <App />
      </RoomProvider>
    </ErrorBoundary>
  </React.StrictMode>,
);

import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import basicSsl from "@vitejs/plugin-basic-ssl";

// `npm run dev`        http://localhost:5173 (the camera works on localhost)
// `npm run dev:phone`  https://<this computer's IP>:5173 with a self-signed
//                      certificate, because phones only allow the camera over HTTPS
//
// /api is passed through to the backend, so a phone only needs to reach this
// one address. The Origin header is dropped because the request now comes
// from the dev server itself, not the browser's page.
export default defineConfig(({ mode }) => ({
  plugins: [react(), mode === "phone" && basicSsl()].filter(Boolean),
  server: {
    port: 5173,
    host: true,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        configure: proxy => proxy.on("proxyReq", request => request.removeHeader("origin")),
      },
    },
  },
}));

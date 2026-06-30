import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Relative base so the bundle works inside the OBR iframe / GitHub Pages subpath.
export default defineConfig({
  plugins: [react()],
  base: './',
  server: {
    port: 5173,
    // Proxy API calls to the Spring Boot server in dev so the frontend can use
    // relative `/api/...` paths (no CORS, no hardcoded host).
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});

import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev:  npm run dev      -> http://localhost:5173 (API calls are proxied to Spring Boot on :8080)
// Prod: npm run build    -> writes the app into ../src/main/resources/static, served by Spring Boot
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/uploads': 'http://localhost:8080'
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    chunkSizeWarningLimit: 900
  }
});

import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: { environment: 'jsdom', globals: true, setupFiles: './src/test/setup.js', css: false },
  server: {
    port: 5173,
    // Same-origin in dev: the browser talks to :5173 and Vite forwards /api to Spring Boot,
    // so the HttpOnly cookies and the CSRF cookie behave exactly as they will behind a production proxy.
    proxy: { '/api': { target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080', changeOrigin: false } },
  },
});

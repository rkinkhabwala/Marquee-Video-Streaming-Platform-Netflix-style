/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The API serves /api, /stream (HLS) and /media (artwork); the dev server proxies them so the
// browser sees one origin and no CORS setup is needed.
const apiTarget = process.env.API_URL ?? 'http://localhost:8080';
const proxy = Object.fromEntries(['/api', '/stream', '/media'].map((path) => [path, { target: apiTarget, changeOrigin: true }]));

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy },
  preview: { port: 4173, proxy },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
});

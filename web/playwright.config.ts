import { defineConfig } from '@playwright/test';

// E2E_BASE_URL targets an already-running app (e.g. the Compose web container on :3000);
// otherwise the Vite dev server is started.
const externalBaseUrl = process.env.E2E_BASE_URL;

/**
 * End-to-end tests against the running Compose stack (API on :8080) through the Vite dev server.
 * Uses installed Google Chrome: Playwright's bundled Chromium cannot decode H.264/AAC.
 */
export default defineConfig({
  testDir: 'e2e',
  globalSetup: './e2e/global-setup.ts',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: externalBaseUrl ?? 'http://localhost:5173',
    channel: 'chrome',
    viewport: { width: 1280, height: 800 },
    trace: 'retain-on-failure',
    // Lets the player start with sound without a user gesture.
    launchOptions: { args: ['--autoplay-policy=no-user-gesture-required'] },
  },
  webServer: externalBaseUrl
    ? undefined
    : {
        command: 'npm run dev -- --strictPort',
        url: 'http://localhost:5173',
        reuseExistingServer: true,
        timeout: 60_000,
      },
});

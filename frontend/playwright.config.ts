import { defineConfig } from '@playwright/test';
import { existsSync } from 'node:fs';
const channel =
  process.env['FASTCOLOC_BROWSER_CHANNEL'] ??
  (existsSync('C:/Program Files/Google/Chrome/Application/chrome.exe') ? 'chrome' : undefined);
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 180000,
  expect: { timeout: 30000 },
  reporter: [['list'], ['json', { outputFile: 'test-results/results.json' }]],
  use: {
    channel,
    actionTimeout: 20000,
    baseURL: 'http://localhost:4200',
    headless: true,
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  webServer: {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 60000,
  },
});

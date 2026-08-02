import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';

const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:4200';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: [['list'], ['html', { outputFolder: 'test-results/playwright-report', open: 'never' }]],
  outputDir: 'test-results/playwright-artifacts',
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  },
  projects: [
    { name: 'specialist-setup', testMatch: /specialist\.setup\.ts/, use: { ...devices['Desktop Chrome'] } },
    { name: 'specialist-body-map-desktop', testMatch: /specialist-body-map\.spec\.ts/, dependencies: ['specialist-setup'], use: { ...devices['Desktop Chrome'], storageState: path.join(__dirname, '.auth/specialist.json'), viewport: { width: 1440, height: 900 } } },
    { name: 'specialist-body-map-390', testMatch: /specialist-body-map\.spec\.ts/, dependencies: ['specialist-setup'], use: { ...devices['Desktop Chrome'], storageState: path.join(__dirname, '.auth/specialist.json'), viewport: { width: 390, height: 844 } } },
    { name: 'specialist-body-map-320', testMatch: /specialist-body-map\.spec\.ts/, dependencies: ['specialist-setup'], use: { ...devices['Desktop Chrome'], storageState: path.join(__dirname, '.auth/specialist.json'), viewport: { width: 320, height: 700 } } },
    { name: 'chromium', testIgnore: /specialist-body-map\.spec\.ts/, use: { ...devices['Desktop Chrome'] } }
  ]
});

import { test } from '@playwright/test';
import specialistGlobalSetup from './specialist.global-setup';

test('creates the real OIDC specialist session and body-map fixture', async () => {
  await specialistGlobalSetup();
});

import { expect, type Page } from '@playwright/test';

export type Credentials = { username: string; password: string };

export const participantCredentials = (): Credentials => requiredCredentials('E2E_PARTICIPANT');

export const optionalCredentials = (prefix: 'E2E_PARTICIPANT' | 'E2E_SPECIALIST' | 'E2E_RESUME_PARTICIPANT'): Credentials | undefined => {
  const username = process.env[`${prefix}_USERNAME`];
  const password = process.env[`${prefix}_PASSWORD`];
  if (!username && !password) return undefined;
  if (!username || !password) throw new Error(`${prefix}_USERNAME and ${prefix}_PASSWORD must be set together.`);
  return { username, password };
};

export async function loginWithOidc(page: Page, credentials: Credentials): Promise<void> {
  await page.goto('/login');
  await page.getByRole('button', { name: 'Przejdź do logowania' }).click();
  await page.getByLabel(/username|nazwa użytkownika/i).fill(credentials.username);
  await page.getByLabel(/^password$|hasło/i).fill(credentials.password);
  await page.getByRole('button', { name: /sign in|zaloguj/i }).click();
  await expect(page).toHaveURL(/^(?!.*\/login)/);
}

export async function expectReadyParticipant(page: Page): Promise<void> {
  await page.goto('/onboarding');
  await expect(page.getByRole('heading', { name: 'Profil gotowy' })).toBeVisible();
}

function requiredCredentials(prefix: 'E2E_PARTICIPANT'): Credentials {
  const credentials = optionalCredentials(prefix);
  if (!credentials) throw new Error(`${prefix}_USERNAME and ${prefix}_PASSWORD are required for real-OIDC E2E tests.`);
  return credentials;
}

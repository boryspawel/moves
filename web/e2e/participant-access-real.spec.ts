import { test, expect, chromium } from '@playwright/test';
import { loginWithOidc, type Credentials } from './fixtures';

const required = (name: string) => {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required by the isolated participant-access run.`);
  return value;
};

async function completeOnboarding(page: import('@playwright/test').Page, type: 'participant' | 'specialist') {
  await page.goto('/onboarding');
  if (await page.getByRole('heading', { name: 'Profil gotowy' }).isVisible().catch(() => false)) return;
  await page.getByRole('radio', { name: type === 'participant' ? 'Uczestnik' : 'Specjalista' }).click();
  await page.getByRole('button', { name: 'Kontynuuj' }).click();
  await page.getByText('Akceptuję warunki korzystania z usługi.').click();
  await page.getByText('Potwierdzam zapoznanie się z informacją o prywatności.').click();
  await page.getByRole('button', { name: 'Potwierdzam' }).click();
  await page.getByLabel('Nazwa wyświetlana').fill(type === 'participant' ? 'P3 participant' : 'P3 specialist');
  if (type === 'specialist') { await page.getByLabel('Rodzaj specjalisty').click(); await page.getByRole('option', { name: 'Trener' }).click(); }
  await page.getByLabel('Strefa czasowa').click(); await page.getByRole('option', { name: 'Europe/Warsaw' }).click();
  await page.getByRole('button', { name: 'Zapisz profil' }).click();
  await page.getByRole('button', { name: 'Zapisz dostępność' }).click();
  await expect(page.getByRole('heading', { name: 'Profil gotowy' })).toBeVisible();
}

test.use({ trace: 'off', video: 'off', screenshot: 'off' });
test('real invitation email and deliberate participant claim retain the original record', async ({ page, context, request }) => {
  const specialist: Credentials = { username: required('P3_SPECIALIST_USERNAME'), password: required('P3_SPECIALIST_PASSWORD') };
  const participant: Credentials = { username: required('P3_PARTICIPANT_USERNAME'), password: required('P3_PARTICIPANT_PASSWORD') };
  let authorization = '';
  page.on('request', value => {
    if (value.url().includes('/api/') && value.headers().authorization?.startsWith('Bearer ')) authorization ||= value.headers().authorization!;
  });
  await loginWithOidc(page, specialist);
  await completeOnboarding(page, 'specialist');
  await page.goto('/specialist/clients');
  await page.getByRole('button', { name: 'Dodaj klienta' }).first().click();
  await page.getByLabel('Imię i nazwisko lub nazwa kartoteki').fill('P3 original participant');
  await page.getByLabel('E-mail (opcjonalnie)').fill(required('P3_PARTICIPANT_EMAIL'));
  await page.getByRole('button', { name: 'Dodaj klienta' }).last().click();
  await expect(page).toHaveURL(/\/specialist\/clients\/[0-9a-f-]+$/);
  const participantId = page.url().split('/').at(-1)!;
  await expect.poll(() => authorization).not.toBe('');
  const goal = await request.fetch(`${required('E2E_API_ORIGIN')}/api/v1/specialist/clients/${participantId}/goals?actingContext=TRAINER`, {
    method: 'POST', headers: { Authorization: authorization, 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
    data: { perspective: 'PERFORMANCE', title: 'Original claim goal', priority: 10, outcomes: [] }
  });
  expect(goal.ok()).toBeTruthy();
  await page.getByLabel('Dostęp uczestnika').getByLabel('Adres e-mail').fill(required('P3_PARTICIPANT_EMAIL'));
  await page.getByRole('button', { name: 'Wyślij zaproszenie' }).click();
  const mailpit = required('P3_MAILPIT_ORIGIN');
  await expect.poll(async () => {
    const response = await request.get(`${mailpit}/api/v1/messages`); const json = await response.json() as { messages?: Array<{ ID: string }> };
    return json.messages?.[0]?.ID ?? '';
  }, { timeout: 20_000 }).not.toBe('');
  const messages = await request.get(`${mailpit}/api/v1/messages`);
  const messageId = ((await messages.json()) as { messages: Array<{ ID: string }> }).messages[0].ID;
  const source = await (await request.get(`${mailpit}/api/v1/message/${messageId}`)).text();
  const link = source.match(/http:\/\/localhost:\d+\/participant\/claim#token=[^\s"<]+/)?.[0];
  expect(link).toBeTruthy();
  await context.clearCookies();
  await page.goto(link!);
  await page.getByRole('button', { name: 'Zaloguj się' }).click();
  await page.getByLabel(/username|nazwa użytkownika/i).fill(participant.username);
  await page.getByLabel(/^password$|hasło/i).fill(participant.password);
  await page.getByRole('button', { name: /sign in|zaloguj/i }).click();
  if (await page.getByRole('button', { name: 'Dokończ konfigurację' }).isVisible().catch(() => false)) {
    await page.getByRole('button', { name: 'Dokończ konfigurację' }).click();
    await completeOnboarding(page, 'participant');
    await page.goto('/participant/claim');
  }
  await expect(page.getByRole('button', { name: 'Potwierdź dostęp' })).toBeVisible();
  await page.getByRole('button', { name: 'Potwierdź dostęp' }).click();
  await expect(page).toHaveURL(/\/sessions$/);
  await page.goto('/my-data');
  await expect(page.getByText('Original claim goal')).toBeVisible();
  await context.clearCookies();
});

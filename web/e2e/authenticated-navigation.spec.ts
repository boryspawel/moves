import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { expectReadyParticipant, loginWithOidc, optionalCredentials, participantCredentials } from './fixtures';

test.describe('participant navigation through real OIDC', () => {
  test('reaches READY onboarding, keeps the authenticated session after refresh, and navigates to sessions', async ({ page }) => {
    await loginWithOidc(page, participantCredentials());
    await expectReadyParticipant(page);

    await page.reload();
    await expect(page.getByRole('heading', { name: 'Profil gotowy' })).toBeVisible();

    await page.getByRole('link', { name: 'Sesje' }).click();
    await expect(page.getByRole('heading', { name: 'Twoja sesja na dziś' })).toBeVisible();
  });

  test('sessions view supports keyboard navigation and has no detectable axe violations', async ({ page }) => {
    await loginWithOidc(page, participantCredentials());
    await page.goto('/sessions');
    await expect(page.getByRole('heading', { name: 'Twoja sesja na dziś' })).toBeVisible();

    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toBeVisible();
    const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
    expect(accessibilityScanResults.violations).toEqual([]);
  });

  for (const width of [390, 320]) {
    test(`sessions remains usable at ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 844 });
      await loginWithOidc(page, participantCredentials());
      await page.goto('/sessions');
      await expect(page.getByRole('heading', { name: 'Twoja sesja na dziś' })).toBeVisible();
      await page.screenshot({ path: test.info().outputPath(`sessions-${width}.png`), fullPage: true, animations: 'disabled' });
    });
  }

  test('shows and retries a real backend error without request interception', async ({ page }) => {
    await loginWithOidc(page, participantCredentials());
    // An invalid version id is handled by the deployed backend; this test does not mock or intercept it.
    await page.goto('/catalog/not-a-valid-version-id');
    await expect(page.getByRole('heading', { name: 'Nie udało się pobrać ćwiczenia' })).toBeVisible();
    await page.getByRole('button', { name: 'Ponów' }).click();
    await expect(page.getByRole('heading', { name: 'Nie udało się pobrać ćwiczenia' })).toBeVisible();
  });
});

const specialist = optionalCredentials('E2E_SPECIALIST');
test('specialist worklist is available to an independently configured specialist account', async ({ page }) => {
  test.skip(!specialist, 'E2E_SPECIALIST_USERNAME/PASSWORD were not configured for the optional independent specialist account.');
  await loginWithOidc(page, specialist!);
  await page.goto('/specialist-alerts');
  await expect(page.getByRole('heading', { name: 'Elementy wymagające uwagi' })).toBeVisible();
});

const resumeParticipant = optionalCredentials('E2E_RESUME_PARTICIPANT');
test('refresh resumes an independently prepared participant attempt', async ({ page }) => {
  test.skip(!resumeParticipant, 'E2E_RESUME_PARTICIPANT_USERNAME/PASSWORD were not configured for the optional independent account with a startable session.');
  await loginWithOidc(page, resumeParticipant!);
  await page.goto('/sessions');
  await page.getByRole('button', { name: 'Przejdź do wyboru' }).click();
  await page.getByRole('button', { name: 'Pełna sesja' }).click();
  await page.getByRole('button', { name: 'Kontynuuj' }).click();
  await page.getByRole('button', { name: 'Rozpocznij sesję' }).click();
  await expect(page.getByRole('heading', { name: 'W trakcie sesji' })).toBeVisible();
  await page.getByRole('button', { name: 'Zapisz i wróć później' }).click();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'W trakcie sesji' })).toBeVisible();
});

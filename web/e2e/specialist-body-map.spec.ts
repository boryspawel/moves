import { expect, test, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import path from 'node:path';

type Fixture = { setId: string; draftVersionId: string; publishedVersionId: string };
const fixturePath = path.join(__dirname, '..', '.auth/specialist-body-map-fixture.json');

test.describe('specialist body map on real Compose services', () => {
  test('renders, supports pointer/keyboard/focus, and has no overflow', async ({ page }) => {
    const fixture = JSON.parse(await readFile(fixturePath, 'utf8')) as Fixture;
    const problems = collectRuntimeProblems(page);
    await page.goto(`/exercise-sets/${fixture.setId}/versions/${fixture.draftVersionId}/edit`);
    const draftMap = page.locator('app-body-map');
    await expect(draftMap.locator('.svg-host svg')).toBeVisible();
    await expect(draftMap.locator('.svg-host svg')).toHaveCount(1);
    await expect(draftMap.locator('svg[data-anatomy-view="FRONT"]')).toBeVisible();

    await page.goto(`/exercise-sets/${fixture.setId}/versions/${fixture.publishedVersionId}`);
    const map = page.locator('app-body-map');
    await expect(map.locator('.svg-host svg')).toBeVisible();
    await map.getByRole('button', { name: 'Tył' }).click();
    await expect(map.getByRole('button', { name: 'Tył' })).toHaveAttribute('aria-pressed', 'true');
    await expect(map.locator('.svg-host svg')).toHaveCount(1);
    await expect(map.locator('svg[data-anatomy-view="BACK"]')).toBeVisible();
    await expect(map.locator('svg[data-anatomy-view="FRONT"]')).toHaveCount(0);
    const region = map.locator('g[role="button"]').first();
    await region.click(); await expect(map.getByRole('dialog')).toBeVisible();
    const close = map.getByRole('button', { name: 'Zamknij szczegóły' });
    await close.click(); await expect(region).toBeFocused();
    await region.focus(); await page.keyboard.press('Enter'); await expect(map.getByRole('dialog')).toBeVisible(); await close.click();
    await region.focus(); await page.keyboard.press(' '); await expect(map.getByRole('dialog')).toBeVisible(); await close.click();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
    expect(problems).toEqual([]);
  });
});

function collectRuntimeProblems(page: Page): string[] {
  const problems: string[] = [];
  page.on('console', message => { if (message.type() === 'error' && message.text() !== 'Third-party cookie blocking is enabled; Keycloak session iframe is unavailable.') problems.push(`console.error: ${message.text()}`); });
  page.on('pageerror', error => problems.push(`pageerror: ${error.message}`));
  page.on('requestfailed', request => { if (/\/(assets\/anatomy\/anatomy-body-(front|back)-v1\.svg|api\/)/.test(request.url())) problems.push(`requestfailed: ${request.url()} ${request.failure()?.errorText ?? ''}`); });
  return problems;
}

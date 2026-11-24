import { test, expect } from '@playwright/test';
import { housingFixture } from './support/housing-fixture';
import { fixture } from './support/ui-fixture';
for (const theme of ['light', 'dark'])
  test(theme + ' secondary forms keep readable text and fields', async ({ page }) => {
    await housingFixture(page);
    await page.addInitScript((t) => localStorage.setItem('fastcoloc.desktop-theme.v1', t), theme);
    for (const route of [
      '/annonces/own/modifier?step=3',
      '/admin/annonces/ui-listing/modifier',
      '/register/2',
      '/profile',
    ]) {
      await page.goto(route);
      await page.locator('form').first().waitFor();
      const pairs = await page
        .locator(
          '.listing-steps span,.flow-progress li,.field input,.listing-editor input,.field select',
        )
        .evaluateAll((es) =>
          es
            .filter((e) => e.getClientRects().length)
            .map((e) => {
              let n: Element | null = e,
                bg = '';
              while (n) {
                bg = getComputedStyle(n).backgroundColor;
                if (bg !== 'rgba(0, 0, 0, 0)') break;
                n = n.parentElement;
              }
              const L = (c: string) => {
                const rgb = c
                  .match(/[\d.]+/g)!
                  .slice(0, 3)
                  .map(Number)
                  .map((v) => v / 255)
                  .map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
                return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722;
              };
              const a = L(getComputedStyle(e).color),
                b = L(bg);
              return {
                tag: e.tagName,
                cls: e.className,
                ratio: (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05),
              };
            }),
        );
      for (const pair of pairs)
        expect(pair.ratio, route + ' ' + pair.cls).toBeGreaterThanOrEqual(4.5);
    }
  });
test('recovery form and denied editing remain reachable without changing permission rules', async ({
  page,
}) => {
  await fixture(page);
  await page.goto(
    '/auth/callback#access_token=fixture-only&refresh_token=fixture-only&type=recovery',
  );
  await expect(page).toHaveURL(/reset-password$/);
  await expect(page.getByLabel('Nouveau mot de passe', { exact: true })).toBeVisible();
  await page.locator('form button.btn').last().click();
  await expect(page.locator('.field-error').first()).toBeVisible();
});
test('another members listing never exposes an editing form', async ({ page }) => {
  await housingFixture(page, 'USER');
  await page.goto('/annonces/ui-listing/modifier');
  await expect(
    page.getByRole('heading', { name: 'Complète ton profil pour continuer.' }),
  ).toBeVisible();
  await expect(page.locator('form')).toHaveCount(0);
});

import { test, expect } from '@playwright/test';
import { housingFixture } from './support/housing-fixture';
for (const theme of ['light', 'dark'])
  for (const width of [375, 1440])
    test(theme + ' ' + width + ' secondary controls and dialogs', async ({ page }) => {
      await housingFixture(page);
      await page.setViewportSize({ width, height: 1000 });
      await page.addInitScript((t) => localStorage.setItem('fastcoloc.desktop-theme.v1', t), theme);
      await page.route('https://tile.openstreetmap.org/**', (r) => r.abort());
      await page.goto('/admin');
      await page.getByRole('button', { name: 'Corriger les contacts', exact: true }).click();
      const dialog = page.getByRole('dialog');
      await expect(dialog).toBeVisible();
      await expect(dialog.getByLabel('Mobile', { exact: true })).toHaveValue('+216 20 000 000');
      await page.keyboard.press('Escape');
      await expect(dialog).not.toBeVisible();
      for (const name of ['Bloquer', 'Supprimer']) {
        await page.getByRole('button', { name, exact: true }).first().click();
        await expect(dialog).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(dialog).not.toBeVisible();
      }
      await page.goto('/admin/annonces/ui-listing');
      for (const name of ['Masquer', 'Supprimer']) {
        await page.getByRole('button', { name, exact: true }).first().click();
        await expect(dialog).toBeVisible();
        await page.keyboard.press('Escape');
      }
      await page.goto('/annonces/ui-listing');
      await page.locator('.all-photos').click();
      await expect(dialog).toBeVisible();
      await page.keyboard.press('Escape');
      await expect(page.locator('.all-photos')).toBeFocused();
      await page.goto('/annonces');
      if (width < 851) {
        const summary = page.locator('.catalog-advanced>summary');
        await summary.click();
        await page.getByText('Plus de critères', { exact: true }).click();
        await expect(page.locator('[name=capacity]')).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(summary).toBeFocused();
      } else {
        await page.getByRole('button', { name: 'Filtres et tri' }).click();
        await expect(dialog).toBeVisible();
        await dialog.getByLabel('Offres', { exact: true }).check();
        await page.keyboard.press('Escape');
      }
      if (width < 851) {
        await page.getByRole('button', { name: 'Ouvrir le menu' }).click();
        await expect(page.locator('.mobile-navigation')).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(page.locator('.mobile-navigation')).not.toBeVisible();
        await expect(page.getByRole('button', { name: 'Ouvrir le menu' })).toBeFocused();
      }
    });
test('all skeletons and gallery remain stable under reduced motion', async ({ page }) => {
  await housingFixture(page);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.route('**/api/v1/listings/ui-listing', async (r) => {
    await new Promise((resolve) => setTimeout(resolve, 1000));
    await r.fallback();
  });
  await page.goto('/annonces/ui-listing');
  await expect(page.locator('fc-skeleton')).toBeVisible();
  expect(
    await page.locator('.skeleton-picture').evaluate((e) => getComputedStyle(e).animationName),
  ).toBe('none');
  await expect(page.locator('.listing-title')).toBeVisible();
});

import { test, expect, Page } from '@playwright/test';

import { fixture, listing } from './support/ui-fixture';

async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
}
test('home preserves brand colors and searches with region, budget and listing type', async ({
  page,
}) => {
  await fixture(page);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await expect(page.locator('.preview-card')).toHaveCount(4);
  expect(
    await page
      .locator('.hero-photo')
      .evaluate((el: HTMLImageElement) => el.complete && el.naturalWidth > 0),
  ).toBe(true);
  expect(
    await page.evaluate(() =>
      ['--violet', '--coral', '--pink', '--cream', '--plum'].map((k) =>
        getComputedStyle(document.documentElement).getPropertyValue(k).trim(),
      ),
    ),
  ).toEqual(['#7138d8', '#ff7954', '#ffb9d6', '#fff6ef', '#251532']);
  await noOverflow(page);
  await page.screenshot({ path: 'test-results/ui-home-desktop.png', fullPage: true });
  await page.locator('[name=region]').selectOption('1');
  await page.getByLabel('Budget maximum en TND par mois').fill('500');
  await page.locator('[name=kind]').selectOption('OFFER');
  await page.getByRole('button', { name: 'Explorer les annonces', exact: true }).click();
  await expect(page).toHaveURL(/governorateId=1/);
  expect(new URL(page.url()).searchParams.get('maxRent')).toBe('500');
  expect(new URL(page.url()).searchParams.get('kind')).toBe('OFFER');
});
test('phone navigation and home remain usable at 390px', async ({ page }) => {
  await fixture(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await expect(page.locator('.preview-card')).toHaveCount(4);
  await noOverflow(page);
  await page.getByRole('button', { name: 'Ouvrir le menu' }).click();
  await expect(page.locator('.mobile-navigation')).toBeVisible();
  await page
    .locator('.mobile-navigation')
    .getByRole('link', { name: 'Se connecter', exact: true })
    .click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByLabel('Adresse e-mail')).toBeVisible();
  await noOverflow(page);
  await page.screenshot({ path: 'test-results/ui-login-mobile.png', fullPage: true });
});
test('catalog filters support Escape and submit without obscuring results', async ({ page }) => {
  await fixture(page, true);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/annonces');
  await expect(page.locator('.preview-card')).toHaveCount(4);
  const summary = page.locator('.catalog-advanced>summary');
  await summary.click();
  await expect(page.locator('[name=region]')).toBeVisible();
  await page.locator('[name=region]').focus();
  await page.keyboard.press('Escape');
  await expect(page.locator('.catalog-advanced')).not.toHaveAttribute('open');
  await expect(summary).toBeFocused();
  await summary.click();
  await page.locator('[name=maxRent]').fill('550');
  await page.getByRole('button', { name: 'Afficher les résultats', exact: true }).click();
  await expect(page).toHaveURL(/maxRent=550/);
  await expect(page.locator('.catalog-advanced')).not.toHaveAttribute('open');
  await noOverflow(page);
  await page.screenshot({ path: 'test-results/ui-catalog-mobile.png', fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await expect(page.locator('.catalog-map-panel')).toBeVisible();
  await noOverflow(page);
  await page.screenshot({ path: 'test-results/ui-catalog-desktop.png', fullPage: true });
});
test('listing gallery opens with keyboard, closes with Escape, and contact remains available', async ({
  page,
}) => {
  await fixture(page, true);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/annonces/ui-listing');
  await expect(page.getByRole('heading', { name: listing.title, exact: true })).toBeVisible();
  await page.locator('.all-photos').focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.locator('.photo-dialog figure')).toHaveCount(5);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.getByRole('link', { name: 'Contacter', exact: true })).toHaveAttribute(
    'href',
    '/messages?listing=ui-listing',
  );
  await noOverflow(page);
  await page.screenshot({ path: 'test-results/ui-listing-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('link', { name: 'Contacter', exact: true })).toBeVisible();
  await noOverflow(page);
  await page.screenshot({ path: 'test-results/ui-listing-mobile.png', fullPage: true });
});
test('profile sections and real-email preferences are usable on a phone', async ({ page }) => {
  await fixture(page, true);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/profile');
  await expect(page.getByRole('heading', { name: 'Notifications par e-mail' })).toBeVisible();
  await page.getByRole('link', { name: 'Notifications', exact: true }).click();
  await page.getByRole('checkbox').uncheck();
  await page.getByRole('button', { name: 'Enregistrer les notifications' }).click();
  await expect(page.getByText('Préférences enregistrées.', { exact: true })).toBeVisible();
  await noOverflow(page);
  const choose = page.waitForEvent('filechooser');
  await page.getByRole('button', { name: 'Choisir une photo', exact: true }).click();
  const chooser = await choose;
  expect(chooser.isMultiple()).toBe(false);
  await chooser.setFiles([]);
  await page.screenshot({ path: 'test-results/ui-profile-mobile.png', fullPage: true });
});
test('registration and administrative dashboard keep their responsive forms', async ({ page }) => {
  await fixture(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/register/1');
  await expect(page.getByLabel('Prénom', { exact: true })).toBeVisible();
  await noOverflow(page);
  await page.screenshot({ path: 'test-results/ui-registration-mobile.png', fullPage: true });
  const admin = await page.context().newPage();
  await fixture(admin, true);
  await admin.setViewportSize({ width: 1440, height: 1000 });
  await admin.goto('/admin');
  await expect(admin.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible();
  await expect(admin.locator('.sidebar .brand')).toHaveCSS('color', 'rgb(37, 21, 50)');
  await expect(
    admin.locator('.sidebar').getByRole('link', { name: 'Annonces et modération' }),
  ).toHaveCSS('color', 'rgb(37, 21, 50)');
  await admin.locator('.skip-link').focus();
  await admin.keyboard.press('Enter');
  await expect(admin).toHaveURL(/\/admin$/);
  await noOverflow(admin);
  await admin.screenshot({ path: 'test-results/ui-admin-desktop.png', fullPage: true });
  await admin.close();
});

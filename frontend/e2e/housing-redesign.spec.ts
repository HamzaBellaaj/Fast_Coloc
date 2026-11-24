import { test, expect } from '@playwright/test';
import { housingFixture } from './support/housing-fixture';
import { fixture } from './support/ui-fixture';
import { mkdirSync, writeFileSync } from 'node:fs';
const evidence = '.hallmark/verification';
mkdirSync(evidence, { recursive: true });
const routes = [
  '/',
  '/annonces',
  '/favoris',
  '/mes-annonces',
  '/annonces/ui-listing',
  '/annonces/nouvelle',
  ...Array.from({ length: 5 }, (_, step) => '/annonces/own/modifier?step=' + step),
  '/pour-toi',
  '/messages',
  '/messages/conversation-1',
  '/messages?listing=ui-listing',
  '/profile',
  ...Array.from({ length: 5 }, (_, i) => '/register/' + (i + 1)),
  '/admin',
  '/admin/annonces',
  '/admin/annonces/ui-listing',
  '/admin/annonces/ui-listing/modifier',
  '/page-inexistante',
];
for (const theme of ['light', 'dark'])
  for (const width of [375, 768, 1024, 1440])
    test(theme + ' ' + width + ' reviews all reachable app screens', async ({ page }) => {
      test.setTimeout(240000);
      await housingFixture(page);
      await page.setViewportSize({ width, height: 1000 });
      await page.addInitScript((t) => localStorage.setItem('fastcoloc.desktop-theme.v1', t), theme);
      await page.route('https://tile.openstreetmap.org/**', (r) => r.abort());
      const seen = [];
      for (const route of routes) {
        await page.goto(route);
        await expect(page.locator('html')).toHaveAttribute('data-desktop-theme', theme);
        await page.waitForTimeout(120);
        await expect(page.locator('main')).toBeVisible();
        expect(
          await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
          route + ' overflow',
        ).toBe(true);
        const alerts = await page.locator('[role=alert]').allTextContents();
        if (route.includes('/modifier')) await expect(page.locator('form')).toBeVisible();
        await page.screenshot({
          path:
            evidence + '/' + theme + '-' + width + '-' + route.replace(/[^a-z0-9]/gi, '-') + '.png',
          fullPage: true,
          animations: 'disabled',
        });
        seen.push({ route, theme, width, alerts });
      }
      writeFileSync(
        evidence + '/coverage-' + theme + '-' + width + '.json',
        JSON.stringify(seen, null, 2),
      );
    });
for (const theme of ['light', 'dark'])
  test(theme + ' public authentication and feedback states', async ({ page }) => {
    await fixture(page);
    await page.addInitScript((t) => localStorage.setItem('fastcoloc.desktop-theme.v1', t), theme);
    for (const route of [
      '/login',
      '/forgot-password',
      '/reset-password',
      '/verify-email',
      '/auth/callback',
      '/register/1',
    ]) {
      await page.goto(route);
      await expect(page.locator('main')).toBeVisible();
      await page.screenshot({
        path: evidence + '/public-' + theme + '-' + route.replaceAll('/', '-') + '.png',
        fullPage: true,
      });
    }
    await page.goto('/login');
    await page.locator('form .btn').first().click();
    await expect(page.locator('.field-error').first()).toBeVisible();
  });
test('member permission boundaries and mobile theme persistence', async ({ page }) => {
  await housingFixture(page, 'USER');
  await page.setViewportSize({ width: 375, height: 844 });
  await page.goto('/profile');
  await page.getByRole('button', { name: 'Ouvrir le menu' }).click();
  await page.getByLabel('Thème', { exact: true }).selectOption('dark');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-desktop-theme', 'dark');
  await page.goto('/admin');
  await expect(page).toHaveURL(/profile/);
  await expect(page.getByRole('link', { name: 'Administration', exact: true })).toHaveCount(0);
});
for (const state of ['empty', 'error'])
  test(
    state + ' states across discovery messaging recommendations and profile',
    async ({ page }) => {
      await housingFixture(page, 'ADMIN', state);
      for (const route of ['/annonces', '/pour-toi', '/messages', '/profile']) {
        await page.goto(route);
        await page.waitForTimeout(180);
        await page.screenshot({
          path: evidence + '/' + state + '-' + route.replaceAll('/', '-') + '.png',
          fullPage: true,
        });
        if (state === 'error' && route !== '/messages')
          await expect(page.locator('[role=alert]').first()).toBeVisible();
      }
    },
  );
test('layout skeletons appear while listing and gallery requests are pending', async ({ page }) => {
  await housingFixture(page);
  await page.route('**/api/v1/listings?**', async (r) => {
    await new Promise((resolve) => setTimeout(resolve, 1000));
    await r.fallback();
  });
  await page.goto('/annonces');
  await expect(page.locator('fc-skeleton')).toBeVisible();
  await expect(page.locator('.preview-card')).toHaveCount(4);
});

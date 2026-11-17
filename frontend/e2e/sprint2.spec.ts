import { test, expect } from '@playwright/test';
import { mkdirSync } from 'node:fs';
const recipe = require('../../backend/tools/sprint2-fixtures.cjs');
test('Sprint 2 : publication Web, recherche, photos, favoris, édition et droits', async ({
  page,
  browser,
}) => {
  test.setTimeout(300000);
  let fixture: any;
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.name));
  try {
    fixture = await recipe.fixtures();
    const id = await recipe.webCreate(page, fixture, expect);
    await page.goto('/annonces?q=' + encodeURIComponent(fixture.webTitle));
    await expect(page.locator('.listing-card')).toHaveCount(1);
    await page.getByLabel('Loyer max. (TND)', { exact: true }).fill('100');
    await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
    await expect(page.locator('.listing-card')).toHaveCount(0);
    await page.getByLabel('Loyer max. (TND)', { exact: true }).fill('400');
    await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
    await expect(page.locator('.listing-card')).toHaveCount(1);
    await expect(page.locator('.leaflet-marker-icon')).toHaveCount(1);
    await page.locator('.leaflet-marker-icon').click();
    await expect(page.locator('.leaflet-popup-content a')).toHaveAttribute(
      'href',
      '/annonces/' + id,
    );
    const reader = await browser.newContext();
    const other = await reader.newPage();
    await recipe.login(other, fixture.reader);
    await other.goto('/annonces/' + id);
    await expect(other.locator('.listing-gallery img')).toHaveCount(1);
    await expect(other.locator('.listing-gallery img')).toHaveJSProperty('naturalWidth', 1);
    await expect(
      other.getByRole('link', { name: 'Modifier mon annonce', exact: true }),
    ).toHaveCount(0);
    await other.getByRole('button', { name: '♡ Ajouter aux favoris', exact: true }).click();
    await expect(
      other.getByRole('button', { name: '♥ Retirer des favoris', exact: true }),
    ).toBeVisible();
    await other.goto('/favoris');
    await expect(other.getByRole('heading', { name: fixture.webTitle, exact: true })).toBeVisible();
    const denied = await fetch('http://127.0.0.1:8001/api/v1/listings/' + id, {
      method: 'DELETE',
      headers: { Authorization: 'Bearer ' + fixture.reader.session.access_token },
    });
    expect(denied.status).toBe(404);
    await recipe.webEdit(page, id, 425.5);
    await other.goto('/annonces/' + id);
    await expect(other.locator('.rent')).toContainText('425.5');
    await other.getByRole('button', { name: '♥ Retirer des favoris', exact: true }).click();
    await expect(
      other.getByRole('button', { name: '♡ Ajouter aux favoris', exact: true }),
    ).toBeVisible();
    mkdirSync('test-results/screenshots', { recursive: true });
    for (const width of [390, 768, 1440]) {
      await other.setViewportSize({ width, height: 900 });
      await other.screenshot({
        path: 'test-results/screenshots/sprint2-detail-' + width + '.png',
        fullPage: true,
      });
      const overflow = await other.evaluate(() =>
        [...document.querySelectorAll('body *')]
          .filter((e) => e.getBoundingClientRect().right > innerWidth + 1)
          .map((e) => ({
            tag: e.tagName,
            classes: e.className,
            width: e.getBoundingClientRect().width,
          }))
          .slice(0, 12),
      );
      expect(
        await other.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
        JSON.stringify({ width, overflow }),
      ).toBe(true);
    }
    await reader.close();
    expect(errors).toEqual([]);
  } finally {
    await recipe.cleanup();
  }
});

import { test, expect } from '@playwright/test';
import { mkdirSync, writeFileSync } from 'node:fs';
const recipe = require('../../backend/tools/sprint2-fixtures.cjs');

test('carte : tuiles OSM réelles, origine seule et position sélectionnée', async ({ page }) => {
  test.setTimeout(240000);
  const headers: Promise<Record<string, string>>[] = [];
  const statuses: number[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).hostname === 'tile.openstreetmap.org') {
      headers.push(request.allHeaders());
    }
  });
  page.on('response', (response) => {
    if (new URL(response.url()).hostname === 'tile.openstreetmap.org')
      statuses.push(response.status());
  });
  try {
    const fixture = await recipe.fixtures();
    await recipe.login(page, fixture.owner);
    await page.goto('/annonces/nouvelle');
    await page.getByLabel('Titre', { exact: true }).fill('Position de recette temporaire');
    await page
      .getByLabel('Description', { exact: true })
      .fill('Vérification du fond de carte et de la sélection de position, sans publier.');
    await page.getByLabel('Type de logement', { exact: true }).fill('Appartement');
    await page.getByRole('button', { name: 'Continuer →', exact: true }).click();
    await expect(page.locator('meta[name=referrer]')).toHaveAttribute('content', 'no-referrer');
    await expect.poll(() => statuses.length).toBeGreaterThan(0);
    await expect.poll(() => page.locator('.leaflet-tile-loaded').count()).toBeGreaterThan(0);
    const map = page.locator('.listing-map');
    await expect
      .poll(() =>
        map
          .locator('img.leaflet-tile')
          .evaluateAll(
            (images) =>
              images.length > 0 &&
              images.every(
                (image) =>
                  (image as HTMLImageElement).complete &&
                  (image as HTMLImageElement).naturalWidth > 0,
              ),
          ),
      )
      .toBe(true);
    const bounds = await map.boundingBox();
    expect(bounds).not.toBeNull();
    await map.click({ position: { x: bounds!.width / 2, y: bounds!.height / 2 } });
    const latitude = Number(await page.getByLabel('Latitude', { exact: true }).inputValue());
    const longitude = Number(await page.getByLabel('Longitude', { exact: true }).inputValue());
    expect(latitude).toBeGreaterThan(30);
    expect(latitude).toBeLessThan(38);
    expect(longitude).toBeGreaterThan(7);
    expect(longitude).toBeLessThan(12);
    mkdirSync('test-results/screenshots', { recursive: true });
    await map.screenshot({
      path: 'test-results/screenshots/map-picker-referrer-fixed.png',
      animations: 'disabled',
    });
    // Same browser and normal HTTP cache: no tile scraping, prefetch or cache busting.
    await page.goto('/annonces?kind=OFFER&q=aucune-annonce-' + fixture.run);
    await expect(page.locator('fc-listing-map .leaflet-tile-loaded').first()).toBeVisible();
    await expect
      .poll(() =>
        page
          .locator('fc-listing-map img.leaflet-tile')
          .evaluateAll(
            (images) =>
              images.length > 0 &&
              images.every(
                (image) =>
                  (image as HTMLImageElement).complete &&
                  (image as HTMLImageElement).naturalWidth > 0,
              ),
          ),
      )
      .toBe(true);
    const actualHeaders = await Promise.all(headers);
    expect(actualHeaders.length).toBeGreaterThan(0);
    for (const h of actualHeaders) expect(h['referer']).toBe('http://localhost:4200/');
    expect(statuses.every((status) => status === 200)).toBe(true);
    writeFileSync(
      'test-results/map-referrer-report.json',
      JSON.stringify(
        {
          originOnly: true,
          globalAuthPolicy: 'no-referrer',
          tileResponses: statuses.length,
          statuses: [...new Set(statuses)],
          selectedPosition: { latitude, longitude },
        },
        null,
        2,
      ),
    );
  } finally {
    await recipe.cleanup();
  }
});

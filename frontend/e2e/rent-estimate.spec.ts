import { test, expect } from '@playwright/test';
import { mkdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

// Isolated UI check: no Supabase writes and no additional Ollama/Tavily calls.
test('US25: estimate a new listing, dismiss, apply and keep editing the rent', async ({ page }) => {
  await page.addInitScript(() =>
    sessionStorage.setItem(
      'fastcoloc.session.v1',
      JSON.stringify({
        access_token: 'ui-test',
        refresh_token: 'ui-test',
        expires_at: Math.floor(Date.now() / 1000) + 3600,
      }),
    ),
  );
  let estimateCalls = 0;
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    let body: unknown = {};
    if (path.endsWith('/users/sync'))
      body = {
        id: 'owner',
        firstName: 'Test',
        role: 'USER',
        status: 'ACTIVE',
        completedAt: '2026-11-01',
        validatedSteps: [1, 2, 3, 4, 5],
      };
    if (path.endsWith('/listings/areas'))
      body = [{ id: 1, locality: 'Ariana', governorate: 'Ariana', governorate_id: 1 }];
    if (path.endsWith('/geography/position')) body={latitude:36.86,longitude:10.19,address:'Centre'};
    if (path.endsWith('/ai/rent-estimate')) {
      estimateCalls++;
      expect(route.request().headers()['authorization']).toBe('Bearer ui-test');
      const multipart = route.request().postDataBuffer()!.toString();
      expect(multipart).toContain('name="logement"');
      expect(multipart).toContain('name="photo"');
      expect(multipart.match(/name="photo"/g)).toHaveLength(2);
      expect(multipart).toContain('salon.jpg');
      expect(multipart).toContain('cuisine.jpg');
      expect(multipart).not.toContain('retirer.jpg');
      expect(multipart).not.toContain('"rent"');
      body = {
        success: true,
        estimation: {
          disponible: true,
          loyer_min_tnd: 1100,
          loyer_max_tnd: 1400,
          loyer_conseille_tnd: 1250,
          fiabilite: 'moyenne',
          explication: 'Estimation de recette UI.',
        },
      };
    }
    await route.fulfill({ json: body });
  });
  await page.goto('/annonces/nouvelle');
  await page.getByRole('button', { name: 'Continuer', exact: true }).click();
  await page.getByLabel('Titre', { exact: true }).fill('Appartement lumineux à Ariana');
  await page
    .getByLabel('Décris ton logement', { exact: true })
    .fill('Appartement de 85 m² meublé et lumineux avec climatisation.');
  await page.getByLabel('Type de logement', { exact: true }).fill('S+2');
  await page
    .getByRole('combobox', { name: 'Gouvernorat', exact: true })
    .selectOption({ label: 'Ariana' });
  await page
    .getByRole('combobox', { name: 'Localité du logement', exact: true })
    .selectOption({ label: 'Ariana' });
  await page.getByLabel('Adresse du logement', { exact: true }).fill('Centre');
  await page.getByLabel('Latitude', { exact: true }).fill('36.86');
  await page.getByLabel('Longitude', { exact: true }).fill('10.19');
  await expect(page.getByRole('status').filter({hasText:'Position terrestre validée'})).toBeVisible();
  await page.getByRole('button', { name: 'Continuer', exact: true }).click();
  const rent = page.getByLabel('Loyer mensuel (TND)', { exact: true });
  await rent.fill('900');
  const estimate = page.getByRole('button', {
    name: 'Estimer mon loyer avec l’IA',
    exact: true,
  });
  await estimate.click();
  await expect(page.getByRole('alert')).toContainText('au moins une photo');
  expect(estimateCalls).toBe(0);
  await page
    .locator('.rent-ai input[type=file]')
    .setInputFiles(
      ['salon.jpg', 'cuisine.jpg', 'retirer.jpg'].map((name) => ({
        name,
        mimeType: 'image/jpeg',
        buffer: readFileSync(resolve(__dirname, '../public/assets/coloc-interior.jpg')),
      })),
    );
  await expect(page.locator('.ai-pending-gallery figure')).toHaveCount(3);
  await page
    .locator('.ai-pending-gallery figure')
    .filter({ hasText: 'retirer.jpg' })
    .getByRole('button')
    .click();
  await expect(page.locator('.ai-pending-gallery figure')).toHaveCount(2);
  await estimate.click();
  await expect(page.getByRole('button', { name: 'Utiliser ce prix', exact: true })).toBeVisible();
  await expect(rent).toHaveValue('900');
  mkdirSync('test-results/screenshots', { recursive: true });
  for (const width of [390, 1440]) {
    await page.setViewportSize({ width, height: 950 });
    await page
      .locator('.rent-ai')
      .screenshot({ path: `test-results/screenshots/rent-estimate-${width}.png` });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
  }
  await page.getByRole('button', { name: 'Ignorer', exact: true }).click();
  await expect(rent).toHaveValue('900');
  await estimate.click();
  await page.getByRole('button', { name: 'Utiliser ce prix', exact: true }).click();
  await expect(rent).toHaveValue('1250');
  await rent.fill('1300');
  await expect(rent).toHaveValue('1300');
  expect(estimateCalls).toBe(2);
});

import { test, expect, Page } from '@playwright/test';
import { readFileSync, mkdirSync } from 'node:fs';

// Reuse the Sprint 2 fixture lifecycle. All mutations target only UUID-labelled accounts/listings.
const recipe = require('../../backend/tools/sprint2-fixtures.cjs');
const config = Object.fromEntries(
  readFileSync('../backend/.env.local', 'utf8')
    .split(/\r?\n/)
    .filter((l) => /^[A-Z_]+=/.test(l))
    .map((l) => {
      const i = l.indexOf('=');
      return [l.slice(0, i), l.slice(i + 1).trim()];
    }),
);
let fixture: any;
let id = '';
let title = '';
test.beforeAll(async () => {
  fixture = await recipe.fixtures();
  title = '[TEST US29 TEMPORAIRE] ' + fixture.run;
});
test.afterAll(async () => {
  console.log('US29 temporary accounts cleaned:', await recipe.cleanup());
});
async function confirm(page: Page, reason: string) {
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await dialog.getByLabel('Motif obligatoire', { exact: true }).fill(reason);
  await dialog.getByRole('button', { name: 'Confirmer l’action', exact: true }).click();
  await expect(dialog).not.toBeVisible();
}
test('US29 réel : consultation, correction, masquage, réactivation et suppression isolée', async ({
  page,
}) => {
  test.setTimeout(300000);
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  const body = {
    kind: 'OFFER',
    title,
    description:
      'Annonce technique isolée pour la vérification US29. Elle sera supprimée après le parcours.',
    localityId: fixture.area.id,
    street: 'Adresse de recette temporaire',
    latitude: 36.8,
    longitude: 10.18,
    rent: 350,
    capacity: 2,
    propertyType: 'Appartement',
    roommateType: 'Étudiant',
    equipment: ['WiFi', 'Cuisine'],
    availableFrom: '2026-01-01',
    availableUntil: null,
    publicationDays: 30,
  };
  const created = await recipe.api('POST', '/listings', fixture.owner.session.access_token, body);
  id = created.id;
  const photo = new FormData();
  photo.append('file', new Blob([recipe.png], { type: 'image/png' }), 'us29.png');
  photo.append('caption', 'Photo technique temporaire US29');
  await recipe.api(
    'POST',
    '/listings/' + id + '/photos',
    fixture.owner.session.access_token,
    photo,
  );
  await recipe.api('POST', '/listings/' + id + '/publish', fixture.owner.session.access_token);
  await recipe.api('PUT', '/listings/' + id + '/favorite', fixture.reader.session.access_token);
  await page.goto('/login');
  await page.getByLabel('Adresse e-mail', { exact: true }).fill(config['FASTCOLOC_TEST_EMAIL']);
  await page
    .getByLabel('Mot de passe', { exact: true })
    .fill(config['FASTCOLOC_TEST_EMAIL_PASSWORD']);
  await page.getByRole('button', { name: 'Se connecter →', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login$/);
  await page.goto('/admin');
  await page.getByRole('link', { name: '⌂ Gestion des annonces', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Gestion des annonces', exact: true }),
  ).toBeVisible();
  await page.getByRole('textbox', { name: 'Rechercher', exact: true }).fill(title);
  await page.getByRole('combobox', { name: 'Type', exact: true }).selectOption('OFFER');
  const from = page.getByLabel('Créée à partir du', { exact: true });
  await expect(from).toHaveAttribute('type', 'date');
  await from.click();
  await page.keyboard.press('Escape');
  await from.fill('2026-01-01');
  await page.getByLabel('Créée jusqu’au', { exact: true }).fill('2026-12-31');
  await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
  const row = page.getByRole('row').filter({ hasText: title });
  await expect(row).toBeVisible();
  await expect(row).toContainText('350 TND');
  mkdirSync('test-results/us29', { recursive: true });
  await page.screenshot({ path: 'test-results/us29/gestion-annonces.png', fullPage: true });
  await row.getByRole('link', { name: 'Consulter', exact: true }).click();
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
  await expect(page.getByAltText('Photo technique temporaire US29')).toBeVisible();
  await page.getByRole('link', { name: 'Modifier l’annonce', exact: true }).click();
  await page.getByLabel('Titre', { exact: true }).fill(title + ' corrigée');
  await page.getByRole('button', { name: 'Continuer →', exact: true }).click();
  await page.getByRole('button', { name: 'Continuer →', exact: true }).click();
  await page.getByLabel('Loyer mensuel (TND)', { exact: true }).fill('375');
  await page
    .getByLabel('Motif de correction obligatoire')
    .fill('Correction isolée du titre et du loyer US29');
  await page.getByRole('button', { name: 'Enregistrer l’annonce', exact: true }).click();
  await expect(page).toHaveURL('/admin/annonces/' + id);
  await expect(page.getByRole('heading', { name: title + ' corrigée', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Masquer', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Annuler', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Masquer', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Masquer', exact: true }).click();
  await confirm(page, 'Contrôle du masquage US29');
  await expect(page.getByRole('button', { name: 'Réactiver', exact: true })).toBeVisible();
  const hidden = await recipe.api('GET', '/listings?q=' + encodeURIComponent(title));
  expect(hidden.total).toBe(0);
  const bypass = await fetch('http://127.0.0.1:8001/api/v1/listings/' + id + '/renew', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + fixture.owner.session.access_token },
  });
  expect(bypass.status).toBe(403);
  await page.getByRole('button', { name: 'Réactiver', exact: true }).click();
  await confirm(page, 'Fin du contrôle US29');
  await expect(page.getByRole('button', { name: 'Masquer', exact: true })).toBeVisible();
  expect((await recipe.api('GET', '/listings?q=' + encodeURIComponent(title))).total).toBe(1);
  for (const width of [390, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
  }
  await page.screenshot({ path: 'test-results/us29/fiche-moderation.png', fullPage: true });
  await page.getByRole('button', { name: 'Supprimer', exact: true }).click();
  await confirm(page, 'Nettoyage définitif de la seule annonce temporaire US29');
  await expect(
    page.getByRole('heading', { name: 'Annonce supprimée.', exact: true }),
  ).toBeVisible();
  await expect(page.locator('.history-event')).toHaveCount(4);
  expect(
    (await recipe.api('GET', '/listings/mine', fixture.owner.session.access_token)).items.some(
      (r: any) => r.id === id,
    ),
  ).toBe(false);
  expect(errors).toEqual([]);
});

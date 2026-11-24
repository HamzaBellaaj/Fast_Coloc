import { test, expect } from '@playwright/test';
import { mkdirSync } from 'node:fs';

test('Pour toi: one initial request, AI order, favorites, explicit refresh and error retry', async ({
  page,
}) => {
  await page.addInitScript(() =>
    sessionStorage.setItem(
      'fastcoloc.session.v1',
      JSON.stringify({
        access_token: 'ui-fixture',
        refresh_token: 'ui-fixture',
        expires_at: Math.floor(Date.now() / 1000) + 3600,
      }),
    ),
  );
  let calls = 0,
    favorites = 0;
  let release!: () => void;
  const loading = new Promise<void>((resolve) => (release = resolve));
  const rows = ['b', 'a'].map((id, index) => ({
    id,
    author_id: 'other',
    kind: 'OFFER',
    title: index === 0 ? 'Une chambre lumineuse à Ariana' : 'Une coloc calme à Tunis',
    description: 'Une description de recette de plus de vingt caractères.',
    locality_id: 1,
    locality: index === 0 ? 'Ariana' : 'Tunis',
    governorate: 'Tunis',
    governorate_id: 1,
    street: 'Centre',
    rent: 450 + index * 100,
    capacity: 2,
    property_type: 'Appartement',
    roommate_type: 'Étudiant',
    equipment: ['WiFi'],
    available_from: '2026-12-01',
    available_until: null,
    publication_days: 30,
    status: 'PUBLISHED',
    expires_at: '2099-01-01',
    version: 1,
    favorite: false,
    photos: [
      { id: 'photo', url: '/assets/coloc-interior.jpg', caption: 'Le salon', display_order: 0 },
    ],
    latitude: 36.8,
    longitude: 10.18,
  }));
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/users/sync'))
      return route.fulfill({
        json: {
          id: 'owner',
          firstName: 'Test',
          role: 'USER',
          completedAt: '2026-11-01',
          validatedSteps: [1, 2, 3, 4, 5],
        },
      });
    if (path.endsWith('/recommendations/ai')) {
      calls++;
      expect(route.request().method()).toBe('POST');
      expect(route.request().headers()['authorization']).toBe('Bearer ui-fixture');
      if (calls === 1) {
        await loading;
        return route.fulfill({
          json: {
            success: true,
            profileIncomplete: true,
            recommendations: rows.map((listing, index) => ({
              listing,
              score: 95 - index * 15,
              raison:
                index === 0
                  ? 'Correspond à ton budget et à ta ville.'
                  : 'Une autre piste selon tes préférences.',
            })),
          },
        });
      }
      if (calls === 2)
        return route.fulfill({
          status: 503,
          json: { message: 'Le service IA est indisponible. Réessaie.' },
        });
      return route.fulfill({
        json: { success: true, profileIncomplete: true, recommendations: [] },
      });
    }
    if (path.endsWith('/favorite')) {
      favorites++;
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ json: {} });
  });
  await page.goto('/pour-toi');
  await expect(
    page.getByRole('heading', { name: 'On cherche les bonnes affinités…' }),
  ).toBeVisible();
  await expect(page.getByRole('button', { name: 'Actualiser les recommandations' })).toBeDisabled();
  expect(calls).toBe(1);
  release();
  await expect(page.locator('fc-listing-card')).toHaveCount(2);
  await expect(page.locator('fc-listing-card h3')).toHaveText(rows.map((row) => row.title));
  await expect(page.locator('.recommendation-match').first()).toContainText('95');
  await expect(page.getByRole('link', { name: 'Compléter mon profil' })).toBeVisible();
  await expect(
    page.getByRole('link', { name: 'Découvrir cette annonce' }).first(),
  ).toHaveAttribute('href', '/annonces/b?from=%2Fpour-toi');
  await page.getByRole('button', { name: 'Ajouter aux favoris' }).first().click();
  await expect(page.getByRole('button', { name: 'Retirer des favoris' })).toBeVisible();
  expect(favorites).toBe(1);
  expect(calls).toBe(1);
  mkdirSync('test-results/screenshots', { recursive: true });
  for (const width of [390, 1440]) {
    await page.setViewportSize({ width, height: 950 });
    await page.screenshot({
      path: `test-results/screenshots/recommendations-${width}.png`,
      fullPage: true,
    });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
  }
  await page.getByRole('button', { name: 'Actualiser les recommandations' }).click();
  await expect(page.getByRole('alert')).toContainText('Le service IA est indisponible');
  expect(calls).toBe(2);
  await page.getByRole('button', { name: 'Réessayer', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'La prochaine coloc arrive peut-être demain.' }),
  ).toBeVisible();
  expect(calls).toBe(3);
});

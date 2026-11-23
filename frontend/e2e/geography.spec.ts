import { test, expect } from '@playwright/test';

// Real Angular/Leaflet interactions, HTTP fixtures only: no account or database mutations.
test('listing editor synchronizes map, address and coordinates and preserves the last land position', async ({
  page,
}) => {
  let water = false;
  let positionCalls = 0;
  let writes = 0;
  let listing: Record<string, unknown> = {
    id: 'geo-test',
    author_id: 'owner',
    kind: 'OFFER',
    title: 'Une colocation à Tunis',
    description: 'Un appartement lumineux à partager près des transports.',
    locality_id: 1,
    locality: 'Tunis',
    governorate: 'Tunis',
    governorate_id: 1,
    street: 'Rue initiale',
    latitude: 36.8065,
    longitude: 10.1815,
    rent: 400,
    capacity: 2,
    property_type: 'Appartement',
    roommate_type: '',
    equipment: [],
    available_from: '2026-01-01',
    available_until: null,
    publication_days: 30,
    status: 'PUBLISHED',
    version: 1,
    favorite: false,
    photos: [],
    is_hidden: false,
    expires_at: '2099-01-01T00:00:00Z',
  };
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
  await page.route('**/tile.openstreetmap.org/**', (route) =>
    route.fulfill({
      contentType: 'image/png',
      body: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aQ1cAAAAASUVORK5CYII=',
        'base64',
      ),
    }),
  );
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    let body: unknown = {};
    if (path.endsWith('/auth/session')) body = { emailConfirmed: true };
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
      body = [{ id: 1, locality: 'Tunis', governorate: 'Tunis', governorate_id: 1 }];
    if (path.endsWith('/listings/geo-test')) {
      if (route.request().method() === 'PUT') {
        const payload = route.request().postDataJSON();
        listing = {
          ...listing,
          street: payload.street,
          latitude: payload.latitude,
          longitude: payload.longitude,
          version: 2,
        };
        writes++;
      }
      body = listing;
    }
    if (path.endsWith('/geography/position')) {
      positionCalls++;
      if (water) {
        await route.fulfill({
          status: 400,
          json: {
            code: 'INVALID_POSITION',
            message: 'Position dans l’eau : choisis un point terrestre.',
          },
        });
        return;
      }
      body = {
        ...route.request().postDataJSON(),
        address: 'Rue depuis la carte, Tunis',
        approximate: true,
      };
    }
    if (path.endsWith('/geography/search'))
      body = [
        { address: 'Adresse proposée A', latitude: 36.81, longitude: 10.19 },
        { address: 'Adresse proposée B', latitude: 36.82, longitude: 10.2 },
      ];
    await route.fulfill({ json: body });
  });
  const lat = () => page.getByLabel('Latitude', { exact: true });
  const lon = () => page.getByLabel('Longitude', { exact: true });
  const street = () => page.getByLabel('Adresse du logement', { exact: true });
  await page.goto('/annonces/geo-test/modifier?step=1');
  await expect(lat()).toHaveValue('36.8065');
  await expect(street()).toHaveValue('Rue initiale');
  const map = page.locator('fc-location-picker .listing-map');
  await expect(map.locator('.leaflet-marker-icon')).toBeVisible();
  await map.click({ position: { x: 210, y: 140 } });
  await expect(street()).toHaveValue('Rue depuis la carte, Tunis');
  await expect(page.getByText(/Adresse approximative :/)).toBeVisible();
  await lat().fill('36.81');
  await lon().fill('10.19');
  await expect(page.getByRole('button', { name: 'Valider les coordonnées' })).toBeEnabled();
  await expect(lat()).toHaveValue('36.81');
  const previousLat = await lat().inputValue(),
    previousLon = await lon().inputValue();
  water = true;
  await map.click({ position: { x: 240, y: 120 } });
  await expect(page.getByText('Position dans l’eau : choisis un point terrestre.')).toBeVisible();
  await expect(lat()).toHaveValue(previousLat);
  await expect(lon()).toHaveValue(previousLon);
  water = false;
  await street().fill('Avenue de test');
  const beforeSearch = positionCalls;
  await page.getByRole('button', { name: 'Rechercher cette adresse' }).click();
  await expect(page.getByRole('button', { name: 'Adresse proposée A' })).toBeVisible();
  expect(positionCalls).toBe(beforeSearch);
  await page.getByRole('button', { name: 'Adresse proposée A' }).click();
  await expect(street()).toHaveValue('Rue depuis la carte, Tunis');
  await expect(lat()).toHaveValue('36.81');
  await expect(lon()).toHaveValue('10.19');
  for (let i = 0; i < 3; i++)
    await page.getByRole('button', { name: 'Continuer →', exact: true }).click();
  await page.getByRole('button', { name: 'Enregistrer les modifications', exact: true }).click();
  await expect.poll(() => writes).toBe(1);
  await page.reload();
  await expect(lat()).toHaveValue('36.81');
  await expect(lon()).toHaveValue('10.19');
  await expect(street()).toHaveValue('Rue depuis la carte, Tunis');
});

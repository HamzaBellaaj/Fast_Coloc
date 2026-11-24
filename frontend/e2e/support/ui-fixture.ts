import { Page } from '@playwright/test';

const profile = {
  id: 'ui-user',
  firstName: 'Sarah',
  lastName: 'Fixture',
  role: 'ADMIN',
  status: 'ACTIVE',
  completedAt: '2026-11-23T12:00:00Z',
  validatedSteps: [1, 2, 3, 4, 5],
};
export const listing = {
  id: 'ui-listing',
  author_id: 'other-user',
  kind: 'REQUEST',
  title: 'Un appartement lumineux à Ariana',
  description:
    'Une chambre calme et un salon à partager. Une vraie annonce de test pour vérifier les parcours.',
  locality_id: 1,
  locality: 'Ariana',
  governorate_id: 1,
  governorate: 'Ariana',
  street: 'Rue du Jasmin',
  latitude: null,
  longitude: null,
  rent: 450,
  capacity: 2,
  property_type: 'Appartement',
  roommate_type: 'Étudiant',
  equipment: ['WiFi', 'Cuisine'],
  available_from: '2026-12-01',
  available_until: null,
  publication_days: 30,
  status: 'PUBLISHED',
  is_hidden: false,
  expires_at: '2026-12-31T00:00:00Z',
  published_at: '2026-11-23T00:00:00Z',
  version: 1,
  favorite: false,
  photos: Array.from({ length: 5 }, (_, i) => ({
    id: 'photo-' + i,
    url: '/assets/coloc-interior.jpg',
    caption: 'Pièce ' + (i + 1),
    display_order: i,
  })),
};
export async function fixture(page: Page, signedIn = false) {
  if (signedIn)
    await page.addInitScript(() =>
      sessionStorage.setItem(
        'fastcoloc.session.v1',
        JSON.stringify({
          access_token: 'ui-fixture-only',
          refresh_token: 'ui-fixture-only',
          expires_at: Math.floor(Date.now() / 1000) + 3600,
          expires_in: 3600,
        }),
      ),
    );
  let enabled = true;
  await page.route('**/api/v1/**', async (route) => {
    const p = new URL(route.request().url()).pathname.replace('/api/v1', '');
    let body: unknown = {};
    let status = 200;
    if (p === '/auth/session') body = { emailConfirmed: true, email: 'fixture@example.invalid' };
    else if (p === '/users/sync') body = profile;
    else if (p === '/users/me/details')
      body = {
        profile,
        address: { country_code: 'TN', governorate_id: 1, locality_id: 1 },
        personal: {},
        contacts: {},
        preferences: {},
        preferredLocalities: [],
        photos: [],
      };
    else if (p === '/notification-preferences') {
      if (route.request().method() === 'PATCH')
        enabled = route.request().postDataJSON().newMessageEmail;
      body = { newMessageEmail: enabled, deliveryAvailable: true, deliveryMode: 'external' };
    } else if (p === '/listings/areas')
      body = [{ id: 1, locality: 'Ariana', governorate_id: 1, governorate: 'Ariana' }];
    else if (p === '/listings/map') body = { items: [], total: 0, page: 0, size: 50 };
    else if (p === '/listings/ui-listing') body = listing;
    else if (p.endsWith('/favorite')) body = {};
    else if (p === '/listings' || p === '/listings/mine' || p === '/listings/favorites')
      body = {
        items: Array.from({ length: 4 }, (_, i) => ({ ...listing, id: 'ui-listing-' + i })),
        total: 4,
        page: 0,
        size: 12,
      };
    else if (p === '/geography/countries') body = [{ code: 'TN', name: 'Tunisie' }];
    else if (p.includes('/geography/')) body = [{ id: 1, name: 'Ariana' }];
    else if (p === '/admin/access') body = { allowed: true };
    else if (p === '/admin/users') body = [];
    else {
      status = 404;
      body = { code: 'UI_FIXTURE_NOT_FOUND' };
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
  });
}

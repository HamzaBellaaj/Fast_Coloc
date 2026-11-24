import { Page } from '@playwright/test';
import { fixture, listing } from './ui-fixture';
export async function housingFixture(page: Page, role: 'ADMIN' | 'USER' = 'ADMIN', state = 'full') {
  await fixture(page, true);
  const profile = {
    id: 'ui-user',
    firstName: 'Sarah',
    lastName: 'Fixture',
    role,
    status: 'ACTIVE',
    completedAt: '2026-11-23T12:00:00Z',
    validatedSteps: [1, 2, 3, 4, 5],
  };
  const conversation = {
    id: 'conversation-1',
    listing_id: 'ui-listing',
    listing_reference: 'ui-listing',
    listing_title: listing.title,
    listing_kind: 'OFFER',
    locality: 'Ariana',
    rent: 450,
    participant_id: 'owner',
    participant_name: 'Ahmed',
    last_message: 'Bonjour',
    unread_count: 1,
    listing_available: true,
    created_at: '2026-11-23T12:00:00Z',
    updated_at: '2026-11-23T12:00:00Z',
  };
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request(),
      p = new URL(req.url()).pathname.replace('/api/v1', '');
    let body: any;
    let status = 200;
    if (p === '/users/sync') body = profile;
    else if (p === '/admin/access') {
      status = role === 'ADMIN' ? 200 : 403;
      body = { allowed: role === 'ADMIN' };
    } else if (p === '/users/me/details')
      body = {
        profile,
        address: { country_code: 'TN', governorate_id: 1, locality_id: 1 },
        personal: {},
        contacts: {},
        preferences: {},
        preferredLocalities: [],
        photos: [],
      };
    else if (p === '/listings/own')
      body = { ...listing, id: 'own', author_id: profile.id, latitude: 36.8, longitude: 10.18 };
    else if (p === '/admin/users')
      body = [
        {
          id: 'other-user',
          first_name: 'Ahmed',
          last_name: 'Fixture',
          app_role: 'USER',
          status: 'ACTIVE',
          created_at: '2026-11-23T12:00:00Z',
          total_count: 1,
        },
      ];
    else if (p.startsWith('/admin/users/') && p.endsWith('/contacts'))
      body = { mobile_phone: '+216 20 000 000' };
    else if (p === '/admin/listings')
      body = {
        items: [
          {
            ...listing,
            author_name: 'Ahmed',
            display_status: 'PUBLISHED',
            created_at: listing.published_at,
            updated_at: listing.published_at,
          },
        ],
        total: 1,
      };
    else if (p.endsWith('/history')) body = [];
    else if (p.startsWith('/admin/listings/'))
      body = {
        ...listing,
        author_name: 'Ahmed',
        display_status: 'PUBLISHED',
        created_at: listing.published_at,
        updated_at: listing.published_at,
      };
    else if (p === '/recommendations/ai')
      body = {
        success: true,
        profileIncomplete: false,
        recommendations:
          state === 'empty'
            ? []
            : [{ listing, score: 92, raison: 'Correspond à la zone et au budget recherchés.' }],
      };
    else if (p === '/conversations')
      body = {
        items: state === 'empty' ? [] : [conversation],
        total: state === 'empty' ? 0 : 1,
        page: 0,
        size: 20,
      };
    else if (p === '/conversations/conversation-1') body = conversation;
    else if (p.endsWith('/messages'))
      body = {
        items: [
          {
            id: 1,
            conversation_id: conversation.id,
            sender_id: 'owner',
            client_id: 'test',
            content: 'Bonjour, la chambre est disponible.',
            created_at: '2026-11-23T12:00:00Z',
          },
        ],
        hasMore: false,
        nextBefore: 1,
      };
    else if (p.endsWith('/read')) {
      status = 204;
      body = {};
    } else if (p === '/geography/position')
      body = { latitude: 36.8, longitude: 10.18, address: 'Ariana' };
    else if (
      state === 'empty' &&
      ['/listings', '/listings/mine', '/listings/favorites'].includes(p)
    )
      body = { items: [], total: 0, page: 0, size: 12 };
    if (
      state === 'error' &&
      ['/listings', '/recommendations/ai', '/notification-preferences', '/conversations'].includes(
        p,
      )
    ) {
      status = 503;
      body = { message: 'Service momentanément indisponible.' };
    }
    if (body === undefined) return route.fallback();
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: status === 204 ? '' : JSON.stringify(body),
    });
  });
}

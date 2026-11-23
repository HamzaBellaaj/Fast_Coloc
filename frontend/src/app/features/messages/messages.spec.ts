import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Messages } from './messages';
import { Messaging, Conversation, Message } from './messaging';
import { Auth } from '../../core/auth';
import { Api } from '../../core/api';
const conversation = {
  id: 'discussion',
  listing_id: 'listing',
  listing_reference: 'listing',
  listing_title: 'Chambre à Ariana',
  listing_kind: 'OFFER',
  locality: 'Ariana',
  rent: 450,
  participant_name: 'Ahmed',
  participant_id: 'owner',
  last_message: 'Bonjour',
  unread_count: 1,
  listing_available: true,
  created_at: '2026-11-23T18:00:00Z',
  updated_at: '2026-11-23T18:00:00Z',
} as Conversation;
const message = (id: number, content = 'Bonjour'): Message => ({
  id,
  conversation_id: 'discussion',
  sender_id: 'owner',
  client_id: 'client-' + id,
  content,
  created_at: '2026-11-23T18:00:00Z',
});
let service: any, router: any, view: Messages;
beforeEach(() => {
  service = {
    lookup: vi.fn().mockResolvedValue(null),
    inbox: vi.fn().mockResolvedValue({ items: [conversation], total: 1, page: 0, size: 20 }),
    detail: vi.fn().mockResolvedValue(conversation),
    history: vi.fn().mockResolvedValue({ items: [message(2)], hasMore: true, nextBefore: 2 }),
    send: vi.fn(),
    start: vi.fn(),
    read: vi.fn().mockResolvedValue(undefined),
  };
  router = { navigate: vi.fn().mockResolvedValue(true) };
  TestBed.configureTestingModule({
    providers: [
      { provide: Messaging, useValue: service },
      { provide: Auth, useValue: { profile: () => ({ id: 'guest' }) } },
      {
        provide: Api,
        useValue: {
          request: vi
            .fn()
            .mockResolvedValue({ id: 'listing', author_id: 'owner', title: 'Chambre' }),
        },
      },
      { provide: Router, useValue: router },
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: { paramMap: convertToParamMap({}), queryParamMap: convertToParamMap({}) },
        },
      },
    ],
  });
  view = TestBed.runInInjectionContext(() => new Messages());
  vi.spyOn(document, 'hasFocus').mockReturnValue(true);
});
afterEach(() => {
  TestBed.resetTestingModule();
  vi.restoreAllMocks();
});
it('keeps text and reuses the request id after a failed send', async () => {
  await view.open('discussion', null);
  view.draft = 'Une visite demain ?';
  service.send
    .mockRejectedValueOnce(new Error('Réseau indisponible'))
    .mockResolvedValueOnce(message(3, 'Une visite demain ?'));
  await view.send();
  expect(view.draft).toBe('Une visite demain ?');
  expect(view.error()).toContain('Réseau');
  await view.send();
  expect(service.send.mock.calls[0][2]).toBe(service.send.mock.calls[1][2]);
  expect(view.draft).toBe('');
});
it('does not create an empty conversation when opening a listing composer', async () => {
  service.inbox.mockResolvedValue({ items: [], total: 0, page: 0, size: 20 });
  await view.open(null, 'listing');
  expect(service.start).not.toHaveBeenCalled();
  expect(view.listing()?.id).toBe('listing');
  view.draft = 'Bonjour';
  service.start.mockResolvedValue(conversation);
  await view.send();
  expect(service.start).toHaveBeenCalledWith('listing', 'Bonjour', expect.any(String));
  expect(router.navigate).toHaveBeenCalledWith(['/messages', 'discussion'], { replaceUrl: true });
});
it('reopens an existing listing conversation rather than starting another', async () => {
  await view.open(null, 'listing');
  expect(router.navigate).toHaveBeenCalledWith(['/messages', 'discussion'], { replaceUrl: true });
  expect(service.start).not.toHaveBeenCalled();
});
it('merges older history and polling without duplicates or lost messages', async () => {
  await view.open('discussion', null);
  service.history.mockResolvedValueOnce({ items: [message(1)], hasMore: false, nextBefore: 1 });
  await view.olderMessages();
  service.history.mockResolvedValueOnce({
    items: [message(2), message(3)],
    hasMore: true,
    nextBefore: 2,
  });
  await view.refresh();
  expect(view.messages().map((m) => m.id)).toEqual([1, 2, 3]);
  expect(view.older()).toBe(false);
});
it('does not apply an old history response to another conversation', async () => {
  let resolve!: (value: unknown) => void;
  service.history.mockImplementationOnce(() => new Promise((r) => (resolve = r)));
  view.selected.set(conversation);
  const old = view.loadHistory();
  await view.open(null, null);
  resolve({ items: [message(99)], hasMore: false, nextBefore: 99 });
  await old;
  expect(view.messages()).toEqual([]);
});
it('does not mark a background discussion read', async () => {
  vi.mocked(document.hasFocus).mockReturnValue(false);
  await view.open('discussion', null);
  expect(service.read).not.toHaveBeenCalled();
});
it('marks only the received message cursor as read in a focused discussion', async () => {
  await view.open('discussion', null);
  expect(service.read).toHaveBeenCalledWith('discussion', 2);
  expect(view.conversations()[0].unread_count).toBe(0);
});
it('rejects empty and oversized drafts without making a request', async () => {
  await view.open('discussion', null);
  view.draft = '   ';
  await view.send();
  view.draft = 'x'.repeat(4001);
  await view.send();
  expect(service.send).not.toHaveBeenCalled();
});

it('does not skip inbox pages when load-more is clicked twice', async () => {
  await view.open(null, null);
  let resolve!: (value: unknown) => void;
  service.inbox.mockImplementationOnce(() => new Promise((r) => (resolve = r)));
  const first = view.moreConversations();
  await view.moreConversations();
  expect(service.inbox).toHaveBeenCalledTimes(2);
  resolve({ items: [], total: 40, page: 1, size: 20 });
  await first;
  expect(view.loadingInboxPage()).toBe(false);
});

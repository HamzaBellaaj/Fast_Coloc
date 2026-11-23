import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Api } from '../../core/api';
import { NotificationSettings } from './notification-settings';
let request: any, settings: NotificationSettings;
beforeEach(() => {
  request = vi
    .fn()
    .mockResolvedValue({ newMessageEmail: true, deliveryAvailable: true, deliveryMode: 'local' });
  TestBed.configureTestingModule({ providers: [{ provide: Api, useValue: { request } }] });
  settings = TestBed.runInInjectionContext(() => new NotificationSettings());
});
afterEach(() => TestBed.resetTestingModule());
it('loads the saved preference and local delivery mode', async () => {
  await settings.load();
  expect(settings.enabled).toBe(true);
  expect(settings.preferences()?.deliveryMode).toBe('local');
  expect(settings.loading()).toBe(false);
});
it('persists an opt-out through the shared backend API', async () => {
  await settings.load();
  settings.enabled = false;
  request.mockResolvedValueOnce({
    newMessageEmail: false,
    deliveryAvailable: true,
    deliveryMode: 'local',
  });
  await settings.save();
  expect(request).toHaveBeenLastCalledWith('PATCH', '/notification-preferences', {
    newMessageEmail: false,
  });
  expect(settings.saved()).toBe(true);
  expect(settings.preferences()?.newMessageEmail).toBe(false);
});
it('keeps the edited value when saving fails and allows retry', async () => {
  await settings.load();
  settings.enabled = false;
  request.mockRejectedValueOnce(new Error('Offline'));
  await settings.save();
  expect(settings.enabled).toBe(false);
  expect(settings.preferences()?.newMessageEmail).toBe(true);
  expect(settings.saving()).toBe(false);
  expect(settings.error()).toBe('Offline');
});
it('does not submit before preferences have loaded', async () => {
  await settings.save();
  expect(request).not.toHaveBeenCalled();
});

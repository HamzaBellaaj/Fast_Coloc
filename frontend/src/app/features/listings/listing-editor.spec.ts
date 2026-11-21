import { afterEach, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { ListingEditor } from './listing-editor';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Feedback } from '../../core/feedback';
afterEach(() => TestBed.resetTestingModule());

function rentEditor(request = vi.fn()) {
  TestBed.configureTestingModule({
    providers: [
      { provide: Api, useValue: { request } },
      { provide: Auth, useValue: { profile: () => ({ id: 'owner', completedAt: '2026-11-01' }) } },
      { provide: Feedback, useValue: { show: vi.fn(), error: vi.fn() } },
      { provide: Router, useValue: { navigate: vi.fn() } },
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {
            data: {},
            paramMap: convertToParamMap({}),
            queryParamMap: convertToParamMap({}),
          },
        },
      },
    ],
  });
  const editor = TestBed.runInInjectionContext(() => new ListingEditor());
  editor.busy.set(false);
  editor.areas.set([{ id: 1, locality: 'Ariana', governorate: 'Ariana', governorate_id: 1 }]);
  Object.assign(editor.form, {
    localityId: 1,
    propertyType: 'S+2',
    description: '85 m², meublé',
    rent: 900,
  });
  return editor;
}

it('estimates an unsaved listing with all selected photos and applies the price only on demand', async () => {
  const result = {
    success: true,
    estimation: {
      disponible: true,
      loyer_min_tnd: 1100,
      loyer_max_tnd: 1400,
      loyer_conseille_tnd: 1250,
      fiabilite: 'moyenne',
      explication: 'Références du marché',
    },
  };
  let resolve!: (value: unknown) => void;
  const request = vi.fn().mockImplementation(() => new Promise((r) => (resolve = r)));
  const editor = rentEditor(request);
  editor.pendingPhotos = [
    new File(['photo'], 'room.jpg', { type: 'image/jpeg' }),
    new File(['second'], 'kitchen.jpg', { type: 'image/jpeg' }),
  ];
  const pending = editor.estimateRent();
  expect(editor.estimating()).toBe(true);
  await editor.estimateRent();
  expect(request).toHaveBeenCalledTimes(1);
  expect(request.mock.calls[0].slice(0, 2)).toEqual(['POST', '/ai/rent-estimate']);
  const body = request.mock.calls[0][2] as FormData;
  expect([...body.keys()]).toEqual(['photo', 'photo', 'logement']);
  expect(body.getAll('photo')).toHaveLength(2);
  const payload = await new Promise<string>((resolve) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.readAsText(body.get('logement') as Blob);
  });
  expect(JSON.parse(payload)).toMatchObject({
    localisation: 'Ariana · Ariana',
    type: 'S+2',
    description: '85 m², meublé',
  });
  expect(JSON.parse(payload)).not.toHaveProperty('rent');
  resolve(result);
  await pending;
  expect(editor.form.rent).toBe(900);
  expect(editor.saved()).toBeNull();
  editor.estimate.set(null); // Ignorer preserves the user's value.
  expect(editor.form.rent).toBe(900);
  editor.estimate.set(result);
  editor.applyEstimate();
  expect(editor.form.rent).toBe(1250);
  editor.form.rent = 1300;
  expect(editor.form.rent).toBe(1300);
});

it('requires a photo and allows retry after a workflow failure without changing rent', async () => {
  const request = vi.fn().mockRejectedValue({ error: { message: 'Service indisponible' } });
  const editor = rentEditor(request);
  await editor.estimateRent();
  expect(request).not.toHaveBeenCalled();
  expect(editor.estimateError).toContain('photo');
  editor.pendingPhotos = [
    new File(['photo'], 'room.jpg', { type: 'image/jpeg' }),
    new File(['second'], 'kitchen.jpg', { type: 'image/jpeg' }),
  ];
  await editor.estimateRent();
  expect(editor.estimating()).toBe(false);
  expect(editor.estimateError).toBe('Service indisponible');
  expect(editor.form.rent).toBe(900);
  await editor.estimateRent();
  expect(request).toHaveBeenCalledTimes(2);
});

it('saves the current request before publication and keeps it editable if publication fails', async () => {
  const failure = new Error('Publication indisponible');
  const request = vi
    .fn()
    .mockResolvedValueOnce({
      id: 'draft',
      kind: 'REQUEST',
      title: 'Ma recherche de colocation',
      description: 'Je recherche une colocation calme proche des transports.',
      locality_id: 1,
      street: '',
      latitude: null,
      longitude: null,
      rent: 600,
      capacity: 1,
      property_type: 'Appartement',
      roommate_type: 'Étudiant',
      equipment: ['WiFi'],
      available_from: '2026-12-01',
      available_until: null,
      publication_days: 30,
      version: 1,
      status: 'DRAFT',
      photos: [],
    })
    .mockRejectedValueOnce(failure);
  const error = vi.fn();
  const navigate = vi.fn();
  TestBed.configureTestingModule({
    providers: [
      { provide: Api, useValue: { request } },
      { provide: Auth, useValue: { profile: () => ({ id: 'owner', completedAt: '2026-11-01' }) } },
      { provide: Feedback, useValue: { show: vi.fn(), error } },
      { provide: Router, useValue: { navigate } },
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {
            data: {},
            paramMap: convertToParamMap({}),
            queryParamMap: convertToParamMap({}),
          },
        },
      },
    ],
  });
  const editor = TestBed.runInInjectionContext(() => new ListingEditor());
  editor.busy.set(false);
  Object.assign(editor.form, {
    kind: 'REQUEST',
    title: 'Ma recherche de colocation',
    description: 'Je recherche une colocation calme proche des transports.',
    localityId: 1,
    rent: 600,
    propertyType: 'Appartement',
    roommateType: 'Étudiant',
    equipment: 'WiFi',
    availableFrom: '2026-12-01',
  });
  editor.step = 4;
  await editor.publish();
  expect(request.mock.calls.map((call) => call.slice(0, 2))).toEqual([
    ['POST', '/listings'],
    ['POST', '/listings/draft/publish'],
  ]);
  expect(request.mock.calls[0][2]).toMatchObject({
    kind: 'REQUEST',
    latitude: null,
    longitude: null,
    rent: 600,
  });
  expect(editor.saved()?.version).toBe(1);
  expect(editor.form.title).toBe('Ma recherche de colocation');
  expect(editor.step).toBe(4);
  expect(editor.busy()).toBe(false);
  expect(error).toHaveBeenCalledWith(failure);
  expect(navigate).not.toHaveBeenCalled();
});

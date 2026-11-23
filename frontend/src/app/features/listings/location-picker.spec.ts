import { afterEach, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Api } from '../../core/api';
import { LocationPicker, GeoPosition } from './location-picker';

afterEach(() => TestBed.resetTestingModule());
function picker(request = vi.fn()) {
  TestBed.configureTestingModule({ providers: [{ provide: Api, useValue: { request } }] });
  return TestBed.runInInjectionContext(() => new LocationPicker());
}
const land: GeoPosition = {
  latitude: 36.8,
  longitude: 10.18,
  address: 'Rue de test, Tunis',
  approximate: true,
};
it('updates all fields only after a terrestrial point is accepted, retaining the last valid point on water/network failure', async () => {
  const request = vi
    .fn()
    .mockResolvedValueOnce(land)
    .mockRejectedValueOnce({ error: { message: 'Dans l’eau' } })
    .mockRejectedValueOnce({ status: 503 });
  const map = picker(request);
  const positions: GeoPosition[] = [];
  map.position.subscribe((value) => positions.push(value));
  await map.validate(36.8, 10.18);
  expect(positions.at(-1)).toEqual(land);
  map.address = land.address;
  await map.validate(36.82, 10.25);
  expect(positions.at(-1)).toEqual(land);
  expect(map.message).toBe('Dans l’eau');
  await map.validate(36.83, 10.26);
  expect(positions.at(-1)).toEqual(land);
  expect(map.busy).toBe(false);
});
it('requires explicit selection when address search has several results', async () => {
  const request = vi
    .fn()
    .mockResolvedValueOnce([land, { ...land, address: 'Autre rue' }])
    .mockResolvedValueOnce(land);
  const map = picker(request);
  const emit = vi.spyOn(map.position, 'emit');
  map.address = 'Rue de test';
  await map.search();
  expect(map.results).toHaveLength(2);
  expect(emit).not.toHaveBeenCalled();
  expect(request).toHaveBeenCalledTimes(1);
  await map.validate(map.results[0].latitude, map.results[0].longitude);
  expect(emit).toHaveBeenCalledWith(land);
});
it('discards a late response after a newer map selection', async () => {
  let complete!: (position: GeoPosition) => void;
  const request = vi
    .fn()
    .mockImplementationOnce(() => new Promise<GeoPosition>((resolve) => (complete = resolve)))
    .mockResolvedValueOnce({ ...land, longitude: 10.19 });
  const map = picker(request);
  const emit = vi.spyOn(map.position, 'emit');
  const old = map.validate(36.8, 10.18);
  await map.validate(36.8, 10.19);
  complete(land);
  await old;
  expect(emit).toHaveBeenCalledTimes(1);
  expect(emit).toHaveBeenCalledWith({ ...land, longitude: 10.19 });
});

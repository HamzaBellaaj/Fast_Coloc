import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Details,
  Profile,
  Country,
  Place,
  AdminUser,
  Identity,
  Geography,
  Personal,
  Contacts,
  Preferences,
} from './models';

@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);
  request<T>(method: string, path: string, body?: unknown, timeout = 45000): Promise<T> {
    return firstValueFrom(
      this.http.request<T>(method, environment.apiUrl + path, { body, timeout }),
    );
  }
  sync = () => this.request<Profile>('POST', '/users/sync', {});
  details = () => this.request<Details>('GET', '/users/me/details');
  countries = () => this.request<Country[]>('GET', '/geography/countries');
  governorates = (country: string) =>
    this.request<Place[]>(
      'GET',
      '/geography/governorates?countryCode=' + encodeURIComponent(country),
    );
  localities = (id: number) =>
    this.request<Place[]>('GET', '/geography/localities?governorateId=' + id);
  step = (
    step: number,
    body: Identity | Geography | Personal | Contacts | { photoIds: string[] },
  ) => this.request<Details>('PUT', '/onboarding/steps/' + step, body);
  complete = () => this.request<Details>('POST', '/onboarding/complete');
  preferences = (body: Preferences) => this.request<Details>('PUT', '/users/me/preferences', body);
  signed = (id: string) =>
    this.request<{ url: string; expiresIn: number }>(
      'GET',
      '/profile-photos/' + id + '/signed-url',
    );
  users = (offset: number, search: string, status: string) =>
    this.request<AdminUser[]>(
      'GET',
      `/admin/users?limit=20&offset=${offset}&search=${encodeURIComponent(search)}${status ? '&status=' + status : ''}`,
    );
}

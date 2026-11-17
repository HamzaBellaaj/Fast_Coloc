import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { Auth } from './auth';
import { Api } from './api';
import { Session } from './models';
import { sessionInterceptor } from './interceptor';
import { adminGuard, sessionGuard } from './guards';
const session: Session = {
  access_token: 'test-access',
  refresh_token: 'test-refresh',
  expires_in: 3600,
  token_type: 'bearer',
};
describe('Session et autorisations Web', () => {
  let auth: Auth, http: HttpTestingController;
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([sessionInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(Auth);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
    sessionStorage.clear();
  });
  it('ne stocke la session que dans l’onglet', () => {
    auth.accept(session);
    expect(sessionStorage.getItem('fastcoloc.session.v1')).toContain('test-access');
    expect(localStorage.length).toBe(0);
    auth.clear();
    expect(sessionStorage.length).toBe(0);
  });
  it('sérialise les refresh concurrents et conserve le token tournant', async () => {
    auth.accept({ ...session, expires_at: 0 });
    const a = auth.token(),
      b = auth.token();
    const r = http.expectOne('/api/v1/auth/refresh');
    expect(r.request.body).toEqual({ refreshToken: 'test-refresh' });
    r.flush({ ...session, access_token: 'rotated', refresh_token: 'rotated-refresh' });
    expect(await a).toBe('rotated');
    expect(await b).toBe('rotated');
    expect(auth.session()?.refresh_token).toBe('rotated-refresh');
  });
  it('purge une session dont le refresh a été refusé', async () => {
    auth.accept({ ...session, expires_at: 0 });
    const p = auth.token();
    const rejected = expect(p).rejects.toBeDefined();
    http.expectOne('/api/v1/auth/refresh').flush({}, { status: 400, statusText: 'Bad Request' });
    await rejected;
    expect(auth.session()).toBeNull();
  });
  it('n’attache aucun Bearer à une URL externe', async () => {
    auth.accept(session);
    const { HttpClient } = await import('@angular/common/http');
    TestBed.inject(HttpClient).get('https://example.test/image').subscribe();
    const r = http.expectOne('https://example.test/image');
    expect(r.request.headers.has('Authorization')).toBe(false);
    r.flush({});
  });
  it('ajoute le JWT aux appels métier', async () => {
    auth.accept(session);
    const p = TestBed.inject(Api).details();
    await Promise.resolve();
    const r = http.expectOne('/api/v1/users/me/details');
    expect(r.request.headers.get('Authorization')).toBe('Bearer test-access');
    r.flush({});
    await p;
  });
  it('refuse une page privée sans session', async () => {
    const result = await TestBed.runInInjectionContext(() =>
      sessionGuard({} as never, {} as never),
    );
    expect(TestBed.inject(Router).serializeUrl(result as import('@angular/router').UrlTree)).toBe(
      '/login',
    );
  });
  it('conserve les filtres de la page demandée avant connexion', async () => {
    const url = '/favoris?sort=RENT_ASC&page=2';
    const result = await TestBed.runInInjectionContext(() =>
      sessionGuard({} as never, { url } as never),
    );
    expect((result as import('@angular/router').UrlTree).queryParams['returnUrl']).toBe(url);
    expect(auth.takeDestination()).toBe(url);
    expect(auth.takeDestination()).toBe('/');
  });
  it('refuse les destinations externes ou les routes d’authentification en retour', () => {
    for (const url of [
      'https://example.test',
      '//example.test',
      '/login',
      '/auth/callback',
      '/annonces\\external',
    ]) {
      auth.rememberDestination(url);
      expect(auth.takeDestination()).toBe('/');
    }
  });
  it('redirige vers la connexion si le refresh échoue pendant une action', async () => {
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    auth.accept({ ...session, expires_at: 0 });
    const request = TestBed.inject(Api).details();
    const rejected = expect(request).rejects.toBeDefined();
    http.expectOne('/api/v1/auth/refresh').flush({}, { status: 400, statusText: 'Bad Request' });
    await rejected;
    expect(navigation).toHaveBeenCalledWith('/login');
    expect(auth.session()).toBeNull();
  });
  it('préserve une identité déjà validée après une tentative d’inscription en doublon', async () => {
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    auth.accept(session);
    auth.draftIdentity = {
      email: 'same@example.test',
      value: { firstName: 'Brouillon', lastName: 'À ignorer', age: 25 },
    };
    const completion = auth.finish();
    await Promise.resolve();
    http
      .expectOne('/api/v1/auth/session')
      .flush({ emailConfirmed: true, email: 'same@example.test' });
    await Promise.resolve();
    await Promise.resolve();
    http.expectOne('/api/v1/users/sync').flush({
      id: 'own-user',
      firstName: 'Identité conservée',
      lastName: 'Existante',
      age: 30,
      role: 'USER',
      status: 'ACTIVE',
      completedAt: '2026-11-12T00:00:00Z',
      validatedSteps: [1, 2, 3, 4, 5],
    });
    await completion;
    http.expectNone('/api/v1/onboarding/steps/1');
    expect(auth.profile()?.firstName).toBe('Identité conservée');
    expect(auth.draftIdentity).toBeNull();
  });
  it('vérifie ADMIN sur le serveur, même si le rôle local indique ADMIN', async () => {
    auth.accept(session);
    vi.spyOn(auth, 'restore').mockResolvedValue();
    const guard = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    await Promise.resolve();
    await Promise.resolve();
    http
      .expectOne('/api/v1/admin/access')
      .flush({ code: 'ACCESS_DENIED' }, { status: 403, statusText: 'Forbidden' });
    const result = await guard;
    expect(TestBed.inject(Router).serializeUrl(result as import('@angular/router').UrlTree)).toBe(
      '/profile',
    );
  });
  it('refuse un callback Google dont le state ne correspond pas', async () => {
    sessionStorage.setItem(
      'fastcoloc.pkce.v1',
      JSON.stringify({
        verifier: 'a'.repeat(64),
        state: 'expected',
        kind: 'google',
        created: Date.now(),
      }),
    );
    history.replaceState(null, '', '/auth/callback?code=example&state=wrong');
    await expect(auth.callback()).rejects.toThrow('ne correspond pas');
    expect(location.search).toBe('');
  });
});

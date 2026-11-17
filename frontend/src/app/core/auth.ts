import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Session, Profile, Identity } from './models';
import { environment } from '../../environments/environment';
import { Feedback } from './feedback';

interface AuthFlow {
  verifier: string;
  state: string;
  kind: 'google' | 'signup' | 'recovery';
  created: number;
}
const SESSION_KEY = 'fastcoloc.session.v1';
const FLOW_KEY = 'fastcoloc.pkce.v1';
export function base64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

@Injectable({ providedIn: 'root' })
export class Auth {
  private http = inject(HttpClient);
  private router = inject(Router);
  private feedback = inject(Feedback);
  session = signal<Session | null>(null);
  profile = signal<Profile | null>(null);
  recovery = signal(false);
  draftIdentity: { email: string; value: Identity } | null = null; // Memory only before e-mail confirmation.
  private refreshing: Promise<string> | null = null;
  private restoring: Promise<void> | null = null;
  private generation = 0;
  private raw<T>(method: string, path: string, body?: unknown) {
    return firstValueFrom(
      this.http.request<T>(method, environment.apiUrl + path, { body, timeout: 45000 }),
    );
  }
  accept(session: Session) {
    if (!session.access_token || !session.refresh_token)
      throw new Error('Lien invalide ou session absente. Reconnecte-toi.');
    const value = {
      ...session,
      expires_at: session.expires_at ?? Math.floor(Date.now() / 1000) + session.expires_in,
    };
    this.session.set(value);
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(value));
  }
  clear() {
    this.generation++;
    this.session.set(null);
    this.profile.set(null);
    this.recovery.set(false);
    this.draftIdentity = null;
    sessionStorage.removeItem(SESSION_KEY);
  }
  async restore() {
    if (this.restoring) return this.restoring;
    this.restoring = (async () => {
      try {
        const stored = sessionStorage.getItem(SESSION_KEY);
        if (!stored) return;
        this.accept(JSON.parse(stored) as Session);
        await this.token();
        await this.raw('GET', '/auth/session');
        this.profile.set(await this.raw<Profile>('POST', '/users/sync', {}));
      } catch {
        this.clear();
      }
    })();
    return this.restoring;
  }
  async token(force = false): Promise<string> {
    const session = this.session();
    if (!session) throw new Error('Reconnecte-toi pour continuer.');
    if (!force && (session.expires_at ?? 0) * 1000 > Date.now() + 45000)
      return session.access_token;
    if (this.refreshing) return this.refreshing;
    const generation = this.generation;
    this.refreshing = this.raw<Session>('POST', '/auth/refresh', {
      refreshToken: session.refresh_token,
    })
      .then((value) => {
        if (generation !== this.generation) throw new Error('Session fermée.');
        this.accept(value);
        return value.access_token;
      })
      .catch((error) => {
        if (generation === this.generation) this.clear();
        throw error;
      })
      .finally(() => (this.refreshing = null));
    return this.refreshing;
  }
  async login(email: string, password: string) {
    const draft =
      this.draftIdentity?.email.toLowerCase() === email.toLowerCase() ? this.draftIdentity : null;
    this.clear();
    this.draftIdentity = draft;
    this.accept(await this.raw<Session>('POST', '/auth/login', { email, password }));
    await this.finish();
  }
  async finish() {
    const session = await this.raw<{ emailConfirmed: boolean; email: string }>(
      'GET',
      '/auth/session',
    );
    if (!session.emailConfirmed) {
      this.clear();
      throw new Error('Confirme ton adresse e-mail avant de continuer.');
    }
    this.profile.set(await this.raw<Profile>('POST', '/users/sync', {}));
    if (
      this.draftIdentity &&
      this.draftIdentity.email.toLowerCase() === session.email.toLowerCase() &&
      !this.profile()?.validatedSteps.includes(1)
    ) {
      await this.raw('PUT', '/onboarding/steps/1', this.draftIdentity.value);
      this.draftIdentity = null;
      this.profile.set(await this.raw<Profile>('GET', '/users/me'));
    }
    this.draftIdentity = null;
    await this.router.navigateByUrl(this.destination());
  }
  destination() {
    const p = this.profile();
    return p?.completedAt
      ? '/profile'
      : '/register/' + ([1, 2, 3, 4, 5].find((n) => !p?.validatedSteps.includes(n)) ?? 5);
  }
  async logout() {
    try {
      if (this.session()) await this.raw('POST', '/auth/logout');
    } catch {
      this.feedback.show(
        'Session locale fermée. La révocation distante n’a pas pu être confirmée.',
        true,
      );
    } finally {
      this.clear();
      sessionStorage.removeItem(FLOW_KEY);
      await this.router.navigateByUrl('/login');
    }
  }
  async begin(kind: AuthFlow['kind']) {
    const flow: AuthFlow = {
      verifier: base64url(crypto.getRandomValues(new Uint8Array(48))),
      state: base64url(crypto.getRandomValues(new Uint8Array(32))),
      kind,
      created: Date.now(),
    };
    const challenge = base64url(
      new Uint8Array(
        await crypto.subtle.digest('SHA-256', new TextEncoder().encode(flow.verifier)),
      ),
    );
    sessionStorage.setItem(FLOW_KEY, JSON.stringify(flow));
    return { flow, challenge };
  }
  async google() {
    const { flow, challenge } = await this.begin('google');
    const result = await this.raw<{ authorizationUrl: string }>(
      'GET',
      '/auth/google?codeChallenge=' + challenge,
    );
    const url = new URL(result.authorizationUrl);
    // Supabase owns the provider state. Bind the app callback through redirect_to.
    const callback = new URL(
      url.searchParams.get('redirect_to') ?? location.origin + '/auth/callback',
    );
    callback.searchParams.set('state', flow.state);
    url.searchParams.set('redirect_to', callback.href);
    location.assign(url.href);
  }
  async callback() {
    const query = new URLSearchParams(location.search);
    const hash = new URLSearchParams(location.hash.slice(1));
    history.replaceState(null, '', location.pathname); // Remove codes/tokens before further navigation.
    const stored = sessionStorage.getItem(FLOW_KEY);
    const flow = stored ? (JSON.parse(stored) as AuthFlow) : null;
    const kind = query.get('type') ?? hash.get('type');
    if (query.has('error') || hash.has('error'))
      throw new Error('Ce lien est expiré, invalide ou la connexion a été annulée.');
    let session: Session;
    if (query.has('code')) {
      if (!flow || Date.now() - flow.created > 24 * 60 * 60 * 1000)
        throw new Error(
          'Ouvre ce lien dans le navigateur qui a initié la demande, ou reconnecte-toi.',
        );
      if (flow.kind === 'google' && query.get('state') !== flow.state)
        throw new Error(
          'Le retour Google ne correspond pas à la demande initiale. Relance la connexion.',
        );
      session = await this.raw<Session>('POST', '/auth/exchange', {
        code: query.get('code'),
        codeVerifier: flow.verifier,
      });
    } else if (query.has('token_hash') && ['signup', 'recovery', 'email'].includes(kind ?? '')) {
      session = await this.raw<Session>('POST', '/auth/verify', {
        tokenHash: query.get('token_hash'),
        type: kind,
      });
    } else if (
      hash.has('access_token') &&
      hash.has('refresh_token') &&
      ['signup', 'recovery', 'email'].includes(kind ?? '')
    ) {
      session = {
        access_token: hash.get('access_token')!,
        refresh_token: hash.get('refresh_token')!,
        expires_in: Number(hash.get('expires_in') ?? 3600),
        token_type: 'bearer',
      };
    } else
      throw new Error(
        'Aucun lien de confirmation ou de récupération valide. Connecte-toi après avoir confirmé ton e-mail.',
      );
    sessionStorage.removeItem(FLOW_KEY);
    this.accept(session);
    await this.raw('GET', '/auth/session');
    if (kind === 'recovery' || (query.has('code') && flow?.kind === 'recovery')) {
      this.recovery.set(true);
      await this.router.navigateByUrl('/reset-password');
    } else await this.finish();
  }
}

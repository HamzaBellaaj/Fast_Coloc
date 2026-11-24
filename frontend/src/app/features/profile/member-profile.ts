import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Api } from '../../core/api';
import { errorMessage } from '../../core/feedback';
import { BlockUser } from './block-user';

@Component({
  imports: [BlockUser],
  template: `
    <section class="section-card">
      @if (member(); as profile) {
        <h1 class="page-title">{{ profile.displayName }}</h1>
        <fc-block-user [target]="id" />
      } @else if (error()) {
        <p role="alert">{{ error() }}</p>
        <button class="btn ghost" type="button" (click)="load()">Réessayer</button>
      } @else {
        <p role="status">Chargement du profil…</p>
      }
    </section>
  `,
})
export class MemberProfile implements OnInit {
  private api = inject(Api);
  protected id = inject(ActivatedRoute).snapshot.paramMap.get('id')!;
  protected member = signal<{ displayName: string } | null>(null);
  protected error = signal('');

  ngOnInit() {
    void this.load();
  }

  protected async load() {
    this.error.set('');
    try {
      this.member.set(await this.api.request<{ displayName: string }>('GET', `/users/${this.id}/public-profile`));
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }
}

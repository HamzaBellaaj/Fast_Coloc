import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Api } from '../../core/api';
import { Feedback, errorMessage } from '../../core/feedback';

import { BlockConfirmation } from './block-confirmation';

interface BlockedUser {
  id: string;
  displayName: string;
  createdAt: string;
}

@Component({
  selector: 'fc-blocked-users',
  imports: [DatePipe, BlockConfirmation],
  template: `
    <section class="section-card spaced" [attr.aria-busy]="loading() || saving()">
      <h2 class="card-heading">Utilisateurs bloqués</h2>
      @if (loading()) {
        <p role="status">Chargement des utilisateurs bloqués…</p>
      } @else if (error()) {
        <p role="alert">{{ error() }}</p>
        <button class="btn ghost" type="button" (click)="load()">Réessayer</button>
      } @else {
        @for (user of users(); track user.id) {
          <div class="row wrap">
            <p><strong>{{ user.displayName }}</strong> · Bloqué le {{ user.createdAt | date: 'dd/MM/yyyy' }}</p>
            <button class="btn ghost small" type="button" [disabled]="saving()" (click)="selected.set(user); confirmation.open()">
              Débloquer
            </button>
          </div>
        } @empty {
          <p class="muted" role="status">Tu n’as bloqué aucun utilisateur.</p>
        }
      }
      <fc-block-confirmation #confirmation [unblock]="true" [name]="selected()?.displayName ?? ''"
        (confirmed)="unblock()" />
    </section>
  `,
})
export class BlockedUsers implements OnInit {
  private api = inject(Api);
  private feedback = inject(Feedback);
  protected selected = signal<BlockedUser | null>(null);
  protected users = signal<BlockedUser[]>([]);
  protected loading = signal(false);
  protected saving = signal(false);
  protected error = signal('');

  ngOnInit() {
    void this.load();
  }

  protected async load() {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    try {
      this.users.set(await this.api.request<BlockedUser[]>('GET', '/users/blocked'));
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }

  protected async unblock() {
    const user = this.selected();
    if (!user || this.saving()) return;
    this.saving.set(true);
    try {
      await this.api.request('DELETE', `/users/${user.id}/block`);
      this.users.update((users) => users.filter(({ id }) => id !== user.id));
      this.feedback.show('Utilisateur débloqué.');
    } catch (e) {
      this.feedback.error(e);
    } finally {
      this.saving.set(false);
    }
  }
}

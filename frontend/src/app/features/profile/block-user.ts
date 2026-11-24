import { Component, effect, inject, input, output, signal } from '@angular/core';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Feedback } from '../../core/feedback';
import { BlockConfirmation } from './block-confirmation';

@Component({
  selector: 'fc-block-user',
  imports: [BlockConfirmation],
  template: `
    @if (target() !== auth.profile()?.id) {
      @if (blocked()) {
        <p role="status">Tu as bloqué cet utilisateur.</p>
      }
      <button type="button" class="btn ghost small" [disabled]="saving()" (click)="confirmFor(confirmation)">
        {{ blocked() ? 'Débloquer' : 'Bloquer cet utilisateur' }}
      </button>
      <fc-block-confirmation #confirmation [unblock]="blocked()" (confirmed)="changeBlock()" />
    }
  `,
})
export class BlockUser {
  target = input.required<string>();
  blockedChange = output<boolean>();
  protected auth = inject(Auth);
  protected blocked = signal(false);
  protected saving = signal(false);
  private api = inject(Api);
  private feedback = inject(Feedback);
  private revision = 0;
  private requestedTarget: string | null = null;

  constructor() {
    effect(() => {
      const id = this.target();
      const version = ++this.revision;
      this.blocked.set(false);
      this.saving.set(false);
      void this.api.request<{ blocked: boolean }>('GET', `/users/${id}/block-status`)
        .then((status) => {
          if (version === this.revision) {
            this.blocked.set(status.blocked);
            this.blockedChange.emit(status.blocked);
          }
        })
        .catch(() => { /* The mutation reports errors and remains idempotent. */ });
    });
  }

  protected confirmFor(dialog: BlockConfirmation) {
    this.requestedTarget = this.target();
    dialog.open();
  }

  protected async changeBlock() {
    const id = this.target();
    if (id !== this.requestedTarget || id === this.auth.profile()?.id || this.saving()) return;
    const blocked = !this.blocked();
    const version = ++this.revision;
    this.saving.set(true);
    try {
      await this.api.request(blocked ? 'POST' : 'DELETE', `/users/${id}/block`);
      if (version !== this.revision) return;
      this.blocked.set(blocked);
      this.blockedChange.emit(blocked);
      this.feedback.show(blocked ? 'Utilisateur bloqué.' : 'Utilisateur débloqué.');
    } catch (e) {
      if (version === this.revision) this.feedback.error(e);
    } finally {
      if (version === this.revision) this.saving.set(false);
    }
  }
}

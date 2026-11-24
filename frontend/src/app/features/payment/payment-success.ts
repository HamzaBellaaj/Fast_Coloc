import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { errorMessage } from '../../core/feedback';

type Status = 'PENDING' | 'PAID' | 'FAILED' | 'CANCELLED';
const messages: Record<Status, string> = {
  PENDING: 'Le prestataire doit encore confirmer votre paiement.',
  PAID: 'Votre annonce bénéficie du boost Golden.',
  FAILED: 'Le paiement a été refusé ou la session a expiré.',
  CANCELLED: 'Le paiement a été interrompu.',
};

@Component({
  imports: [RouterLink],
  template: `
    <section class="success-box" [attr.aria-busy]="busy()">
      <h1 class="form-title">{{ title() }}</h1>
      <p role="status">{{ error() || (status() ? messages[status()!] : 'Vérification auprès du prestataire…') }}</p>
      <div class="form">
        @if (!busy() && (status() === 'PENDING' || error()) && id) {
          <button type="button" class="btn coral" (click)="verify()">Vérifier le paiement</button>
        }
        <a class="btn coral" routerLink="/mes-annonces">Voir mes annonces</a>
      </div>
    </section>
  `,
})
export class PaymentSuccess implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(Api);
  readonly id = this.route.snapshot.queryParamMap.get('id');
  readonly messages = messages;
  busy = signal(false);
  status = signal<Status | null>(null);
  error = signal('');

  ngOnInit() {
    void this.verify(this.route.snapshot.data['cancel'] === true);
  }

  title() {
    if (this.busy()) return 'Vérification du paiement';
    if (this.error()) return 'Vérification indisponible';
    switch (this.status()) {
      case 'PAID': return 'Paiement confirmé';
      case 'FAILED': return 'Paiement échoué';
      case 'CANCELLED': return 'Paiement annulé';
      default: return 'Paiement en attente';
    }
  }

  async verify(cancel = false) {
    if (this.busy()) return;
    if (!this.id) {
      this.error.set('Identifiant de paiement introuvable.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    try {
      const result = await this.api.request<{ status: Status }>(
        'POST', '/payments/capture', { id: this.id, cancel },
      );
      this.status.set(result.status);
      if (result.status !== 'PENDING' && sessionStorage.getItem('fastcoloc.boost-payment') === this.id) {
        sessionStorage.removeItem('fastcoloc.boost-payment');
      }
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}

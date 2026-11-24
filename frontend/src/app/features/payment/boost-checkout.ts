import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Api } from '../../core/api';

export type BoostMethod = 'PAYPAL' | 'CARD';

@Injectable({ providedIn: 'root' })
export class BoostCheckout {
  private api = inject(Api);
  private router = inject(Router);
  private requests = new Map<string, string>();

  async start(listingId: string, months: number, method: BoostMethod) {
    const key = JSON.stringify([listingId, months, method]);
    const requestId = this.requests.get(key) ?? crypto.randomUUID();
    this.requests.set(key, requestId);
    const result = await this.api.request<{ id: string; approveUrl: string | null }>(
      'POST', '/payments/create', { listingId, months, method, requestId },
    );
    this.requests.delete(key);
    sessionStorage.setItem('fastcoloc.boost-payment', result.id);
    if (result.approveUrl) {
      window.location.assign(result.approveUrl);
    } else {
      await this.router.navigate(['/payment/success'], { queryParams: { id: result.id } });
    }
  }
}

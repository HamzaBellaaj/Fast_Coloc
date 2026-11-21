import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { ListingCard } from './listing-card';
import { Listing } from './models';

export interface Recommendation {
  listing: Listing;
  score: number;
  raison: string;
}
interface RecommendationResponse {
  success: boolean;
  profileIncomplete: boolean;
  recommendations: Recommendation[];
}

@Component({
  imports: [RouterLink, ListingCard],
  templateUrl: './recommendations.html',
  styleUrl: './recommendations.css',
})
export class Recommendations implements OnInit, OnDestroy {
  private api = inject(Api);
  private destroyed = false;
  busy = signal(false);
  error = signal('');
  items = signal<Recommendation[]>([]);
  incomplete = signal(false);
  pending = signal(new Set<string>());
  ngOnInit() {
    void this.refresh();
  }
  ngOnDestroy() {
    this.destroyed = true;
  }
  async refresh() {
    if (this.busy() || this.pending().size) return;
    this.busy.set(true);
    this.error.set('');
    this.items.set([]);
    try {
      const result = await this.api.request<RecommendationResponse>(
        'POST',
        '/recommendations/ai',
        undefined,
        200000,
      );
      if (!this.destroyed) {
        this.items.set(result.recommendations);
        this.incomplete.set(result.profileIncomplete);
      }
    } catch (error: unknown) {
      if (!this.destroyed) this.error.set(this.message(error));
    } finally {
      if (!this.destroyed) this.busy.set(false);
    }
  }
  async favorite(item: Recommendation) {
    const row = item.listing;
    if (this.pending().has(row.id) || this.busy()) return;
    this.pending.update((ids) => new Set(ids).add(row.id));
    try {
      await this.api.request(row.favorite ? 'DELETE' : 'PUT', '/listings/' + row.id + '/favorite');
      if (!this.destroyed)
        this.items.update((items) =>
          items.map((value) =>
            value.listing.id === row.id
              ? { ...value, listing: { ...value.listing, favorite: !row.favorite } }
              : value,
          ),
        );
    } catch (error: unknown) {
      if (!this.destroyed) this.error.set(this.message(error));
    } finally {
      this.pending.update((ids) => {
        const next = new Set(ids);
        next.delete(row.id);
        return next;
      });
    }
  }
  private message(error: unknown) {
    const failure = error as { error?: { message?: string } };
    return (
      failure?.error?.message || 'Les recommandations sont indisponibles pour le moment. Réessaie.'
    );
  }
}

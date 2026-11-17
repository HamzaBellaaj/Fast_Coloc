import { Reveal } from '../../shared/reveal';
import { ApartmentStage } from './apartment-stage';
import { LiquidPopLink } from '../../shared/liquid-pop';
import { ListingCard } from '../listings/listing-card';
import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Feedback } from '../../core/feedback';
import { Area, Listing, ListingPage } from '../listings/models';
@Component({
  imports: [Reveal, ApartmentStage, LiquidPopLink, RouterLink, FormsModule, ListingCard],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home implements OnInit {
  private api = inject(Api);
  private router = inject(Router);
  auth = inject(Auth);
  private feedback = inject(Feedback);
  items = signal<Listing[]>([]);
  areas = signal<Area[]>([]);
  busy = signal(true);
  failed = signal(false);
  pending = signal<string[]>([]);
  region = '';
  budget = '';
  kind = '';
  get regions() {
    return [
      ...new Map(
        this.areas().map((a) => [a.governorate_id, { id: a.governorate_id, name: a.governorate }]),
      ).values(),
    ];
  }
  ngOnInit() {
    void this.load();
    void this.api
      .request<Area[]>('GET', '/listings/areas')
      .then((a) => this.areas.set(a))
      .catch(() => {});
  }
  async load() {
    this.busy.set(true);
    this.failed.set(false);
    try {
      this.items.set(
        (await this.api.request<ListingPage>('GET', '/listings?sort=NEWEST&size=3&page=0')).items,
      );
    } catch {
      this.failed.set(true);
    } finally {
      this.busy.set(false);
    }
  }
  search() {
    void this.router.navigate(['/annonces'], {
      queryParams: {
        governorateId: this.region || null,
        maxRent: this.budget || null,
        kind: this.kind || null,
        sort: 'NEWEST',
      },
    });
  }
  async favorite(row: Listing) {
    if (!this.auth.session()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: '/' } });
      return;
    }
    if (this.pending().includes(row.id)) return;
    this.pending.update((ids) => [...ids, row.id]);
    try {
      await this.api.request(row.favorite ? 'DELETE' : 'PUT', '/listings/' + row.id + '/favorite');
      this.items.update((rows) =>
        rows.map((r) => (r.id === row.id ? { ...r, favorite: !r.favorite } : r)),
      );
    } catch (e) {
      this.feedback.error(e);
    } finally {
      this.pending.update((ids) => ids.filter((id) => id !== row.id));
    }
  }
}

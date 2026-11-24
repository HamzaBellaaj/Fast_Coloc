import { ListingCard } from './listing-card';
import { Calendar } from '../../shared/calendar';
import { BoostCheckout, BoostMethod } from '../payment/boost-checkout';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Feedback } from '../../core/feedback';
import { Area, Listing, ListingPage, ListingMarker, ListingMapPage } from './models';
import { ListingMap } from './listing-map';
@Component({
  selector: 'fc-catalog',
  imports: [ListingCard, Calendar, FormsModule, RouterLink, DatePipe, ListingMap],
  templateUrl: './catalog.html',
})
export class Catalog implements OnInit {
  api = inject(Api);
  auth = inject(Auth);
  feedback = inject(Feedback);
  route = inject(ActivatedRoute);
  router = inject(Router);
  items = signal<Listing[]>([]);
  markers = signal<ListingMarker[]>([]);
  mapBusy = signal(false);
  mapFailed = signal(false);
  total = signal(0);
  busy = signal(false);
  failed = signal(false);
  areas = signal<Area[]>([]);
  mode = '';
  page = 0;
  filters: Record<string, string | number> = {
    q: '',
    kind: '',
    governorateId: '',
    localityId: '',
    minRent: '',
    maxRent: '',
    minCapacity: '',
    propertyType: '',
    roommateType: '',
    equipment: '',
    availableOn: '',
    sort: 'NEWEST',
  };
  pendingFavorites = signal<string[]>([]);
  private requestNumber = 0;
  private destroyRef = inject(DestroyRef);
  get regions() {
    return [
      ...new Map(
        this.areas().map((a) => [a.governorate_id, { id: a.governorate_id, name: a.governorate }]),
      ).values(),
    ];
  }
  get towns() {
    return this.areas().filter(
      (a) =>
        !this.filters['governorateId'] ||
        a.governorate_id === Number(this.filters['governorateId']),
    );
  }
  constructor() {
    this.destroyRef.onDestroy(() => this.requestNumber++);
  }
  async ngOnInit() {
    this.mode = this.route.snapshot.data['mode'] || '';
    try {
      this.areas.set(await this.api.request<Area[]>('GET', '/listings/areas'));
    } catch (e) {
      this.feedback.error(e);
    }
    if (this.destroyRef.destroyed) return;
    this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((p) => {
      this.filters = {
        q: '',
        kind: '',
        governorateId: '',
        localityId: '',
        minRent: '',
        maxRent: '',
        minCapacity: '',
        propertyType: '',
        roommateType: '',
        equipment: '',
        availableOn: '',
        sort: 'NEWEST',
        ...p,
      };
      this.page = Number(p['page'] || 0);
      void this.load();
    });
  }
  async load() {
    const generation = ++this.requestNumber;
    this.busy.set(true);
    this.failed.set(false);
    try {
      const params = new URLSearchParams();
      for (const [key, v] of Object.entries(this.filters))
        if (v !== '' && key !== 'page' && key !== 'equipment') params.set(key, String(v));
      for (const e of String(this.filters['equipment'] || '')
        .split(',')
        .map((v) => v.trim())
        .filter(Boolean))
        params.append('equipment', e);
      params.set('page', String(this.page));
      params.set('size', '12');
      const data = await this.api.request<ListingPage>(
        'GET',
        '/listings' + (this.mode ? '/' + this.mode : '') + '?' + params,
      );
      if (generation === this.requestNumber) {
        this.items.set(data.items);
        this.total.set(data.total);
        if (!this.mode && this.auth.session()) void this.loadMap(params, generation);
      }
    } catch (e) {
      if (generation === this.requestNumber) {
        this.items.set([]);
        this.failed.set(true);
        this.feedback.error(e);
      }
    } finally {
      if (generation === this.requestNumber) this.busy.set(false);
    }
  }
  search(page = 0) {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...this.filters, page },
    });
  }
  private async loadMap(filters: URLSearchParams, generation: number) {
    this.mapBusy.set(true);
    this.mapFailed.set(false);
    this.markers.set([]);
    try {
      const params = new URLSearchParams(filters);
      params.set('size', '50');
      let page = 0;
      const collected = new Map<string, ListingMarker>();
      while (true) {
        params.set('page', String(page));
        const result = await this.api.request<ListingMapPage>('GET', '/listings/map?' + params);
        if (generation !== this.requestNumber) return;
        for (const row of result.items) collected.set(row.id, row);
        if ((page + 1) * result.size >= result.total || !result.items.length) break;
        page++;
      }
      this.markers.set([...collected.values()]);
    } catch {
      if (generation === this.requestNumber) this.mapFailed.set(true);
    } finally {
      if (generation === this.requestNumber) this.mapBusy.set(false);
    }
  }
  clear() {
    this.filters = { q: '', kind: '', sort: 'NEWEST' };
    this.search();
  }
  zone(bounds: Record<string, number>) {
    this.filters = { ...this.filters, ...bounds };
    this.search();
  }
  async favorite(row: Listing) {
    if (!this.auth.session()) {
      this.auth.rememberDestination(this.router.url);
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    if (this.pendingFavorites().includes(row.id)) return;
    this.pendingFavorites.update((ids) => [...ids, row.id]);
    try {
      await this.api.request(row.favorite ? 'DELETE' : 'PUT', '/listings/' + row.id + '/favorite');
      this.items.update((rows) =>
        rows.map((r) => (r.id === row.id ? { ...r, favorite: !r.favorite } : r)),
      );
      if (this.mode === 'favorites') await this.load();
    } catch (e) {
      this.feedback.error(e);
    } finally {
      this.pendingFavorites.update((ids) => ids.filter((id) => id !== row.id));
    }
  }
  async remove(row: Listing) {
    if (!window.confirm('Supprimer définitivement cette annonce et ses photos ?')) return;
    try {
      await this.api.request('DELETE', '/listings/' + row.id);
      await this.load();
    } catch (e) {
      this.feedback.error(e);
    }
  }
  async publish(row: Listing) {
    try {
      await this.api.request(
        'POST',
        '/listings/' + row.id + (row.status === 'DRAFT' ? '/publish' : '/renew'),
      );
      this.feedback.show(row.status === 'DRAFT' ? 'Annonce publiée.' : 'Annonce renouvelée.');
      await this.load();
    } catch (e) {
      this.feedback.error(e);
    }
  }
  private checkout = inject(BoostCheckout);
  get lastBoostPayment() {
    return sessionStorage.getItem('fastcoloc.boost-payment');
  }
  boostTarget = signal<Listing | null>(null);
  boostMonths = 1;
  boostMethod: BoostMethod = 'PAYPAL';
  boostBusy = signal(false);

  openBoost(row: Listing) {
    this.boostTarget.set(row);
    this.boostMonths = 1;
    this.boostMethod = 'PAYPAL';
  }

  closeBoost() {
    if (!this.boostBusy()) this.boostTarget.set(null);
  }

  async confirmBoost() {
    const listing = this.boostTarget();
    if (!listing || this.boostBusy()) return;
    this.boostBusy.set(true);
    try {
      await this.checkout.start(listing.id, Number(this.boostMonths), this.boostMethod);
    } catch (e) {
      this.feedback.error(e);
    } finally {
      this.boostBusy.set(false);
    }
  }
}

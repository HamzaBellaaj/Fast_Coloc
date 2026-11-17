import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { DecimalPipe, DatePipe } from '@angular/common';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Feedback } from '../../core/feedback';
import { Area, Listing, ListingPage, ListingMarker, ListingMapPage } from './models';
import { ListingMap } from './listing-map';
@Component({
  selector: 'fc-catalog',
  imports: [FormsModule, RouterLink, DecimalPipe, DatePipe, ListingMap],
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
  private requestNumber = 0;
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
  async ngOnInit() {
    this.mode = this.route.snapshot.data['mode'] || '';
    try {
      this.areas.set(await this.api.request<Area[]>('GET', '/listings/areas'));
    } catch (e) {
      this.feedback.error(e);
    }
    this.route.queryParams.subscribe((p) => {
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
      void this.router.navigateByUrl('/login');
      return;
    }
    try {
      await this.api.request(row.favorite ? 'DELETE' : 'PUT', '/listings/' + row.id + '/favorite');
      row.favorite = !row.favorite;
      this.items.update((a) => [...a]);
      if (this.mode === 'favorites') await this.load();
    } catch (e) {
      this.feedback.error(e);
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
}

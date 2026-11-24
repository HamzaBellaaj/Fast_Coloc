import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { DecimalPipe, DatePipe } from '@angular/common';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Feedback } from '../../core/feedback';
import { Listing } from './models';
import { ListingMap } from './listing-map';
@Component({
  imports: [RouterLink, DecimalPipe, DatePipe, ListingMap],
  templateUrl: './listing-detail.html',
  styleUrl: './listing-detail.css',
})
export class ListingDetail implements OnInit {
  api = inject(Api);
  auth = inject(Auth);
  feedback = inject(Feedback);
  route = inject(ActivatedRoute);
  router = inject(Router);
  row = signal<Listing | null>(null);
  busy = signal(true);
  changing = signal(false);
  get backLink() {
    const from = this.route.snapshot.queryParamMap.get('from') || '/annonces';
    return this.router.parseUrl(
      /^\/(annonces|favoris|mes-annonces|pour-toi)(\?|$)/.test(from) ? from : '/annonces',
    );
  }
  ngOnInit() {
    void this.load();
  }
  searchZone(bounds: Record<string, number>) {
    void this.router.navigate(['/annonces'], { queryParams: bounds });
  }
  async load() {
    this.busy.set(true);
    try {
      this.row.set(
        await this.api.request<Listing>(
          'GET',
          '/listings/' + this.route.snapshot.paramMap.get('id'),
        ),
      );
    } catch (e) {
      this.feedback.error(e);
    } finally {
      this.busy.set(false);
    }
  }
  async favorite() {
    const r = this.row();
    if (!r) return;
    this.changing.set(true);
    try {
      await this.api.request(r.favorite ? 'DELETE' : 'PUT', '/listings/' + r.id + '/favorite');
      this.row.set({ ...r, favorite: !r.favorite });
    } catch (e) {
      this.feedback.error(e);
    } finally {
      this.changing.set(false);
    }
  }
}

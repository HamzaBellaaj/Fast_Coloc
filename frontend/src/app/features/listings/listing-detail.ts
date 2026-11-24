import { BoostCheckout, BoostMethod } from '../payment/boost-checkout';
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
  template: `<a class="link" [routerLink]="backLink">← Retour aux annonces</a>
    @if (busy()) {
      <p role="status" class="notice">Ouverture de l’annonce…</p>
    }
    @if (row(); as r) {
      @if (
        route.snapshot.queryParamMap.get('published') === '1' &&
        r.author_id === auth.profile()?.id &&
        r.status === 'PUBLISHED'
      ) {
        <section class="publication-notice" role="status">
          <img src="/assets/fastcoloc-symbol.svg" width="56" height="56" alt="" />
          <div>
            <h2>Ton annonce est en ligne.</h2>
            <p>Elle est maintenant visible dans le catalogue. Retrouve-la dans Mes annonces.</p>
          </div>
          <a class="btn ghost" routerLink="/mes-annonces">Mes annonces ↗</a>
        </section>
      }

      <div class="content-head">
        <div>
          <span class="tag">{{
            r.kind === 'OFFER' ? 'UNE PLACE À PARTAGER' : 'UNE COLOC À TROUVER'
          }}</span>
          <h1 class="listing-title">{{ r.title }}</h1>
          <p class="muted">{{ r.locality }} · {{ r.governorate }}</p>
          @if (r.golden) {
            <span class="pill" style="background:#f59e0b;color:#fff;font-weight:600;">⭐ Annonce Golden</span>
          }
        </div>
        <span class="rent">{{ r.rent | number: '1.0-3' }} <small>TND / mois</small></span>
      </div>
      <div class="listing-gallery">
        @for (p of r.photos; track p.id) {
          <figure>
            <img [src]="p.url" [alt]="p.caption || r.title" />
            <figcaption>{{ p.caption }}</figcaption>
          </figure>
        } @empty {
          <div class="section-card empty-listings">⌂ Aucune photo fournie par l’auteur.</div>
        }
      </div>
      <div class="grid2">
        <section class="section-card">
          <h2>On te fait visiter ?</h2>
          <p class="listing-description">{{ r.description }}</p>
          <p>{{ r.property_type }} · {{ r.capacity }} personne(s)</p>
          <p>Adresse : {{ r.street }}, {{ r.locality }}</p>
          <p>Colocataire : {{ r.roommate_type || 'Aucune préférence indiquée' }}</p>
          <div class="row wrap">
            @for (e of r.equipment; track $index) {
              <span class="pill">{{ e }}</span>
            }
          </div>
        </section>
        <aside class="section-card">
          <h3>Les infos pratiques</h3>
          <p>
            Disponible dès le {{ r.available_from | date: 'dd/MM/yyyy' }}
            @if (r.available_until) {
              , jusqu’au {{ r.available_until | date: 'dd/MM/yyyy' }}
            }
            .
          </p>
          <p>
            Statut :
            {{
              r.is_hidden
                ? 'Masquée par l’administration'
                : r.status === 'DRAFT'
                  ? 'Brouillon'
                  : 'Publiée'
            }}
            @if (r.expires_at) {
              · jusqu’au {{ r.expires_at | date: 'dd/MM/yyyy' }}
            }
            .
          </p>
          <div class="row wrap">
            <button
              class="btn"
              [disabled]="changing() || r.status === 'DRAFT' || !!r.is_hidden"
              (click)="favorite()"
              [attr.aria-pressed]="r.favorite"
            >
              {{ r.favorite ? '♥ Retirer des favoris' : '♡ Ajouter aux favoris' }}
            </button>
            @if (r.author_id !== auth.profile()?.id && r.status === 'PUBLISHED' && !r.is_hidden) {
              <a class="btn ghost" [routerLink]="['/membres', r.author_id]">Voir le profil de l’auteur</a>
              <a class="btn coral" routerLink="/messages" [queryParams]="{ listing: r.id }">Contacter</a>
            }
            @if (r.author_id === auth.profile()?.id) {
              <a class="btn ghost" [routerLink]="['/annonces', r.id, 'modifier']"
                >Modifier mon annonce</a
              >
              @if (r.status === 'PUBLISHED' && !r.is_hidden) {
                <p>Boost Golden · 1 mois · 5 USD</p>
                <button class="btn coral" [disabled]="boostBusy()" (click)="boost('PAYPAL')">
                  Payer avec PayPal
                </button>
                <button class="btn ghost" [disabled]="boostBusy()" (click)="boost('CARD')">
                  Carte bancaire via PayPal — Visa / Mastercard
                </button>
                <p class="muted">PayPal affiche le paiement par carte sans compte selon votre éligibilité.</p>
              }
            }
          </div>
        </aside>
      </div>
      @if (r.kind === 'OFFER') {
        <section class="section-card map-section">
          <h3>À cet endroit</h3>
          <fc-listing-map [items]="[r]" (zone)="searchZone($event)" />
        </section>
      }
    } @else if (!busy()) {
      <section class="section-card">
        <h2>Annonce indisponible</h2>
        <p>Elle peut avoir expiré ou être réservée à son auteur.</p>
        <button class="btn" (click)="load()">Réessayer</button>
      </section>
    }`,
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

  boostBusy = signal(false);

  private checkout = inject(BoostCheckout);

  async boost(method: BoostMethod) {
    const listing = this.row();
    if (!listing || this.boostBusy()) return;
    this.boostBusy.set(true);
    try {
      await this.checkout.start(listing.id, 1, method);
    } catch (e) {
      this.feedback.error(e);
    } finally {
      this.boostBusy.set(false);
    }

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

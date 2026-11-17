import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { Listing } from './models';
@Component({
  selector: 'fc-listing-card',
  imports: [RouterLink, DecimalPipe],
  styleUrl: './listing-card.css',
  template: `<article class="preview-card">
    <div class="preview-photo">
      <a [routerLink]="['/annonces', row().id]" [queryParams]="from() ? { from: from() } : {}">
        @if (row().photos[0]; as photo) {
          <img
            [src]="photo.url"
            [alt]="photo.caption || row().title"
            loading="lazy"
            width="600"
            height="420"
          />
        } @else {
          <div class="no-photo">
            <img
              src="/assets/fastcoloc-symbol.svg"
              alt="Annonce sans photo"
              width="64"
              height="64"
            />
          </div>
        }</a
      ><span class="kind-label">{{
        row().kind === 'OFFER' ? 'Une place à partager' : 'Recherche de coloc'
      }}</span>
      @if (showFavorite()) {
        <button
          class="favorite-icon"
          [attr.aria-label]="row().favorite ? 'Retirer des favoris' : 'Ajouter aux favoris'"
          [attr.aria-pressed]="row().favorite"
          [disabled]="pending()"
          (click)="toggleFavorite.emit()"
        >
          {{ row().favorite ? '♥' : '♡' }}
        </button>
      }
    </div>
    <div class="preview-body">
      <p class="muted">{{ row().governorate }} · {{ row().locality }}</p>
      <a [routerLink]="['/annonces', row().id]" [queryParams]="from() ? { from: from() } : {}"
        ><h3>{{ row().title }}</h3></a
      >
      <p class="preview-info">{{ row().property_type }} · {{ row().capacity }} personne(s)</p>
      <p class="preview-rent">{{ row().rent | number: '1.0-3' }} <span>TND / mois</span></p>
      <ng-content />
    </div>
  </article>`,
})
export class ListingCard {
  row = input.required<Listing>();
  from = input('');
  pending = input(false);
  showFavorite = input(true);
  toggleFavorite = output<void>();
}

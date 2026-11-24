import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  ViewChild,
} from '@angular/core';
import * as L from 'leaflet';
import { ListingMarker } from './models';

@Component({
  selector: 'fc-listing-map',
  template: `<div #canvas class="listing-map" aria-label="Carte des annonces en Tunisie"></div>
    <button type="button" class="btn small ghost" (click)="searchZone()">
      Rechercher dans cette zone
    </button>
    <p class="tiny muted">
      Déplace ou agrandis la carte, puis recherche dans la zone visible. Chaque marqueur ouvre une
      fiche.
    </p>`,
})
export class ListingMap implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('canvas') canvas!: ElementRef<HTMLDivElement>;
  @Input() items: ListingMarker[] = [];
  @Output() zone = new EventEmitter<Record<string, number>>();
  private map?: L.Map;
  private markers?: L.LayerGroup;
  ngAfterViewInit() {
    this.map = L.map(this.canvas.nativeElement).setView([34.1, 9.6], 6);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      // Override the global no-referrer policy for tiles without sharing paths or tokens.
      referrerPolicy: 'strict-origin',
      attribution:
        '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
    }).addTo(this.map);
    this.markers = L.layerGroup().addTo(this.map);
    this.render();
  }
  ngOnChanges() {
    this.render();
  }
  private render() {
    if (!this.map || !this.markers) return;
    this.markers.clearLayers();
    const points: L.LatLngExpression[] = [];
    for (const row of this.items.filter((r) => r.kind === 'OFFER')) {
      const point: L.LatLngExpression = [Number(row.latitude), Number(row.longitude)];
      points.push(point);
      const link = document.createElement('a');
      link.href = '/annonces/' + row.id;
      link.textContent = row.title + ' · ' + row.rent + ' TND';
      const pin = L.divIcon({
        className: row.golden ? 'coloc-pin coloc-pin--golden' : 'coloc-pin',
        html: '⌂',
        iconSize: [32, 38],
        iconAnchor: [16, 38],
      });
      const label = row.golden ? row.title + ' · Annonce Golden' : row.title;
      L.marker(point, { icon: pin, alt: label, title: label, zIndexOffset: row.golden ? 1000 : 0 })
        .bindPopup(link)
        .addTo(this.markers);
    }
    if (points.length)
      this.map.fitBounds(L.latLngBounds(points), { padding: [30, 30], maxZoom: 13 });
  }
  searchZone() {
    if (this.map) {
      const b = this.map.getBounds();
      this.zone.emit({
        south: b.getSouth(),
        west: b.getWest(),
        north: b.getNorth(),
        east: b.getEast(),
      });
    }
  }
  ngOnDestroy() {
    this.map?.remove();
  }
}

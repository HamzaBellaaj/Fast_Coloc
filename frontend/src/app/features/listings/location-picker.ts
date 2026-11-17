import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  ViewChild,
} from '@angular/core';
import * as L from 'leaflet';
@Component({
  selector: 'fc-location-picker',
  template: `<p class="muted">
      Clique sur la carte pour placer précisément le logement ou la zone recherchée.
    </p>
    <div #canvas class="listing-map" aria-label="Choisir la position de l’annonce"></div>`,
})
export class LocationPicker implements AfterViewInit, OnDestroy {
  @ViewChild('canvas') canvas!: ElementRef<HTMLDivElement>;
  @Input() latitude: number | null = null;
  @Input() longitude: number | null = null;
  @Output() position = new EventEmitter<{ latitude: number; longitude: number }>();
  private map?: L.Map;
  private marker?: L.CircleMarker;
  ngAfterViewInit() {
    this.map = L.map(this.canvas.nativeElement).setView(
      this.latitude && this.longitude
        ? [Number(this.latitude), Number(this.longitude)]
        : [34.1, 9.6],
      this.latitude ? 12 : 6,
    );
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      // OSM requires a Referer. Send only the origin; keep auth URLs/parameters private.
      referrerPolicy: 'strict-origin',
      attribution:
        '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
    }).addTo(this.map);
    if (this.latitude && this.longitude) this.point(Number(this.latitude), Number(this.longitude));
    this.map.on('click', (e) => {
      this.point(e.latlng.lat, e.latlng.lng);
      this.position.emit({
        latitude: Number(e.latlng.lat.toFixed(6)),
        longitude: Number(e.latlng.lng.toFixed(6)),
      });
    });
  }
  private point(lat: number, lng: number) {
    if (this.marker) this.marker.remove();
    this.marker = L.circleMarker([lat, lng], { color: '#7138D8', fillOpacity: 0.65 }).addTo(
      this.map!,
    );
  }
  ngOnDestroy() {
    this.map?.remove();
  }
}

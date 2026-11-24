import { DesktopStatus } from '../../shared/desktop-status';
import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import * as L from 'leaflet';
import { Api } from '../../core/api';

export interface GeoPosition {
  latitude: number;
  longitude: number;
  address: string;
  approximate: boolean;
}
interface GeoPlace {
  latitude: number;
  longitude: number;
  address: string;
}
@Component({
  imports: [DesktopStatus],
  selector: 'fc-location-picker',
  template: `<div class="row wrap">
      <button
        type="button"
        class="btn small ghost"
        [disabled]="busy || address.trim().length < 3"
        (click)="search()"
        [fcStatus]="busy ? 'loading' : null"
      >
        Rechercher cette adresse
      </button>
      <button
        type="button"
        class="btn small ghost"
        [disabled]="busy"
        (click)="coordinates()"
        [fcStatus]="busy ? 'loading' : null"
      >
        Valider les coordonnées
      </button>
    </div>
    @if (results.length) {
      <div class="section-card" aria-label="Adresses proposées">
        <p>Choisis une adresse ; aucun résultat n’est sélectionné automatiquement.</p>
        @for (place of results; track $index) {
          <button
            type="button"
            class="btn small ghost"
            [disabled]="busy"
            (click)="choose(place)"
            [fcStatus]="busy ? 'loading' : null"
          >
            {{ place.address }}
          </button>
        }
      </div>
    }
    <p class="help" role="status" aria-live="polite" [fcStatus]="busy ? 'loading' : null">
      {{ busy ? 'Vérification de la position terrestre…' : message }}
    </p>
    <div #canvas class="listing-map" aria-label="Choisir la position terrestre de l’annonce"></div>
    <p class="tiny muted">
      © OpenStreetMap contributors · Validation terrestre : ESA WorldCover 2021, résolution 10 m.
      Adresse indicative à préciser.
    </p>`,
})
export class LocationPicker implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('canvas') canvas!: ElementRef<HTMLDivElement>;
  @Input() latitude: number | null = null;
  @Input() longitude: number | null = null;
  @Input() address = '';
  @Input() locality = '';
  @Output() position = new EventEmitter<GeoPosition>();
  @Output() busyChange = new EventEmitter<boolean>();
  private api = inject(Api);
  private map?: L.Map;
  private marker?: L.Marker;
  private committed: GeoPosition | null = null;
  private generation = 0;
  private timer?: ReturnType<typeof setTimeout>;
  private destroyed = false;
  busy = false;
  message =
    'Clique sur la carte, déplace le marqueur ou recherche ton adresse. Une position dans l’eau est refusée.';
  results: GeoPlace[] = [];
  ngAfterViewInit() {
    this.map = L.map(this.canvas.nativeElement).setView([34.1, 9.6], 6);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      referrerPolicy: 'strict-origin',
      maxZoom: 19,
      attribution:
        '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    })
      .on(
        'tileerror',
        () =>
          (this.message =
            'Fond de carte indisponible. La recherche et les coordonnées restent utilisables.'),
      )
      .addTo(this.map);
    if (this.validPair()) {
      this.committed = {
        latitude: Number(this.latitude),
        longitude: Number(this.longitude),
        address: this.address,
        approximate: true,
      };
      this.draw(true);
    }
    this.map.on(
      'click',
      (e: L.LeafletMouseEvent) => void this.validate(e.latlng.lat, e.latlng.lng),
    );
  }
  ngOnChanges(changes: SimpleChanges) {
    if (!this.map) return;
    if (!changes['latitude'] && !changes['longitude']) {
      this.results = [];
      return;
    }
    clearTimeout(this.timer);
    this.generation++;
    if (
      this.validPair() &&
      this.committed?.latitude === Number(this.latitude) &&
      this.committed?.longitude === Number(this.longitude)
    ) {
      this.setBusy(false);
      return;
    }
    this.setBusy(true);
    this.timer = setTimeout(() => this.coordinates(), 650);
  }
  private validPair() {
    return (
      this.latitude !== null &&
      this.longitude !== null &&
      Number.isFinite(Number(this.latitude)) &&
      Number.isFinite(Number(this.longitude))
    );
  }
  coordinates() {
    clearTimeout(this.timer);
    if (!this.validPair()) {
      this.generation++;
      this.message =
        'Renseigne les deux coordonnées numériques. La dernière position valide est conservée.';
      this.restore();
      this.setBusy(false);
      return;
    }
    void this.validate(Number(this.latitude), Number(this.longitude));
  }
  async search() {
    const generation = ++this.generation;
    this.setBusy(true);
    this.results = [];
    try {
      const places = await this.api.request<GeoPlace[]>(
        'GET',
        '/geography/search?q=' +
          encodeURIComponent(
            [this.address, this.locality].filter(Boolean).join(', ').slice(0, 250),
          ),
      );
      if (this.destroyed || generation !== this.generation) return;
      this.results = places;
      this.message = places.length
        ? 'Sélectionne explicitement une proposition.'
        : 'Aucune adresse trouvée. Précise la rue et la ville ou choisis un point sur la carte.';
    } catch (e) {
      if (generation === this.generation) this.failure(e);
    } finally {
      if (generation === this.generation && !this.destroyed) this.setBusy(false);
    }
  }
  choose(place: GeoPlace) {
    void this.validate(place.latitude, place.longitude);
  }
  async validate(latitude: number, longitude: number) {
    clearTimeout(this.timer);
    const generation = ++this.generation;
    this.setBusy(true);
    try {
      const result = await this.api.request<GeoPosition>('POST', '/geography/position', {
        latitude,
        longitude,
      });
      if (this.destroyed || generation !== this.generation) return;
      this.committed = result;
      this.results = [];
      this.draw(true);
      this.position.emit(result);
      this.message = result.approximate
        ? 'Adresse approximative : vérifie et précise le numéro ou la rue avant de publier.'
        : 'Position terrestre validée. Vérifie le numéro et la rue.';
      if (!result.address)
        this.message =
          'Position terrestre validée, adresse inconnue : précise l’adresse manuellement.';
    } catch (e) {
      if (generation === this.generation && !this.destroyed) {
        this.failure(e);
        this.restore();
      }
    } finally {
      if (generation === this.generation && !this.destroyed) this.setBusy(false);
    }
  }
  private restore() {
    this.draw(false);
    if (this.committed) this.position.emit({ ...this.committed, address: this.address });
  }
  private draw(center: boolean) {
    this.marker?.remove();
    this.marker = undefined;
    if (!this.committed || !this.map) return;
    const point: L.LatLngExpression = [this.committed.latitude, this.committed.longitude];
    this.marker = L.marker(point, {
      draggable: true,
      icon: L.divIcon({
        className: 'coloc-pin',
        html: '⌂',
        iconSize: [32, 38],
        iconAnchor: [16, 38],
      }),
    }).addTo(this.map);
    this.marker.on('dragend', () => {
      const point = this.marker!.getLatLng();
      this.draw(false);
      void this.validate(point.lat, point.lng);
    });
    if (center) this.map.setView(point, Math.max(this.map.getZoom(), 15));
  }
  private failure(e: unknown) {
    this.message =
      (e as { error?: { message?: string } })?.error?.message ||
      'Localisation indisponible. La position précédente est conservée. Réessaie.';
  }
  private setBusy(value: boolean) {
    this.busy = value;
    this.busyChange.emit(value);
  }
  ngOnDestroy() {
    this.destroyed = true;
    this.generation++;
    clearTimeout(this.timer);
    this.map?.remove();
    this.busyChange.emit(false);
  }
}

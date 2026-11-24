import { DesktopStatus } from '../../shared/desktop-status';
import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { EditorDrafts } from './editor-draft';
import { Calendar } from '../../shared/calendar';
import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Feedback } from '../../core/feedback';
import { Area, Listing } from './models';
import { LocationPicker } from './location-picker';
import { AdminNav } from '../admin/admin-nav';
@Component({
  imports: [
    DesktopStatus,
    DatePipe,
    DecimalPipe,
    NgTemplateOutlet,
    Calendar,
    FormsModule,
    RouterLink,
    LocationPicker,
    AdminNav,
  ],
  templateUrl: './listing-editor.html',
  styleUrl: './listing-editor.css',
})
export class ListingEditor implements OnInit, OnDestroy {
  api = inject(Api);
  auth = inject(Auth);
  feedback = inject(Feedback);
  route = inject(ActivatedRoute);
  router = inject(Router);
  busy = signal(true);
  geoBusy = signal(false);
  allowed = signal(true);
  saved = signal<Listing | null>(null);
  areas = signal<Area[]>([]);
  step = 0;
  regionId: number | null = null;
  flowError = '';
  get isRequest() {
    return this.form.kind === 'REQUEST';
  }
  get steps() {
    return this.isRequest
      ? ['Ton envie', 'Ma recherche', 'Zone et budget', 'Préférences', 'Vérification']
      : [
          'Ton envie',
          'Mon logement',
          'Loyer et disponibilité',
          'Équipements et photos',
          'Vérification',
        ];
  }
  get regions() {
    return [
      ...new Map(
        this.areas().map((a) => [a.governorate_id, { id: a.governorate_id, name: a.governorate }]),
      ).values(),
    ];
  }
  get towns() {
    return this.areas().filter((a) => a.governorate_id === this.regionId);
  }
  get areaName() {
    const a = this.areas().find((a) => a.id === this.form.localityId);
    return a ? a.governorate + ' · ' + a.locality : '';
  }
  back() {
    if (this.geoBusy()) return;
    this.flowError = '';
    this.step = Math.max(0, this.step - 1);
  }
  estimating = signal(false);
  estimateError = '';
  estimate = signal<RentEstimate | null>(null);
  pendingPhotos: File[] = [];
  photoPreviews = new Map<File, string>();
  private setPendingPhotos(files: File[]) {
    for (const [file, url] of this.photoPreviews) {
      if (!files.includes(file)) {
        URL.revokeObjectURL(url);
        this.photoPreviews.delete(file);
      }
    }
    for (const file of files) {
      if (!this.photoPreviews.has(file)) this.photoPreviews.set(file, URL.createObjectURL(file));
    }
    this.pendingPhotos = files;
    this.estimate.set(null);
  }
  removePendingPhoto(file: File) {
    this.setPendingPhotos(this.pendingPhotos.filter((photo) => photo !== file));
  }
  selectEstimatePhotos(event: Event): boolean {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files || []);
    input.value = '';
    if (!files.length) return false;
    if (
      files.some(
        (file) =>
          file.size === 0 ||
          file.size > 5242880 ||
          !['image/jpeg', 'image/png', 'image/webp'].includes(file.type),
      )
    ) {
      this.estimateError = 'Chaque photo doit être en JPEG, PNG ou WebP et peser au maximum 5 MiB.';
      return false;
    }
    const key = (file: File) => `${file.name}:${file.size}:${file.lastModified}`;
    const combined = [
      ...new Map([...this.pendingPhotos, ...files].map((file) => [key(file), file])).values(),
    ];
    if (combined.length + (this.saved()?.photos?.length || 0) > 10) {
      this.estimateError = '10 photos maximum par annonce. Sélectionne moins de photos.';
      return false;
    }
    this.setPendingPhotos(combined);
    this.estimateError = '';
    return true;
  }
  private async uploadPendingPhotos(id: string) {
    while (this.pendingPhotos.length) {
      const file = this.pendingPhotos[0];
      const photo = new FormData();
      photo.append('file', file);
      photo.append('caption', this.caption);
      this.saved.set(await this.api.request<Listing>('POST', '/listings/' + id + '/photos', photo));
      this.removePendingPhoto(file);
    }
    this.caption = '';
  }
  async estimateRent() {
    if (this.estimating() || this.isRequest) return;
    this.estimateError = '';
    this.estimate.set(null);
    if (!this.areaName || !this.form.propertyType.trim()) {
      this.estimateError = 'Précise la localité et le type du logement.';
      return;
    }
    if (!this.pendingPhotos.length && !this.saved()?.photos?.length) {
      this.estimateError = 'Ajoute au moins une photo du logement pour lancer l’analyse.';
      return;
    }
    this.estimating.set(true);
    try {
      const body = new FormData();
      for (const photo of this.saved()?.photos || []) {
        if (!photo.url)
          throw new Error('Photo indisponible. Actualise l’annonce avant de réessayer.');
        const response = await fetch(photo.url, { signal: AbortSignal.timeout(15000) });
        if (!response.ok)
          throw new Error('Photo indisponible. Actualise l’annonce avant de réessayer.');
        body.append('photo', await response.blob(), 'logement-' + photo.id);
      }
      for (const file of this.pendingPhotos) body.append('photo', file, file.name);
      body.append(
        'logement',
        new Blob(
          [
            JSON.stringify({
              localisation: this.areaName,
              type: this.form.propertyType,
              street: this.form.street,
              description: this.form.description,
              capacity: this.form.capacity,
              equipment: this.form.equipment
                .split(',')
                .map((v) => v.trim())
                .filter(Boolean),
            }),
          ],
          { type: 'application/json' },
        ),
      );
      const result = await this.api.request<RentEstimate>(
        'POST',
        '/ai/rent-estimate',
        body,
        200000,
      );
      if (
        !result.success ||
        !result.estimation?.disponible ||
        !Number.isFinite(result.estimation.loyer_conseille_tnd)
      )
        throw new Error('Estimation indisponible. Réessaie dans un instant.');
      if (!this.destroyed) this.estimate.set(result);
    } catch (e: unknown) {
      const failure = e as {
        error?: { message?: string };
        status?: number;
        name?: string;
        message?: string;
      };
      this.estimateError =
        failure?.error?.message ||
        (failure?.status === 0
          ? 'Connexion impossible. Vérifie le réseau et réessaie.'
          : failure?.name === 'TimeoutError'
            ? 'L’analyse prend trop de temps. Réessaie.'
            : failure?.status != null
              ? 'Le service d’estimation est indisponible. Réessaie.'
              : failure?.message || 'Le service d’estimation est injoignable. Réessaie.');
    } finally {
      this.estimating.set(false);
    }
  }
  applyEstimate() {
    const value = this.estimate()?.estimation.loyer_conseille_tnd;
    if (value != null) this.form.rent = Math.round(value);
    this.estimate.set(null);
  }
  caption = '';
  private drafts = inject(EditorDrafts);
  private baseline = '';
  private draftKey = '';
  private destroyed = false;
  reason = '';
  adminMode = !!this.route.snapshot.data['adminMode'];
  form = {
    kind: 'OFFER',
    title: '',
    description: '',
    localityId: null as number | null,
    street: '',
    latitude: null as number | null,
    longitude: null as number | null,
    rent: null as number | null,
    capacity: 1,
    propertyType: '',
    roommateType: '',
    equipment: '',
    availableFrom: '',
    availableUntil: '',
    publicationDays: 30,
  };
  async ngOnInit() {
    this.step = this.adminMode
      ? 1
      : this.route.snapshot.paramMap.get('id')
        ? Math.max(0, Math.min(4, Number(this.route.snapshot.queryParamMap.get('step') || 1)))
        : 0;
    this.drafts.forOwner(this.auth.profile()?.id ?? null);
    this.draftKey =
      (this.adminMode ? 'admin:' : '') + (this.route.snapshot.paramMap.get('id') || 'new');
    try {
      this.areas.set(await this.api.request<Area[]>('GET', '/listings/areas'));
      const id = this.route.snapshot.paramMap.get('id');
      if (id) {
        const r = await this.api.request<Listing>(
          'GET',
          (this.adminMode ? '/admin/listings/' : '/listings/') + id,
        );
        if (!this.adminMode && r.author_id !== this.auth.profile()?.id) {
          this.allowed.set(false);
          return;
        }
        this.accept(r);
      } else if (!this.auth.profile()?.completedAt) this.allowed.set(false);
    } catch (e) {
      this.allowed.set(false);
      this.feedback.error(e);
    } finally {
      this.baseline = JSON.stringify(this.form);
      const pending = this.drafts.get(this.draftKey);
      if (
        !this.destroyed &&
        this.allowed() &&
        pending &&
        pending.version === (this.saved()?.version ?? null)
      ) {
        this.form = { ...pending.form };
        this.step = pending.step;
        this.caption = pending.caption;
        this.setPendingPhotos(pending.photos || []);
      }
      this.regionId =
        this.areas().find((a) => a.id === this.form.localityId)?.governorate_id ?? null;
      this.busy.set(false);
    }
  }
  ngOnDestroy() {
    this.destroyed = true;
    for (const url of this.photoPreviews.values()) URL.revokeObjectURL(url);
    if (
      !this.adminMode &&
      this.allowed() &&
      this.baseline &&
      (JSON.stringify(this.form) !== this.baseline || this.pendingPhotos.length > 0)
    )
      this.drafts.set(this.draftKey, {
        form: { ...this.form },
        step: this.step,
        version: this.saved()?.version ?? null,
        caption: this.caption,
        photos: [...this.pendingPhotos],
      });
    else this.drafts.remove(this.draftKey);
  }
  private accept(r: Listing) {
    this.saved.set(r);
    this.form = {
      kind: r.kind,
      title: r.title,
      description: r.description,
      localityId: Number(r.locality_id),
      street: r.street,
      latitude: r.latitude == null ? null : Number(r.latitude),
      longitude: r.longitude == null ? null : Number(r.longitude),
      rent: Number(r.rent),
      capacity: r.capacity,
      propertyType: r.property_type,
      roommateType: r.roommate_type,
      equipment: r.equipment.join(', '),
      availableFrom: r.available_from,
      availableUntil: r.available_until || '',
      publicationDays: r.publication_days,
    };
  }
  validateStep(step: number): boolean {
    const f = this.form;
    let error = '';
    if (
      step === 1 &&
      (f.title.trim().length < 5 ||
        f.title.trim().length > 160 ||
        f.description.trim().length < 20 ||
        !f.propertyType.trim() ||
        !Number.isInteger(f.capacity) ||
        f.capacity < 1 ||
        f.capacity > 50)
    )
      error =
        'Indique un titre de 5 à 160 caractères, une présentation d’au moins 20 caractères, un type de logement et un nombre de personnes entre 1 et 50.';
    if (
      ((!this.isRequest && step === 1) || (this.isRequest && step === 2)) &&
      (!f.localityId ||
        (!this.isRequest &&
          (!f.street.trim() ||
            f.latitude == null ||
            f.longitude == null ||
            f.latitude < 30 ||
            f.latitude > 38 ||
            f.longitude < 7 ||
            f.longitude > 12)))
    )
      error = this.isRequest
        ? 'Choisis le gouvernorat et la localité souhaités.'
        : 'Précise la localité, l’adresse et la position du logement en Tunisie.';
    if (
      step === 2 &&
      (f.rent == null ||
        !Number.isFinite(f.rent) ||
        f.rent < 0 ||
        f.rent > 999999999.999 ||
        Math.abs(f.rent * 1000 - Math.round(f.rent * 1000)) > 0.0001)
    )
      error = 'Indique un montant positif en TND, avec au maximum trois décimales.';
    if (
      ((!this.isRequest && step === 2) || (this.isRequest && step === 3)) &&
      (!f.availableFrom || (f.availableUntil && f.availableUntil < f.availableFrom))
    )
      error = 'Choisis une date de début et une date de fin postérieure ou identique.';
    if (
      step === 3 &&
      (f.equipment.split(',').filter((v) => v.trim()).length > 30 ||
        f.equipment.split(',').some((v) => v.trim().length > 80))
    )
      error = 'Indique au maximum 30 équipements de 80 caractères chacun.';
    if (
      step === 4 &&
      (!Number.isInteger(f.publicationDays) || f.publicationDays < 1 || f.publicationDays > 365)
    )
      error = 'Choisis une durée de publication entre 1 et 365 jours.';
    this.flowError = error;
    return !error;
  }
  validateAll() {
    for (let n = 1; n <= 4; n++)
      if (!this.validateStep(n)) {
        this.step = n;
        return false;
      }
    return true;
  }
  next(f: NgForm) {
    if (this.geoBusy()) return;
    if (this.adminMode) {
      if (f.invalid) {
        f.control.markAllAsTouched();
        return;
      }
      this.step = Math.min(3, this.step + 1);
      return;
    }
    if (!this.validateStep(this.step) || f.invalid) {
      f.control.markAllAsTouched();
      if (!this.flowError) this.flowError = 'Vérifie les champs indiqués avant de continuer.';
      return;
    }
    this.step = Math.min(4, this.step + 1);
  }
  async save(f?: NgForm, publishAfter = false) {
    if (this.busy() || this.geoBusy() || (!this.adminMode && !this.validateAll())) return;
    if (f?.invalid || (this.adminMode && !this.reason.trim())) {
      f?.control.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    try {
      const body = {
        ...this.form,
        localityId: Number(this.form.localityId),
        equipment: this.form.equipment
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean),
        availableUntil: this.form.availableUntil || null,
        version: this.saved()?.version ?? null,
      };
      const id = this.saved()?.id;
      const r = await this.api.request<Listing>(
        id ? 'PUT' : 'POST',
        (this.adminMode ? '/admin/listings' : '/listings') + (id ? '/' + id : ''),
        this.adminMode ? { listing: body, reason: this.reason.trim() } : body,
      );
      this.accept(r);
      if (!this.isRequest) await this.uploadPendingPhotos(r.id);
      this.baseline = JSON.stringify(this.form);
      this.drafts.remove(this.draftKey);
      this.feedback.show(
        this.adminMode
          ? 'Correction administrative enregistrée.'
          : 'Annonce enregistrée. Tes informations sont conservées.',
      );
      if (this.adminMode) {
        void this.router.navigate(['/admin/annonces', r.id]);
        return;
      }
      if (publishAfter) {
        await this.publishSaved();
        return;
      }
      if (!id)
        void this.router.navigate(['/annonces', r.id, 'modifier'], {
          replaceUrl: true,
          queryParams: this.adminMode ? {} : { step: this.step },
        });
    } catch (e) {
      this.feedback.error(e);
    } finally {
      this.busy.set(false);
    }
  }
  async publish() {
    if (!this.adminMode) {
      await this.save(undefined, true);
      return;
    }
    await this.publishSaved();
  }
  private async publishSaved() {
    const r = this.saved();
    if (!r) return;
    this.busy.set(true);
    try {
      await this.api.request(
        'POST',
        '/listings/' + r.id + (r.status === 'DRAFT' ? '/publish' : '/renew'),
      );
      this.feedback.show(r.status === 'DRAFT' ? 'Annonce publiée.' : 'Annonce renouvelée.');
      void this.router.navigate(['/annonces', r.id], { queryParams: { published: 1 } });
    } catch (e) {
      this.feedback.error(e);
    } finally {
      this.busy.set(false);
    }
  }
  async upload(event: Event) {
    if (!this.selectEstimatePhotos(event)) return;
    const row = this.saved();
    if (!row) return;
    this.busy.set(true);
    try {
      await this.uploadPendingPhotos(row.id);
    } catch (error) {
      this.feedback.error(error);
    } finally {
      this.busy.set(false);
    }
  }
  async removePhoto(photo: string) {
    const r = this.saved();
    if (!r || !window.confirm('Supprimer cette photo ?')) return;
    this.busy.set(true);
    try {
      await this.api.request('DELETE', '/listings/' + r.id + '/photos/' + photo);
      this.saved.set(await this.api.request<Listing>('GET', '/listings/' + r.id));
    } catch (e) {
      this.feedback.error(e);
    } finally {
      this.busy.set(false);
    }
  }
}

export interface RentEstimate {
  success: boolean;
  estimation: {
    disponible: boolean;
    loyer_min_tnd: number;
    loyer_max_tnd: number;
    loyer_conseille_tnd: number;
    fiabilite: string;
    explication: string;
  };
  analyse_visuelle?: {
    etat_apparent?: string;
    luminosite?: string;
    equipements_visibles?: string[];
  };
}

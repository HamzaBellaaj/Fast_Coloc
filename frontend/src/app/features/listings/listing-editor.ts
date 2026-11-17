import { DatePipe } from '@angular/common';
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
  imports: [DatePipe, Calendar, FormsModule, RouterLink, LocationPicker, AdminNav],
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
    this.flowError = '';
    this.step = Math.max(0, this.step - 1);
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
      }
      this.regionId =
        this.areas().find((a) => a.id === this.form.localityId)?.governorate_id ?? null;
      this.busy.set(false);
    }
  }
  ngOnDestroy() {
    this.destroyed = true;
    if (
      !this.adminMode &&
      this.allowed() &&
      this.baseline &&
      JSON.stringify(this.form) !== this.baseline
    )
      this.drafts.set(this.draftKey, {
        form: { ...this.form },
        step: this.step,
        version: this.saved()?.version ?? null,
        caption: this.caption,
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
    if (this.busy() || (!this.adminMode && !this.validateAll())) return;
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
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const r = this.saved();
    if (!file || !r) return;
    if (file.size > 5242880) {
      this.feedback.show('Chaque photo doit peser au maximum 5 MiB.', true);
      input.value = '';
      return;
    }
    this.busy.set(true);
    try {
      const body = new FormData();
      body.append('file', file);
      body.append('caption', this.caption);
      this.saved.set(
        await this.api.request<Listing>('POST', '/listings/' + r.id + '/photos', body),
      );
      this.caption = '';
    } catch (e) {
      this.feedback.error(e);
    } finally {
      this.busy.set(false);
      input.value = '';
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

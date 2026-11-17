import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Feedback } from '../../core/feedback';
import { Area, Listing } from './models';
import { LocationPicker } from './location-picker';
import { AdminNav } from '../admin/admin-nav';
@Component({
  imports: [FormsModule, RouterLink, LocationPicker, AdminNav],
  templateUrl: './listing-editor.html',
})
export class ListingEditor implements OnInit {
  api = inject(Api);
  auth = inject(Auth);
  feedback = inject(Feedback);
  route = inject(ActivatedRoute);
  router = inject(Router);
  busy = signal(true);
  allowed = signal(true);
  saved = signal<Listing | null>(null);
  areas = signal<Area[]>([]);
  step = 1;
  caption = '';
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
      this.busy.set(false);
    }
  }
  private accept(r: Listing) {
    this.saved.set(r);
    this.form = {
      kind: r.kind,
      title: r.title,
      description: r.description,
      localityId: Number(r.locality_id),
      street: r.street,
      latitude: Number(r.latitude),
      longitude: Number(r.longitude),
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
  next(f: NgForm) {
    if (f.invalid) {
      f.control.markAllAsTouched();
      this.feedback.show('Vérifie les champs de cette étape.', true);
      return;
    }
    this.step = Math.min(3, this.step + 1);
  }
  async save(f: NgForm) {
    if (f.invalid || (this.adminMode && !this.reason.trim())) {
      f.control.markAllAsTouched();
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
      this.feedback.show(
        this.adminMode
          ? 'Correction administrative enregistrée.'
          : 'Annonce enregistrée. Ajoute tes photos puis publie-la.',
      );
      if (this.adminMode) {
        void this.router.navigate(['/admin/annonces', r.id]);
        return;
      }
      if (!id) void this.router.navigate(['/annonces', r.id, 'modifier'], { replaceUrl: true });
    } catch (e) {
      this.feedback.error(e);
    } finally {
      this.busy.set(false);
    }
  }
  async publish() {
    const r = this.saved();
    if (!r) return;
    this.busy.set(true);
    try {
      await this.api.request(
        'POST',
        '/listings/' + r.id + (r.status === 'DRAFT' ? '/publish' : '/renew'),
      );
      this.feedback.show(r.status === 'DRAFT' ? 'Annonce publiée.' : 'Annonce renouvelée.');
      void this.router.navigate(['/annonces', r.id]);
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

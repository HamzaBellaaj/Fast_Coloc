import { BlockedUsers } from './blocked-users';
import { NotificationSettings } from './notification-settings';
import { Calendar } from '../../shared/calendar';
import { Component, inject, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Feedback } from '../../core/feedback';
import { Details, Place } from '../../core/models';
import { Photos } from '../../shared/photos';
import { Field } from '../../shared/field';
import { RegistrationDraft } from '../onboarding/draft';
@Component({
  imports: [BlockedUsers, NotificationSettings, Calendar, RouterLink, ReactiveFormsModule, DatePipe, Photos, Field],
  templateUrl: './profile.html',
})
export class ProfilePage implements OnInit {
  api = inject(Api);
  auth = inject(Auth);
  feedback = inject(Feedback);
  draft = inject(RegistrationDraft);
  details: Details | null = null;
  loading = true;
  busy = false;
  places: Place[] = [];
  countryName = '';
  governorateName = '';
  localityName = '';
  form = inject(FormBuilder).group({
    minBudget: [null as number | null, [Validators.min(0), Validators.max(999999999.999)]],
    maxBudget: [null as number | null, [Validators.min(0), Validators.max(999999999.999)]],
    moveInDate: [''],
    localityIds: [[] as number[]],
  });
  async ngOnInit() {
    await this.load();
  }
  async load() {
    this.loading = true;
    try {
      this.details = await this.api.details();
      this.auth.profile.set(this.details.profile);
      this.draft.hydrate(this.details);
      const p = this.details.preferences;
      this.form.reset({
        minBudget: p.min_budget ?? null,
        maxBudget: p.max_budget ?? null,
        moveInDate: p.move_in_date ?? '',
        localityIds: this.details.preferredLocalities,
      });
      const gov = this.details.address.governorate_id,
        country = this.details.address.country_code;
      const [countries, governorates, places] = await Promise.all([
        this.api.countries(),
        country ? this.api.governorates(country) : Promise.resolve([]),
        gov ? this.api.localities(gov) : Promise.resolve([]),
      ]);
      this.places = places;
      this.countryName = countries.find((c) => c.code === country)?.name ?? country ?? '';
      this.governorateName = governorates.find((g) => g.id === gov)?.name ?? '';
      this.localityName =
        places.find((p) => p.id === this.details?.address.locality_id)?.name ?? '';
    } catch (error) {
      this.feedback.error(error);
    } finally {
      this.loading = false;
    }
  }
  async savePreferences() {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy) return;
    const v = this.form.getRawValue();
    if (v.minBudget != null && v.maxBudget != null && v.minBudget > v.maxBudget) {
      this.feedback.show('Le budget minimum doit être inférieur ou égal au maximum.', true);
      return;
    }
    this.busy = true;
    try {
      this.details = await this.api.preferences({
        minBudget: v.minBudget,
        maxBudget: v.maxBudget,
        moveInDate: v.moveInDate || null,
        localityIds: v.localityIds ?? [],
      });
      this.feedback.show('Préférences enregistrées.');
    } catch (error) {
      this.feedback.error(error);
    } finally {
      this.busy = false;
    }
  }
}

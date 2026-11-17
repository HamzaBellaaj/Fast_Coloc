import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { Auth } from '../../core/auth';
import { Api } from '../../core/api';
import { Feedback } from '../../core/feedback';
import { Country, Details, Place } from '../../core/models';
import { AuthLayout } from '../../shared/auth-layout';
import { Field } from '../../shared/field';
import { Photos } from '../../shared/photos';
import { matchingPasswords, passwordPolicy, splitList } from '../../shared/validators';
import { RegistrationDraft } from './draft';
@Component({
  imports: [ReactiveFormsModule, RouterLink, AuthLayout, Field, Photos],
  templateUrl: './registration.html',
})
export class Registration implements OnInit, OnDestroy {
  auth = inject(Auth);
  api = inject(Api);
  feedback = inject(Feedback);
  draft = inject(RegistrationDraft);
  router = inject(Router);
  route = inject(ActivatedRoute);
  step = 1;
  busy = false;
  loading = true;
  failed = false;
  showPassword = false;
  countries: Country[] = [];
  governorates: Place[] = [];
  localities: Place[] = [];
  details: Details | null = null;
  titles = [
    'Tes infos, ton départ ✨',
    'Où es-tu actuellement ? 📍',
    'Parlons un peu de toi 💜',
    'Comment te contacter ? 💬',
    'Montre ton meilleur sourire 📸',
  ];
  labels = [
    'Informations générales',
    'Adresse',
    'Personnalité et habitudes',
    'Coordonnées et disponibilités',
    'Photos et finalisation',
  ];
  account = inject(FormBuilder).nonNullable.group(
    {
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      password: ['', passwordPolicy],
      confirm: ['', Validators.required],
    },
    { validators: matchingPasswords },
  );
  private routeSub: Subscription;
  private geoVersion = 0;
  private localityVersion = 0;
  constructor() {
    this.routeSub = this.route.paramMap.subscribe((p) => {
      this.step = Number(p.get('step'));
      if (![1, 2, 3, 4, 5].includes(this.step)) void this.router.navigateByUrl('/register/1');
    });
  }
  async ngOnInit() {
    await this.load();
  }
  async load() {
    this.loading = true;
    this.failed = false;
    try {
      if (this.auth.session()) {
        this.details = await this.api.details();
        this.auth.profile.set(this.details.profile);
        if (this.draft.owner !== this.details.profile.id) this.draft.hydrate(this.details);
        this.countries = await this.api.countries();
        this.governorates = await this.api.governorates(
          this.draft.address.controls.countryCode.value ?? 'TN',
        );
        const id = this.draft.address.controls.governorateId.value;
        if (id) this.localities = await this.api.localities(id);
      } else if (this.draft.owner) {
        this.draft.clear();
      }
    } catch (error) {
      this.failed = true;
      this.feedback.error(error);
    } finally {
      this.loading = false;
    }
  }
  async countryChanged() {
    const version = ++this.geoVersion;
    ++this.localityVersion;
    this.governorates = [];
    this.localities = [];
    this.draft.address.patchValue({ governorateId: null, localityId: null });
    try {
      const data = await this.api.governorates(
        this.draft.address.controls.countryCode.value ?? 'TN',
      );
      if (version === this.geoVersion) this.governorates = data;
    } catch (error) {
      this.feedback.error(error);
    }
  }
  async governorateChanged() {
    const version = ++this.localityVersion;
    this.localities = [];
    this.draft.address.controls.localityId.setValue(null);
    const id = this.draft.address.controls.governorateId.value;
    if (!id) return;
    try {
      const data = await this.api.localities(id);
      if (version === this.localityVersion) this.localities = data;
    } catch (error) {
      this.feedback.error(error);
    }
  }
  async photosChanged() {
    try {
      this.details = await this.api.details();
      this.auth.profile.set(this.details.profile);
    } catch (error) {
      this.feedback.error(error);
    }
  }
  async google() {
    if (this.busy) return;
    this.busy = true;
    try {
      await this.auth.google();
    } catch (error) {
      this.feedback.error(error);
      this.busy = false;
    }
  }
  async save() {
    if (this.busy || this.loading || this.failed) return;
    const form = [
      this.draft.identity,
      this.draft.address,
      this.draft.personal,
      this.draft.contacts,
    ][this.step - 1];
    form?.markAllAsTouched();
    if (form?.invalid) {
      this.feedback.show('Vérifie les champs signalés avant de continuer.', true);
      return;
    }
    if (!this.auth.session()) {
      this.account.markAllAsTouched();
      if (this.account.invalid) return;
    }
    this.busy = true;
    this.feedback.clear();
    try {
      if (!this.auth.session()) {
        const identity = this.draft.identity.getRawValue();
        this.auth.draftIdentity = {
          email: this.account.getRawValue().email.trim(),
          value: {
            firstName: identity.firstName!.trim(),
            lastName: identity.lastName!.trim(),
            gender: identity.gender,
            age: identity.age!,
          },
        };
        const { email, password } = this.account.getRawValue();
        await this.api.request('POST', '/auth/signup', { email: email.trim(), password });
        this.account.reset();
        await this.router.navigateByUrl('/verify-email');
        return;
      }
      if (this.step === 1) {
        const v = this.draft.identity.getRawValue();
        this.details = await this.api.step(1, {
          firstName: v.firstName!.trim(),
          lastName: v.lastName!.trim(),
          gender: v.gender,
          age: v.age!,
        });
      }
      if (this.step === 2) {
        const v = this.draft.address.getRawValue();
        this.details = await this.api.step(2, {
          countryCode: v.countryCode!,
          governorateId: v.governorateId!,
          localityId: v.localityId!,
          street: v.street!.trim(),
          postalCode: v.postalCode!.trim(),
        });
      }
      if (this.step === 3) {
        const v = this.draft.personal.getRawValue();
        this.details = await this.api.step(3, {
          mainActivities: splitList(v.mainActivities),
          hobbies: splitList(v.hobbies),
          nationalityCode: v.nationalityCode || null,
          spokenLanguages: splitList(v.spokenLanguages),
          religion: v.religion || null,
          smoking: v.smoking === '' ? null : v.smoking === 'true',
          cleanliness: v.cleanliness || null,
          biography: v.biography || null,
        });
      }
      if (this.step === 4) this.details = await this.api.step(4, this.draft.contacts.getRawValue());
      if (this.step === 5) {
        if (!this.details?.photos.length)
          throw new Error('Ajoute au moins une photo pour terminer ton inscription.');
        this.details = await this.api.step(5, { photoIds: this.details.photos.map((p) => p.id) });
        this.details = await this.api.complete();
      }
      if (this.details) this.auth.profile.set(this.details.profile);
      this.feedback.show(
        this.step === 5
          ? 'Ton inscription est complète. Bienvenue chez FastColoc !'
          : 'Étape enregistrée. Tu peux la reprendre à tout moment.',
      );
      await this.router.navigateByUrl(
        this.step === 5 ? '/profile' : '/register/' + (this.step + 1),
      );
    } catch (error) {
      this.feedback.error(error);
    } finally {
      this.account.controls.password.reset();
      this.account.controls.confirm.reset();
      this.busy = false;
    }
  }
  ngOnDestroy() {
    this.routeSub.unsubscribe();
    this.account.reset();
  }
}

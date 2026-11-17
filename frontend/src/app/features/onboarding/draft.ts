import { inject, Injectable } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Details } from '../../core/models';
import { listPolicy, nonBlank } from '../../shared/validators';
export const phoneValidators = [Validators.maxLength(30), Validators.pattern(/^[+0-9() .-]*$/)];
@Injectable({ providedIn: 'root' })
export class RegistrationDraft {
  private fb = inject(FormBuilder);
  owner: string | null = null;
  clear() {
    this.owner = null;
    this.identity.reset();
    this.address.reset({ countryCode: 'TN' });
    this.personal.reset();
    this.contacts.reset();
  }
  identity = this.fb.group({
    firstName: ['', [nonBlank, Validators.maxLength(100)]],
    lastName: ['', [nonBlank, Validators.maxLength(100)]],
    gender: [''],
    age: [
      null as number | null,
      [Validators.required, Validators.min(0), Validators.max(130), Validators.pattern(/^\d+$/)],
    ],
  });
  address = this.fb.group({
    countryCode: ['TN', Validators.required],
    governorateId: [null as number | null, Validators.required],
    localityId: [null as number | null, Validators.required],
    street: ['', [nonBlank, Validators.maxLength(250)]],
    postalCode: ['', [nonBlank, Validators.maxLength(20)]],
  });
  personal = this.fb.nonNullable.group({
    mainActivities: ['', listPolicy],
    hobbies: ['', listPolicy],
    nationalityCode: [''],
    spokenLanguages: ['', listPolicy],
    religion: ['', Validators.maxLength(120)],
    smoking: [''],
    cleanliness: ['', Validators.maxLength(120)],
    biography: ['', Validators.maxLength(2000)],
  });
  contacts = this.fb.nonNullable.group({
    mobilePhone: ['', phoneValidators],
    residentialPhone: ['', phoneValidators],
    callAvailability: ['', Validators.maxLength(250)],
    msnContact: ['', Validators.maxLength(250)],
    skypeContact: ['', Validators.maxLength(250)],
    websiteUrl: ['', [Validators.maxLength(500), Validators.pattern(/^$|^https?:\/\/[^\s]+$/)]],
  });
  hydrate(d: Details) {
    this.owner = d.profile.id;
    this.identity.reset({
      firstName: d.profile.firstName ?? '',
      lastName: d.profile.lastName ?? '',
      gender: d.profile.gender ?? '',
      age: d.profile.age,
    });
    this.address.reset({
      countryCode: d.address.country_code ?? 'TN',
      governorateId: d.address.governorate_id ?? null,
      localityId: d.address.locality_id ?? null,
      street: d.address.street ?? '',
      postalCode: d.address.postal_code ?? '',
    });
    const p = d.personal,
      c = d.contacts;
    this.personal.reset({
      mainActivities: p.main_activities?.join(', ') ?? '',
      hobbies: p.hobbies?.join(', ') ?? '',
      nationalityCode: p.nationality_code ?? '',
      spokenLanguages: p.spoken_languages?.join(', ') ?? '',
      religion: p.religion ?? '',
      smoking: p.smoking == null ? '' : String(p.smoking),
      cleanliness: p.cleanliness ?? '',
      biography: p.biography ?? '',
    });
    this.contacts.reset({
      mobilePhone: c.mobile_phone ?? '',
      residentialPhone: c.residential_phone ?? '',
      callAvailability: c.call_availability ?? '',
      msnContact: c.msn_contact ?? '',
      skypeContact: c.skype_contact ?? '',
      websiteUrl: c.website_url ?? '',
    });
  }
}

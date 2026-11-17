import { Component, ElementRef, inject, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Feedback } from '../../core/feedback';
import { AdminUser, Details } from '../../core/models';
import { phoneValidators } from '../onboarding/draft';
import { Field } from '../../shared/field';
import { nonBlank } from '../../shared/validators';
import { AdminNav } from './admin-nav';
@Component({
  imports: [FormsModule, ReactiveFormsModule, DatePipe, Field, AdminNav],
  templateUrl: './admin.html',
  styleUrl: './admin.css',
})
export class AdminPage implements OnInit, OnDestroy {
  api = inject(Api);
  auth = inject(Auth);
  feedback = inject(Feedback);
  users: AdminUser[] = [];
  offset = 0;
  total = 0;
  search = '';
  status = '';
  loading = false;
  busy = false;
  failed = false;
  contactsLoading = false;
  contactsFailed = false;
  contactsReady = false;
  private contactRequest = 0;
  target: AdminUser | null = null;
  action: 'status' | 'contacts' | 'delete' = 'status';
  @ViewChild('confirmation') dialog?: ElementRef<HTMLDialogElement>;
  form = inject(FormBuilder).nonNullable.group({
    reason: ['', [nonBlank, Validators.maxLength(1000)]],
    mobilePhone: ['', phoneValidators],
    residentialPhone: ['', phoneValidators],
    callAvailability: ['', Validators.maxLength(250)],
    msnContact: ['', Validators.maxLength(250)],
    skypeContact: ['', Validators.maxLength(250)],
    websiteUrl: ['', [Validators.maxLength(500), Validators.pattern(/^$|^https?:\/\/[^\s]+$/)]],
  });
  async ngOnInit() {
    await this.load();
  }
  async load(reset = false) {
    if (this.loading) return;
    if (reset) this.offset = 0;
    this.loading = true;
    this.failed = false;
    try {
      this.users = await this.api.users(this.offset, this.search.trim(), this.status);
      this.total = Number(this.users[0]?.total_count ?? 0);
    } catch (error) {
      this.failed = true;
      this.feedback.error(error);
    } finally {
      this.loading = false;
    }
  }
  async page(delta: number) {
    this.offset = Math.max(0, this.offset + delta);
    await this.load();
  }
  open(user: AdminUser, action: typeof this.action) {
    if (this.busy) return;
    this.target = user;
    this.action = action;
    this.form.reset();
    this.contactsLoading = false;
    this.contactsFailed = false;
    this.contactsReady = false;
    ++this.contactRequest;
    this.dialog?.nativeElement.showModal();
    this.dialog?.nativeElement.querySelector('.admin-dialog-body')?.scrollTo(0, 0);
    if (action === 'contacts') void this.loadContacts();
  }
  async loadContacts() {
    const id = this.target?.id;
    if (!id || this.action !== 'contacts') return;
    const request = ++this.contactRequest;
    this.contactsLoading = true;
    this.contactsFailed = false;
    this.contactsReady = false;
    try {
      const c = await this.api.request<Details['contacts']>(
        'GET',
        '/admin/users/' + id + '/contacts',
      );
      if (request !== this.contactRequest) return;
      this.form.patchValue({
        mobilePhone: c.mobile_phone ?? '',
        residentialPhone: c.residential_phone ?? '',
        callAvailability: c.call_availability ?? '',
        msnContact: c.msn_contact ?? '',
        skypeContact: c.skype_contact ?? '',
        websiteUrl: c.website_url ?? '',
      });
      this.contactsReady = true;
    } catch {
      if (request === this.contactRequest) this.contactsFailed = true;
    } finally {
      if (request === this.contactRequest) this.contactsLoading = false;
    }
  }
  ngOnDestroy() {
    ++this.contactRequest;
  }
  close() {
    if (this.busy) return;
    ++this.contactRequest;
    this.dialog?.nativeElement.close();
    this.target = null;
  }
  async confirm() {
    this.form.markAllAsTouched();
    if (
      this.form.invalid ||
      this.busy ||
      !this.target ||
      (this.action === 'contacts' && !this.contactsReady)
    )
      return;
    this.busy = true;
    const v = this.form.getRawValue(),
      id = this.target.id;
    try {
      if (this.action === 'status')
        await this.api.request('PATCH', '/admin/users/' + id + '/status', {
          status: this.target.status === 'ACTIVE' ? 'BLOCKED' : 'ACTIVE',
          reason: v.reason.trim(),
        });
      if (this.action === 'delete')
        await this.api.request('DELETE', '/admin/users/' + id, { reason: v.reason.trim() });
      if (this.action === 'contacts') {
        const { reason, ...contacts } = v;
        await this.api.request('PATCH', '/admin/users/' + id + '/contacts', {
          contacts,
          reason: reason.trim(),
        });
      }
      this.dialog?.nativeElement.close();
      this.target = null;
      this.feedback.show('Action administrative enregistrée.');
      await this.load();
    } catch (error) {
      this.feedback.error(error);
    } finally {
      this.busy = false;
    }
  }
}

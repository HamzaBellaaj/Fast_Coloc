import { DesktopStatus } from '../../shared/desktop-status';
import { Component, ElementRef, inject, OnInit, signal, ViewChild } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Api } from '../../core/api';
import { Feedback } from '../../core/feedback';
import { Listing } from '../listings/models';
import { AdminNav } from './admin-nav';

interface AdminListing extends Listing {
  author_name: string;
  display_status: string;
  created_at: string;
  updated_at: string;
}
interface History {
  id: string;
  actor_name: string;
  actor_id: string;
  action: string;
  reason: string;
  created_at: string;
  before_state: Record<string, unknown>;
  after_state: Record<string, unknown> | null;
}
@Component({
  imports: [DesktopStatus, AdminNav, RouterLink, FormsModule, DatePipe, DecimalPipe],
  templateUrl: './admin-listings.html',
  styleUrl: './admin-listings.css',
})
export class AdminListings implements OnInit {
  private api = inject(Api);
  private feedback = inject(Feedback);
  private route = inject(ActivatedRoute);
  readonly id = this.route.snapshot.paramMap.get('id');
  items = signal<AdminListing[]>([]);
  row = signal<AdminListing | null>(null);
  history = signal<History[]>([]);
  loading = signal(false);
  failed = signal(false);
  busy = signal(false);
  deleted = signal(false);
  total = signal(0);
  filters = { q: '', kind: '', status: '', from: '', to: '', sort: 'NEWEST' };
  page = 0;
  readonly size = 20;
  action: 'hide' | 'restore' | 'delete' = 'hide';
  reason = '';
  @ViewChild('confirmation') dialog?: ElementRef<HTMLDialogElement>;
  readonly labels: Record<string, string> = {
    HIDDEN: 'Masquée',
    DRAFT: 'Brouillon',
    PUBLISHED: 'Publiée',
    EXPIRED: 'Expirée',
    HIDE: 'Masquage',
    RESTORE: 'Réactivation',
    EDIT: 'Modification',
    DELETE: 'Suppression',
  };
  async ngOnInit() {
    await this.load();
  }
  async load(reset = false) {
    if (this.loading()) return;
    if (reset) this.page = 0;
    this.loading.set(true);
    this.failed.set(false);
    try {
      if (this.id) {
        const history = await this.api.request<History[]>(
          'GET',
          '/admin/listings/' + this.id + '/history',
        );
        this.history.set(history);
        try {
          this.row.set(await this.api.request<AdminListing>('GET', '/admin/listings/' + this.id));
        } catch (e) {
          if (
            e instanceof HttpErrorResponse &&
            e.status === 404 &&
            history.some((h) => h.action === 'DELETE')
          ) {
            this.deleted.set(true);
            this.row.set(null);
          } else throw e;
        }
      } else {
        const query = new URLSearchParams({ page: String(this.page), size: String(this.size) });
        for (const [k, v] of Object.entries(this.filters)) if (v.trim()) query.set(k, v.trim());
        const result = await this.api.request<{ items: AdminListing[]; total: number }>(
          'GET',
          '/admin/listings?' + query,
        );
        this.items.set(result.items);
        this.total.set(result.total);
      }
    } catch (e) {
      this.failed.set(true);
      this.feedback.error(e);
    } finally {
      this.loading.set(false);
    }
  }
  reset() {
    this.filters = { q: '', kind: '', status: '', from: '', to: '', sort: 'NEWEST' };
    void this.load(true);
  }
  paginate(delta: number) {
    this.page += delta;
    void this.load();
  }
  calendar(input: HTMLInputElement) {
    if (input.showPicker) {
      try {
        input.showPicker();
      } catch {
        input.focus();
      }
    } else input.focus();
  }
  open(action: typeof this.action) {
    this.action = action;
    this.reason = '';
    this.dialog?.nativeElement.showModal();
  }
  close() {
    if (!this.busy()) this.dialog?.nativeElement.close();
  }
  async confirm(form: NgForm) {
    if (form.invalid || !this.reason.trim() || this.busy()) return;
    const row = this.row();
    if (!row) return;
    this.busy.set(true);
    try {
      await this.api.request(
        this.action === 'delete' ? 'DELETE' : 'POST',
        '/admin/listings/' + row.id + (this.action === 'delete' ? '' : '/' + this.action),
        { reason: this.reason.trim(), version: row.version },
      );
      this.dialog?.nativeElement.close();
      this.feedback.show('Action administrative enregistrée.');
      await this.load();
    } catch (e) {
      this.feedback.error(e);
    } finally {
      this.busy.set(false);
    }
  }
  changes(event: History) {
    const names: Record<string, string> = {
      title: 'Titre',
      description: 'Description',
      kind: 'Type',
      rent: 'Loyer (TND)',
      street: 'Adresse',
      locality_id: 'Localité',
      latitude: 'Latitude',
      longitude: 'Longitude',
      capacity: 'Capacité',
      property_type: 'Logement',
      roommate_type: 'Colocataire',
      equipment: 'Équipements',
      available_from: 'Disponible dès le',
      available_until: 'Disponible jusqu’au',
      publication_days: 'Durée (jours)',
      is_hidden: 'Masquage',
      status: 'Publication',
      expires_at: 'Expiration',
    };
    return Object.entries(names)
      .filter(
        ([k]) => JSON.stringify(event.before_state[k]) !== JSON.stringify(event.after_state?.[k]),
      )
      .map(([k, label]) => ({
        label,
        before: this.value(event.before_state[k]),
        after: this.value(event.after_state?.[k]),
      }));
  }
  private value(v: unknown): string {
    return v == null
      ? '—'
      : typeof v === 'boolean'
        ? v
          ? 'Oui'
          : 'Non'
        : Array.isArray(v)
          ? v.join(', ')
          : String(v);
  }
}

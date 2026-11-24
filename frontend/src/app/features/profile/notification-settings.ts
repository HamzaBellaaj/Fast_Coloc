import { DesktopStatus } from '../../shared/desktop-status';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../core/api';
import { errorMessage } from '../../core/feedback';
export interface NotificationPreferences {
  newMessageEmail: boolean;
  deliveryAvailable: boolean;
  deliveryMode: 'local' | 'external' | 'disabled';
}
@Component({
  selector: 'fc-notification-settings',
  imports: [DesktopStatus, FormsModule],
  templateUrl: './notification-settings.html',
})
export class NotificationSettings implements OnInit {
  private api = inject(Api);
  loading = signal(true);
  saving = signal(false);
  error = signal('');
  saved = signal(false);
  preferences = signal<NotificationPreferences | null>(null);
  enabled = true;
  ngOnInit() {
    void this.load();
  }
  async load() {
    this.loading.set(true);
    this.error.set('');
    try {
      const p = await this.api.request<NotificationPreferences>('GET', '/notification-preferences');
      this.preferences.set(p);
      this.enabled = p.newMessageEmail;
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }
  async save() {
    if (this.loading() || this.saving() || !this.preferences()) return;
    this.saving.set(true);
    this.saved.set(false);
    this.error.set('');
    try {
      const p = await this.api.request<NotificationPreferences>(
        'PATCH',
        '/notification-preferences',
        { newMessageEmail: this.enabled },
      );
      this.preferences.set(p);
      this.enabled = p.newMessageEmail;
      this.saved.set(true);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.saving.set(false);
    }
  }
}

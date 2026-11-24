import { DesktopStatus } from './desktop-status';
import {
  Component,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  EventEmitter,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api';
import { Photo } from '../core/models';
import { Feedback } from '../core/feedback';
@Component({
  selector: 'app-photos',
  imports: [DesktopStatus, FormsModule],
  template: `
    <div class="upload">
      <div class="upload-icon" aria-hidden="true"></div>
      <h3>Ton sourire fait la différence</h3>
      <p class="help">JPEG, PNG ou WebP · 5 photos maximum · 5 MiB par photo</p>
      <button
        type="button"
        class="btn ghost file-label"
        (click)="photoFile.click()"
        [disabled]="busy || photos.length >= 5"
        [fcStatus]="busy ? 'loading' : null"
      >
        Choisir une photo</button
      ><input
        id="photo-file"
        #photoFile
        tabindex="-1"
        aria-label="Photo à envoyer"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        (change)="choose($event)"
        [disabled]="busy || photos.length >= 5"
      />
    </div>
    @if (preview) {
      <div class="section-card photo-preview">
        <img [src]="preview" alt="Aperçu de la photo choisie" /><label for="new-caption"
          >Description (facultative)</label
        ><input class="text-input" id="new-caption" [(ngModel)]="caption" maxlength="500" />
        <div class="row wrap">
          <button
            class="btn"
            (click)="upload()"
            [disabled]="busy"
            [fcStatus]="busy ? 'loading' : null"
          >
            {{ busy ? 'Envoi…' : 'Envoyer la photo' }}</button><button
            class="btn ghost"
            (click)="discard()"
            [disabled]="busy"
            [fcStatus]="busy ? 'loading' : null"
          >
            Annuler
          </button>
        </div>
      </div>
    }
    <div class="photo-grid">
      @for (photo of photos; track photo.id) {
        <article class="photo-item">
          @if (urls[photo.id]) {
            <img [src]="urls[photo.id]" [alt]="photo.caption || 'Photo de profil privée'" />
          } @else {
            <div class="photo-loading">Chargement de la photo…</div>
          }
          <label [for]="'caption-' + photo.id">Description</label
          ><input
            class="text-input"
            [id]="'caption-' + photo.id"
            [ngModel]="captions[photo.id]"
            (ngModelChange)="captions[photo.id] = $event"
            maxlength="500"
          />
          <div class="row wrap">
            <button
              class="btn small ghost"
              (click)="saveCaption(photo)"
              [disabled]="busy"
              [fcStatus]="busy ? 'loading' : null"
            >
              Enregistrer</button
            ><button
              class="btn small ghost"
              (click)="remove(photo)"
              [disabled]="busy"
              [fcStatus]="busy ? 'loading' : null"
            >
              Supprimer
            </button>
          </div>
        </article>
      }
    </div>
    @if (!photos.length) {
      <p class="help">Ajoute au moins une photo pour valider la cinquième étape.</p>
    }
  `,
})
export class Photos implements OnChanges, OnDestroy {
  @Input() photos: Photo[] = [];
  @Output() changed = new EventEmitter<void>();
  api = inject(Api);
  feedback = inject(Feedback);
  urls: Record<string, string> = {};
  captions: Record<string, string> = {};
  busy = false;
  preview = '';
  file: File | null = null;
  caption = '';
  private destroyed = false;
  private timer = setInterval(() => {
    void this.refresh();
  }, 45000);
  ngOnChanges() {
    this.captions = Object.fromEntries(this.photos.map((p) => [p.id, p.caption ?? '']));
    void this.refresh();
  }
  async refresh() {
    try {
      const results = await Promise.all(
        this.photos.map(async (p) => [p.id, (await this.api.signed(p.id)).url] as const),
      );
      if (!this.destroyed) this.urls = Object.fromEntries(results);
    } catch (error) {
      if (!this.destroyed) this.feedback.error(error);
    }
  }
  choose(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (
      !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
      file.size > 5242880 ||
      !file.size
    ) {
      this.feedback.show('Choisis une image JPEG, PNG ou WebP non vide de 5 MiB maximum.', true);
      return;
    }
    this.discard();
    this.file = file;
    this.preview = URL.createObjectURL(file);
  }
  discard() {
    if (this.preview) URL.revokeObjectURL(this.preview);
    this.preview = '';
    this.file = null;
    this.caption = '';
  }
  async upload() {
    if (!this.file || this.busy) return;
    this.busy = true;
    try {
      const data = new FormData();
      data.append('file', this.file);
      data.append('caption', this.caption);
      await this.api.request('POST', '/profile-photos', data);
      this.discard();
      this.feedback.show('Photo ajoutée dans ton espace privé.');
      this.changed.emit();
    } catch (error) {
      this.feedback.error(error);
    } finally {
      this.busy = false;
    }
  }
  async saveCaption(photo: Photo) {
    if (this.busy) return;
    this.busy = true;
    try {
      await this.api.request('PATCH', '/profile-photos/' + photo.id, {
        caption: this.captions[photo.id],
      });
      this.feedback.show('Description enregistrée.');
      this.changed.emit();
    } catch (error) {
      this.feedback.error(error);
    } finally {
      this.busy = false;
    }
  }
  async remove(photo: Photo) {
    if (
      this.busy ||
      !window.confirm('Supprimer cette photo privée ? Il faudra revalider l’étape 5.')
    )
      return;
    this.busy = true;
    try {
      await this.api.request('DELETE', '/profile-photos/' + photo.id);
      this.changed.emit();
      this.feedback.show('Photo supprimée. Revalide l’étape 5 pour compléter ton profil.');
    } catch (error) {
      this.feedback.error(error);
    } finally {
      this.busy = false;
    }
  }
  ngOnDestroy() {
    this.destroyed = true;
    clearInterval(this.timer);
    this.discard();
  }
}

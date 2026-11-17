import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Feedback } from '../../core/feedback';
import { Field } from '../../shared/field';
@Component({
  imports: [ReactiveFormsModule, RouterLink, Field],
  template: ` <section class="success-box">
    <div class="illustration" aria-hidden="true">📨</div>
    <span class="tag">CONFIRMATION</span>
    <h1 class="form-title">Un petit clic, et c’est parti !</h1>
    <p>
      Si l’inscription est admissible, tu recevras un e-mail. Confirme ton adresse pour activer ton
      compte, puis reprends les cinq étapes après connexion.
    </p>
    <p class="help">
      Les informations saisies avant confirmation restent dans la mémoire de cette page. Les étapes
      enregistrées après connexion sont conservées sur le serveur.
    </p>
    <a class="btn" routerLink="/login">J’ai confirmé mon e-mail →</a>
    <form class="form" [formGroup]="form" (ngSubmit)="resend()" novalidate>
      <app-field
        fieldId="resend-email"
        label="Adresse e-mail pour renvoyer le lien"
        [control]="form.controls.email"
        ><input
          id="resend-email"
          type="email"
          autocomplete="email"
          formControlName="email" /></app-field
      ><button class="btn ghost" [disabled]="busy || cooldown > 0">
        {{
          busy
            ? 'Envoi…'
            : cooldown > 0
              ? 'Lien demandé · réessaie dans une minute'
              : 'Renvoyer le lien'
        }}
      </button>
    </form>
  </section>`,
})
export class Verify {
  api = inject(Api);
  feedback = inject(Feedback);
  busy = false;
  cooldown = 0;
  form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });
  async resend() {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy || this.cooldown) return;
    this.busy = true;
    try {
      await this.api.request('POST', '/auth/resend-confirmation', {
        email: this.form.getRawValue().email.trim(),
      });
      this.feedback.show('Si cette demande est admissible, un e-mail sera envoyé.');
      this.cooldown = 60;
      setTimeout(() => (this.cooldown = 0), 60000);
    } catch (error) {
      this.feedback.error(error);
    } finally {
      this.busy = false;
    }
  }
}

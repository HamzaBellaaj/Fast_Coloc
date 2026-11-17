import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Auth } from '../../core/auth';
import { Api } from '../../core/api';
import { Feedback } from '../../core/feedback';
import { AuthLayout } from '../../shared/auth-layout';
import { Field } from '../../shared/field';
import { matchingPasswords, passwordPolicy } from '../../shared/validators';
@Component({
  imports: [ReactiveFormsModule, RouterLink, AuthLayout, Field],
  templateUrl: './auth-page.html',
})
export class AuthPage {
  auth = inject(Auth);
  api = inject(Api);
  feedback = inject(Feedback);
  mode: 'login' | 'forgot' | 'reset' = inject(ActivatedRoute).snapshot.data['mode'];
  private fb = inject(FormBuilder);
  busy = false;
  sent = false;
  showPassword = false;
  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: [''],
    confirm: [''],
  });
  constructor() {
    if (this.mode === 'reset') {
      this.form.controls.email.disable();
      this.form.controls.password.setValidators(passwordPolicy);
      this.form.setValidators(matchingPasswords);
    } else this.form.controls.confirm.disable();
    if (this.mode === 'login')
      this.form.controls.password.setValidators([Validators.required, Validators.maxLength(256)]);
    if (this.mode === 'forgot') this.form.controls.password.disable();
    Object.values(this.form.controls).forEach((control) => control.updateValueAndValidity());
  }
  async submit() {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy) return;
    this.busy = true;
    this.feedback.clear();
    const { email, password } = this.form.getRawValue();
    try {
      if (this.mode === 'login') await this.auth.login(email.trim(), password);
      // E-mail links may open in another tab/device. Supabase verifies the link
      // before returning a session; Google alone requires the browser-bound PKCE flow.
      if (this.mode === 'forgot') {
        await this.api.request('POST', '/auth/forgot-password', { email: email.trim() });
        this.sent = true;
      }
      if (this.mode === 'reset') {
        if (!this.auth.recovery())
          throw new Error(
            'Ouvre le lien de récupération reçu par e-mail pour choisir un nouveau mot de passe.',
          );
        await this.api.request('PUT', '/auth/password', { password });
        this.feedback.show('Ton mot de passe a été enregistré. Reconnecte-toi.');
        await this.auth.logout();
      }
    } catch (error) {
      this.feedback.error(error);
    } finally {
      this.form.controls.password.reset();
      this.form.controls.confirm.reset();
      this.busy = false;
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
}

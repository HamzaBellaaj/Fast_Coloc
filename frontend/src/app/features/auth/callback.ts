import { DesktopStatus } from '../../shared/desktop-status';
import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Auth } from '../../core/auth';
import { errorMessage } from '../../core/feedback';
@Component({
  imports: [DesktopStatus, RouterLink],
  template: `<section class="success-box" [attr.aria-busy]="!error">
    <div class="illustration" aria-hidden="true">
      <img src="/assets/fastcoloc-symbol.svg" width="100" height="100" alt="" />
    </div>
    <h1 class="form-title">{{ error ? 'Le lien n’a pas abouti' : 'Vérification en cours…' }}</h1>
    <p role="status" [fcStatus]="error ? 'error' : 'loading'">
      {{ error || 'Nous vérifions ton lien sécurisé. Un instant.' }}
    </p>
    @if (error) {
      <div class="form">
        <a class="btn" routerLink="/login">Se connecter</a
        ><a class="link" routerLink="/forgot-password">Demander un lien de récupération</a
        ><a class="link" routerLink="/verify-email">Renvoyer une confirmation</a>
      </div>
    }
  </section>`,
})
export class Callback implements OnInit {
  auth = inject(Auth);
  error = '';
  async ngOnInit() {
    try {
      await this.auth.callback();
    } catch (error) {
      this.auth.clear();
      this.error = errorMessage(error);
    }
  }
}

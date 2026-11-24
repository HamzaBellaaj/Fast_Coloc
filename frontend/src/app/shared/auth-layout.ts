import { Icon } from './icon';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({
  selector: 'app-auth-layout',
  imports: [Icon, RouterLink],
  template: `<div class="auth-layout">
    <header class="auth-card-heading">
      <a routerLink="/" aria-label="Retour à l’accueil"><fc-icon name="arrow-left" /></a
      ><span>{{ registration() ? 'Rejoindre FastColoc' : 'Bienvenue chez FastColoc' }}</span>
    </header>
    <section class="auth-card-content"><ng-content /></section>
    <aside class="auth-assurance">
      <img src="/assets/fastcoloc-symbol.svg" alt="" width="28" height="28" />
      <p>
        La coloc, c’est mieux ensemble.<br /><span>Un profil privé. De nouvelles rencontres.</span>
      </p>
    </aside>
  </div>`,
})
export class AuthLayout {
  registration = input(false);
}

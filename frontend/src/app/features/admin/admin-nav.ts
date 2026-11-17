import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
@Component({
  selector: 'fc-admin-nav',
  imports: [RouterLink, RouterLinkActive],
  styles: [':host{display:contents}.sidebar{align-self:start}'],
  template: `<aside class="sidebar">
    <div class="brand">
      <img src="/assets/fastcoloc-symbol.svg" width="32" height="32" alt="" /> fast<b>coloc</b>
    </div>
    <p class="help">ESPACE ADMINISTRATION</p>
    <a
      routerLink="/admin"
      routerLinkActive="current"
      [routerLinkActiveOptions]="{ exact: true }"
      ariaCurrentWhenActive="page"
      >Utilisateurs</a
    >
    <a routerLink="/admin/annonces" routerLinkActive="current" ariaCurrentWhenActive="page"
      >Annonces et modération</a
    >
    <p class="disclaimer">
      Zone réservée aux administrateurs. Chaque modification est contrôlée et journalisée par le
      serveur.
    </p>
  </aside>`,
})
export class AdminNav {}

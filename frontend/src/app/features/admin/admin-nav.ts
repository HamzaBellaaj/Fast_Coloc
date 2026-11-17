import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
@Component({
  selector: 'fc-admin-nav',
  imports: [RouterLink, RouterLinkActive],
  styles: [':host{display:contents}.sidebar{align-self:start}'],
  template: `<aside class="sidebar">
    <div class="brand">fast<b>coloc</b></div>
    <p class="help">Administration Web</p>
    <a
      routerLink="/admin"
      routerLinkActive="current"
      [routerLinkActiveOptions]="{ exact: true }"
      ariaCurrentWhenActive="page"
      >♙ Gestion des utilisateurs</a
    >
    <a routerLink="/admin/annonces" routerLinkActive="current" ariaCurrentWhenActive="page"
      >⌂ Gestion des annonces</a
    >
    <p class="disclaimer">
      Zone réservée aux administrateurs. Chaque modification est contrôlée et journalisée par le
      serveur.
    </p>
  </aside>`,
})
export class AdminNav {}

import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { Auth } from './core/auth';
import { Feedback } from './core/feedback';
@Component({ selector: 'app-root', imports: [RouterLink, RouterOutlet], templateUrl: './app.html' })
export class App {
  auth = inject(Auth);
  feedback = inject(Feedback);
  menuOpen = false;
  constructor() {
    inject(Router).events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        this.menuOpen = false;
        document.getElementById('main')?.focus({ preventScroll: true });
      }
    });
  }
}

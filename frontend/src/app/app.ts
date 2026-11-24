import { LiquidSurface } from './shared/liquid-pop';
import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { Auth } from './core/auth';
import { Feedback } from './core/feedback';
@Component({
  selector: 'app-root',
  imports: [LiquidSurface, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.html',
})
export class App {
  auth = inject(Auth);
  feedback = inject(Feedback);
  menuOpen = false;
  focusMain() {
    document.getElementById('main')?.focus();
  }
  constructor() {
    inject(Router).events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        this.menuOpen = false;
        document.querySelector('.account-menu')?.removeAttribute('open');
        document.getElementById('main')?.focus({ preventScroll: true });
      }
    });
  }
}

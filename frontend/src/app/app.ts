import { Icon } from './shared/icon';
import { DesktopTheme } from './core/desktop-theme';
import { DesktopStatus } from './shared/desktop-status';
import { LiquidSurface } from './shared/liquid-pop';
import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { Auth } from './core/auth';
import { Feedback } from './core/feedback';
@Component({
  selector: 'app-root',
  imports: [Icon, DesktopStatus, LiquidSurface, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.html',
})
export class App {
  readonly theme = inject(DesktopTheme);
  auth = inject(Auth);
  feedback = inject(Feedback);
  menuOpen = false;
  toggleMenu() {
    this.menuOpen = !this.menuOpen;
    if (this.menuOpen)
      requestAnimationFrame(() =>
        document.querySelector<HTMLElement>('.mobile-navigation a')?.focus(),
      );
  }
  closeMenu() {
    if (!this.menuOpen) return;
    this.menuOpen = false;
    document.querySelector<HTMLElement>('.mobile-menu-toggle')?.focus();
  }
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

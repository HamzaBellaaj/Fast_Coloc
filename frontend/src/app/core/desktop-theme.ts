import { DestroyRef, Injectable, inject, signal } from '@angular/core';
export type DesktopThemeChoice = 'system' | 'light' | 'dark';
const STORAGE_KEY = 'fastcoloc.desktop-theme.v1';
@Injectable({ providedIn: 'root' })
export class DesktopTheme {
  private readonly breakpoint = window.matchMedia?.('(min-width: 851px)');
  private readonly system = window.matchMedia?.('(prefers-color-scheme: dark)');
  readonly desktop = signal(this.breakpoint?.matches ?? window.innerWidth > 850);
  readonly choice = signal<DesktopThemeChoice>(this.read());
  readonly effective = signal<'light' | 'dark'>('light');
  constructor() {
    const update = () => this.apply();
    const sync = (event: StorageEvent) => {
      if (event.key === STORAGE_KEY || event.key === null) {
        this.choice.set(this.read());
        this.apply();
      }
    };
    this.breakpoint?.addEventListener('change', update);
    this.system?.addEventListener('change', update);
    window.addEventListener('storage', sync);
    inject(DestroyRef).onDestroy(() => {
      this.breakpoint?.removeEventListener('change', update);
      this.system?.removeEventListener('change', update);
      window.removeEventListener('storage', sync);
    });
    this.apply();
  }
  choose(value: string) {
    if (!['system', 'light', 'dark'].includes(value)) return;
    this.choice.set(value as DesktopThemeChoice);
    try {
      localStorage.setItem(STORAGE_KEY, value);
    } catch {
      /* The current session can still use the selection. */
    }
    this.apply();
  }
  private read(): DesktopThemeChoice {
    try {
      const value = localStorage.getItem(STORAGE_KEY);
      if (value === 'light' || value === 'dark') return value;
    } catch {}
    return 'system';
  }
  private apply() {
    this.desktop.set(this.breakpoint?.matches ?? window.innerWidth > 850);
    const value =
      this.choice() === 'system'
        ? this.system?.matches
          ? 'dark'
          : 'light'
        : (this.choice() as 'light' | 'dark');
    this.effective.set(value);
    document.documentElement.setAttribute('data-desktop-theme', value);
  }
}

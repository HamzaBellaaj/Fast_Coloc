import { Directive, ElementRef, Renderer2, effect, inject, input } from '@angular/core';
export type FeedbackState = 'loading' | 'success' | 'error' | 'warning' | 'pending';
/** Icons complement existing live-region text; they are hidden below the desktop breakpoint. */
@Directive({
  selector: '[fcStatus]',
  host: { '[class.desktop-status]': '!!fcStatus()', '[attr.data-status]': 'fcStatus()' },
})
export class DesktopStatus {
  readonly fcStatus = input<FeedbackState | null>(null);
  private readonly element = inject(ElementRef<HTMLElement>).nativeElement;
  private readonly renderer = inject(Renderer2);
  constructor() {
    const r = this.renderer,
      svg = r.createElement('svg', 'svg');
    for (const [k, v] of Object.entries({
      viewBox: '0 0 24 24',
      fill: 'none',
      stroke: 'currentColor',
      'stroke-width': '1.75',
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
      'aria-hidden': 'true',
      focusable: 'false',
      class: 'fc-state-icon',
    }))
      r.setAttribute(svg, k, v);
    r.insertBefore(this.element, svg, this.element.firstChild);
    effect(() => {
      const state = this.fcStatus();
      while (svg.firstChild) r.removeChild(svg, svg.firstChild);
      if (!state) return;
      const names: Record<FeedbackState, string> = {
        loading: 'loader-circle',
        success: 'circle-check',
        error: 'circle-alert',
        warning: 'triangle-alert',
        pending: 'clock',
      };
      const use = r.createElement('use', 'svg');
      r.setAttribute(use, 'href', '/assets/icons/lucide.svg#' + names[state]);
      r.appendChild(svg, use);
    });
  }
}

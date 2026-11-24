import { Component, input } from '@angular/core';
@Component({
  selector: 'fc-icon',
  template: `<svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.75"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <use [attr.href]="'/assets/icons/lucide.svg#' + name()" />
  </svg>`,
  host: { 'aria-hidden': 'true' },
  styles: [
    ':host{display:inline-flex;flex:0 0 auto;width:1.25em;height:1.25em;vertical-align:middle}svg{width:100%;height:100%}',
  ],
})
export class Icon {
  readonly name = input.required<string>();
}

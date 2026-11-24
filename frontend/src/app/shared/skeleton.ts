import { Component, input } from '@angular/core';
@Component({
  selector: 'fc-skeleton',
  template: `<span class="sr-only">{{ label() }}</span>
    <div
      class="skeleton-layout"
      [class.skeleton-cards]="kind() === 'cards'"
      [class.skeleton-gallery]="kind() === 'gallery'"
      [class.skeleton-rows]="kind() === 'rows'"
      aria-hidden="true"
    >
      @for (i of blocks(); track $index) {
        <div class="skeleton-block">
          <div class="skeleton-picture"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line short"></div>
        </div>
      }
    </div>`,
  host: { role: 'status', 'aria-live': 'polite', '[attr.aria-label]': 'label()' },
})
export class Skeleton {
  readonly kind = input('rows');
  readonly label = input('Chargement…');
  blocks() {
    return Array.from({ length: this.kind() === 'cards' ? 4 : this.kind() === 'gallery' ? 1 : 3 });
  }
}

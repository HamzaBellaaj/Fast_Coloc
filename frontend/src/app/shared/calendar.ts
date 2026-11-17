import { Directive, ElementRef, inject } from '@angular/core';
/** Keep the browser's accessible native calendar and open its whole field. */
@Directive({ selector: 'input[fcCalendar]', host: { '(click)': 'open()' } })
export class Calendar {
  private element = inject<ElementRef<HTMLInputElement>>(ElementRef);
  open() {
    try {
      this.element.nativeElement.showPicker?.();
    } catch {
      /* Native icon remains available. */
    }
  }
}

import { Component, ElementRef, input, output, viewChild } from '@angular/core';

@Component({
  selector: 'fc-block-confirmation',
  template: `
    <dialog #dialog aria-label="Confirmation du blocage personnel">
      <h2 class="card-heading">{{ unblock() ? 'Débloquer cet utilisateur ?' : 'Bloquer cet utilisateur ?' }}</h2>
      <p class="muted">
        @if (unblock()) {
          {{ name() || 'Cet utilisateur' }} pourra de nouveau échanger avec toi si les échanges sont autorisés.
        } @else {
          Vous ne pourrez plus vous envoyer de nouveaux messages. Vos conversations précédentes resteront accessibles.
        }
      </p>
      <div class="row wrap actions">
        <button type="button" class="btn ghost" autofocus (click)="close()">Annuler</button>
        <button type="button" class="btn" [class.danger]="!unblock()" (click)="confirm()">
          {{ unblock() ? 'Débloquer' : 'Bloquer' }}
        </button>
      </div>
    </dialog>
  `,
  styles: [`
    dialog { width: min(440px, calc(100% - 28px)); }
    dialog h2 { color: var(--plum); margin: 0 0 12px; }
    .actions { justify-content: flex-end; margin-top: 24px; }
  `],
})
export class BlockConfirmation {
  unblock = input(false);
  name = input('');
  confirmed = output<void>();
  private dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  open() {
    this.dialog().nativeElement.showModal();
  }

  close() {
    this.dialog().nativeElement.close();
  }

  protected confirm() {
    this.close();
    this.confirmed.emit();
  }
}

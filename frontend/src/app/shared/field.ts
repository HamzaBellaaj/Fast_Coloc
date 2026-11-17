import { Component, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';
@Component({
  selector: 'app-field',
  template: `<div class="field">
    <label [attr.for]="fieldId()">{{ label() }}</label
    ><ng-content />
    @if (control()?.invalid && control()?.touched) {
      <p class="field-error" [id]="fieldId() + '-error'" role="alert">{{ error() }}</p>
    }
  </div>`,
})
export class Field {
  fieldId = input.required<string>();
  label = input.required<string>();
  control = input<AbstractControl | null>(null);
  error() {
    const c = this.control();
    if (c?.hasError('required')) return 'Ce champ est obligatoire.';
    if (c?.hasError('email')) return 'Entre une adresse e-mail valide.';
    if (c?.hasError('passwordPolicy'))
      return 'Choisis entre 12 et 72 caractères (72 octets UTF-8 maximum).';
    if (c?.hasError('listPolicy'))
      return 'Maximum 50 éléments de 100 caractères, séparés par des virgules.';
    return 'Vérifie le format ou la longueur de ce champ.';
  }
}

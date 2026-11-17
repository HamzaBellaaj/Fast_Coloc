import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
export const nonBlank: ValidatorFn = (control) =>
  typeof control.value === 'string' && !control.value.trim() ? { required: true } : null;
export const passwordPolicy: ValidatorFn = (control) => {
  const value = String(control.value ?? '');
  return value.length < 12 || value.length > 72 || new TextEncoder().encode(value).length > 72
    ? { passwordPolicy: true }
    : null;
};
export function matchingPasswords(group: AbstractControl): ValidationErrors | null {
  return group.get('password')?.value === group.get('confirm')?.value
    ? null
    : { passwordMismatch: true };
}
export const listPolicy: ValidatorFn = (control) => {
  const values = splitList(String(control.value ?? ''));
  return values.length > 50 || values.some((v) => v.length > 100) ? { listPolicy: true } : null;
};
export function splitList(value: string): string[] {
  return value
    .split(',')
    .map((v) => v.trim())
    .filter(Boolean);
}

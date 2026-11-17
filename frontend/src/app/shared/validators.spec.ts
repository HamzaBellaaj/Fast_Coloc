import { describe, it, expect } from 'vitest';
import { FormControl, FormGroup } from '@angular/forms';
import { listPolicy, matchingPasswords, nonBlank, passwordPolicy, splitList } from './validators';
describe('Validation des contrats backend', () => {
  it('refuse un mot de passe trop court ou dépassant 72 octets UTF-8', () => {
    expect(passwordPolicy(new FormControl('court'))).not.toBeNull();
    expect(passwordPolicy(new FormControl('é'.repeat(37)))).not.toBeNull();
    expect(passwordPolicy(new FormControl('é'.repeat(36)))).toBeNull();
  });
  it('impose la confirmation exacte du mot de passe', () => {
    const group = new FormGroup({
      password: new FormControl('un-mot-de-passe'),
      confirm: new FormControl('autre'),
    });
    expect(matchingPasswords(group)).toEqual({ passwordMismatch: true });
    group.controls.confirm.setValue('un-mot-de-passe');
    expect(matchingPasswords(group)).toBeNull();
  });
  it('rejette les noms vides après trim', () => {
    expect(nonBlank(new FormControl('  '))).toEqual({ required: true });
    expect(nonBlank(new FormControl(' Nour '))).toBeNull();
  });
  it('respecte les limites des listes sans imposer les informations facultatives', () => {
    expect(splitList(' arabe, français, , anglais ')).toEqual(['arabe', 'français', 'anglais']);
    expect(listPolicy(new FormControl(''))).toBeNull();
    expect(listPolicy(new FormControl(Array(51).fill('langue').join(',')))).not.toBeNull();
    expect(listPolicy(new FormControl('a'.repeat(101)))).not.toBeNull();
  });
});

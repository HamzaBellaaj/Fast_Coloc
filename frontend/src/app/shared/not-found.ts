import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({
  imports: [RouterLink],
  template: `<section class="success-box">
    <div class="illustration" aria-hidden="true">
      <img src="/assets/fastcoloc-symbol.svg" width="100" height="100" alt="" />
    </div>
    <h1>Cette page a déménagé.</h1>
    <p>Retrouve ton chemin vers FastColoc.</p>
    <a class="btn" routerLink="/">Retour à l’accueil</a>
  </section>`,
})
export class NotFound {}

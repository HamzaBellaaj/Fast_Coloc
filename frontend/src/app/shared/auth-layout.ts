import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({
  selector: 'app-auth-layout',
  imports: [RouterLink],
  template: ` <div class="split">
    <aside class="split-side">
      <div>
        <a class="brand" routerLink="/">fast<b>coloc</b><span class="coral-text">.</span></a>
        <div class="side-copy">
          <h2>
            {{
              registration() ? 'Un nouveau départ commence ici.' : 'La coloc, c’est mieux ensemble.'
            }}
          </h2>
          <p>
            {{
              registration()
                ? 'Cinq étapes simples pour rejoindre une communauté et trouver le logement qui te ressemble.'
                : 'Trouve ton logement, rencontre tes futurs colocs et commence une nouvelle aventure.'
            }}
          </p>
        </div>
      </div>
      <div class="side-art" aria-hidden="true">
        <img src="/assets/fastcoloc-symbol.svg" width="130" height="130" alt="" />
      </div>
    </aside>
    <section class="split-main">
      <span class="tag">BIENVENUE CHEZ FASTCOLOC</span><ng-content />
    </section>
  </div>`,
})
export class AuthLayout {
  registration = input(false);
}

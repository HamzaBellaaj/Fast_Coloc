import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Catalog } from '../listings/catalog';
@Component({
  imports: [RouterLink, Catalog],
  template: ` <section class="hero">
      <div>
        <span class="tag">✦ BIENVENUE DANS TA FUTURE COLOC</span>
        <h1>
          La coloc,<br /><span class="pink-text">c’est mieux</span><br />ensemble<span
            class="coral-text"
            >.</span
          >
        </h1>
        <p>
          Des personnes à rencontrer, un quotidien à partager. Ton aventure commence avec un profil
          qui te ressemble.
        </p>
        <div class="row wrap">
          <a class="btn coral" routerLink="/register/1">Rejoindre FastColoc →</a
          ><a class="hero-link" routerLink="/login">J’ai déjà un compte ↗</a>
        </div>
      </div>
      <div class="hero-art" aria-hidden="true"><div class="art-house"></div></div>
    </section>
    <fc-catalog />
    <div class="content-head">
      <div>
        <span class="tag">TON NOUVEAU DÉPART</span>
        <h2>Fais comme chez toi.</h2>
      </div>
    </div>
    <section class="cards" aria-label="Les étapes pour rejoindre FastColoc">
      <article class="card">
        <div class="card-image"><span class="feature-emoji" aria-hidden="true">👋</span></div>
        <div class="card-body">
          <span class="pill">01 · Fais connaissance</span>
          <h3>Ton profil, ta personnalité</h3>
          <p class="muted">
            Présente-toi, partage tes centres d’intérêt et tes habitudes, à ton rythme.
          </p>
        </div>
      </article>
      <article class="card">
        <div class="card-image alt"><span class="feature-emoji" aria-hidden="true">💌</span></div>
        <div class="card-body">
          <span class="pill">02 · En toute confiance</span>
          <h3>Une adresse vérifiée</h3>
          <p class="muted">
            Confirme ton e-mail pour poursuivre ton inscription dans un espace sécurisé.
          </p>
        </div>
      </article>
      <article class="card">
        <div class="card-image alt2"><span class="feature-emoji" aria-hidden="true">📸</span></div>
        <div class="card-body">
          <span class="pill">03 · Sois toi-même</span>
          <h3>Le sourire en plus</h3>
          <p class="muted">
            Ajoute tes photos et retrouve tes informations dans ton espace personnel privé.
          </p>
        </div>
      </article>
    </section>
    <section class="grid2 home-benefits">
      <div class="section-card">
        <h3>🏡 Des logements qui te ressemblent</h3>
        <p class="muted">
          Trouve les offres et les demandes de colocation grâce à la recherche par ville et budget.
        </p>
        <a class="link" routerLink="/annonces">Découvrir les annonces ↗</a>
      </div>
      <div class="section-card">
        <h3>💜 Plus qu’un toit : une communauté</h3>
        <p class="muted">
          Un espace pensé pour les étudiants et les jeunes actifs en Tunisie. Cinq étapes pour
          commencer.
        </p>
        <a class="link" routerLink="/register/1">Créer mon profil ↗</a>
      </div>
    </section>`,
})
export class Home {}

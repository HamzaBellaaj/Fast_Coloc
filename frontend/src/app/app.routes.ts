import { Routes } from '@angular/router';
import { adminGuard, registrationGuard, sessionGuard } from './core/guards';
export const routes: Routes = [
  {path:'messages',title:'Messages ? FastColoc',canActivate:[sessionGuard],loadComponent:()=>import('./features/messages/messages').then(m=>m.Messages)},
  {path:'messages/:id',title:'Discussion ? FastColoc',canActivate:[sessionGuard],loadComponent:()=>import('./features/messages/messages').then(m=>m.Messages)},
  {
    path: 'pour-toi',
    title: 'Pour toi ✨ · FastColoc',
    canActivate: [sessionGuard],
    loadComponent: () =>
      import('./features/listings/recommendations').then((m) => m.Recommendations),
  },
  {
    path: 'annonces',
    loadComponent: () => import('./features/listings/catalog').then((m) => m.Catalog),
  },
  {
    path: 'mes-annonces',
    canActivate: [sessionGuard],
    data: { mode: 'mine' },
    loadComponent: () => import('./features/listings/catalog').then((m) => m.Catalog),
  },
  {
    path: 'favoris',
    canActivate: [sessionGuard],
    data: { mode: 'favorites' },
    loadComponent: () => import('./features/listings/catalog').then((m) => m.Catalog),
  },
  {
    path: 'annonces/nouvelle',
    canActivate: [sessionGuard],
    loadComponent: () => import('./features/listings/listing-editor').then((m) => m.ListingEditor),
  },
  {
    path: 'annonces/:id/modifier',
    canActivate: [sessionGuard],
    loadComponent: () => import('./features/listings/listing-editor').then((m) => m.ListingEditor),
  },
  {
    path: 'annonces/:id',
    canActivate: [sessionGuard],
    loadComponent: () => import('./features/listings/listing-detail').then((m) => m.ListingDetail),
  },
  {
    path: 'payment/success',
    title: 'Vérification du paiement · FastColoc',
    canActivate: [sessionGuard],
    loadComponent: () => import('./features/payment/payment-success').then((m) => m.PaymentSuccess),
  },
  {
    path: 'payment/cancel',
    title: 'Paiement annulé · FastColoc',
    canActivate: [sessionGuard],
    data: { cancel: true },
    loadComponent: () => import('./features/payment/payment-success').then((m) => m.PaymentSuccess),
  },
  {
    path: '',
    title: 'FastColoc · La coloc, c’est mieux ensemble',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  {
    path: 'login',
    title: 'Connexion · FastColoc',
    loadComponent: () => import('./features/auth/auth-page').then((m) => m.AuthPage),
    data: { mode: 'login' },
  },
  {
    path: 'forgot-password',
    title: 'Mot de passe oublié · FastColoc',
    loadComponent: () => import('./features/auth/auth-page').then((m) => m.AuthPage),
    data: { mode: 'forgot' },
  },
  {
    path: 'reset-password',
    title: 'Nouveau mot de passe · FastColoc',
    loadComponent: () => import('./features/auth/auth-page').then((m) => m.AuthPage),
    data: { mode: 'reset' },
  },
  {
    path: 'verify-email',
    title: 'Confirmation e-mail · FastColoc',
    loadComponent: () => import('./features/auth/verify').then((m) => m.Verify),
  },
  {
    path: 'auth/callback',
    title: 'Vérification sécurisée · FastColoc',
    loadComponent: () => import('./features/auth/callback').then((m) => m.Callback),
  },
  {
    path: 'register/:step',
    title: 'Inscription en cinq étapes · FastColoc',
    canActivate: [registrationGuard],
    runGuardsAndResolvers: 'paramsChange',
    loadComponent: () => import('./features/onboarding/registration').then((m) => m.Registration),
  },
  { path: 'register', redirectTo: 'register/1', pathMatch: 'full' },
  {
    path: 'membres/:id',
    canActivate: [sessionGuard],
    loadComponent: () => import('./features/profile/member-profile').then((m) => m.MemberProfile),
  },
  {
    path: 'profile',
    title: 'Mon profil · FastColoc',
    canActivate: [sessionGuard],
    loadComponent: () => import('./features/profile/profile').then((m) => m.ProfilePage),
  },
  {
    path: 'admin/annonces/:id/modifier',
    title: 'Correction administrative · FastColoc',
    canActivate: [adminGuard],
    data: { adminMode: true },
    loadComponent: () => import('./features/listings/listing-editor').then((m) => m.ListingEditor),
  },
  {
    path: 'admin/annonces/:id',
    title: 'Modération d’une annonce · FastColoc',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/admin/admin-listings').then((m) => m.AdminListings),
  },
  {
    path: 'admin/annonces',
    title: 'Gestion des annonces · FastColoc',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/admin/admin-listings').then((m) => m.AdminListings),
  },
  {
    path: 'admin',
    title: 'Administration · FastColoc',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/admin/admin').then((m) => m.AdminPage),
  },
  {
    path: '**',
    title: 'Page introuvable · FastColoc',
    loadComponent: () => import('./shared/not-found').then((m) => m.NotFound),
  },
];

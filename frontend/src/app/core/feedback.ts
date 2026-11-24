import { Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const code: string = error.error?.code ?? '';
    const messages: Record<string, string> = {
      INVALID_POSITION: 'Position refusée : choisis un point terrestre en Tunisie, hors eau et zones humides.',
      GEOGRAPHY_UNAVAILABLE: 'La position terrestre ne peut pas être vérifiée. Réessaie avant d’enregistrer.',
      ACCOUNT_BLOCKED: 'Ce compte est bloqué. Contacte l’administration FastColoc.',
      INVALID_CREDENTIALS: 'E-mail ou mot de passe incorrect, ou adresse non confirmée.',
      LISTING_HIDDEN:
        'Cette annonce est masquée par l’administration. Sa republication est bloquée.',
      STALE_LISTING: 'Cette annonce a changé. Recharge la page avant de confirmer.',
      ACCESS_DENIED: 'Tu ne disposes pas des droits nécessaires.',
      ONBOARDING_INCOMPLETE: 'Enregistre les cinq étapes avant de finaliser ton inscription.',
      USER_NOT_FOUND: 'Cet utilisateur est introuvable.',
      SELF_BLOCK: 'Tu ne peux pas te bloquer.',
      MESSAGE_RECIPIENT_UNAVAILABLE: 'Cet échange est indisponible.',
      INVALID_REQUEST: 'Vérifie les champs et leurs formats avant de réessayer.',
      PHOTO_QUOTA: 'Tu peux ajouter au maximum cinq photos.',
      PHOTO_SIZE: 'Chaque photo doit peser au maximum 5 MiB.',
      INVALID_PHOTO:
        'Cette image ne peut pas être décodée. Choisis un fichier JPEG, PNG ou WebP valide.',
      GOOGLE_NOT_CONFIGURED: 'La connexion Google est temporairement indisponible.',
    };
    if (messages[code]) return messages[code];
    if (error.status === 0)
      return 'Connexion au serveur impossible. Vérifie ta connexion et réessaie.';
    if (error.status === 401) return 'Ta session a expiré. Reconnecte-toi pour continuer.';
    if (error.status === 403) return 'Accès refusé pour ce compte.';
    if (error.status === 429)
      return 'Trop de demandes. Patiente quelques instants avant de réessayer.';
    return 'La demande n’a pas abouti. Réessaie dans quelques instants.';
  }
  return error instanceof Error ? error.message : 'Une erreur est survenue. Réessaie.';
}
@Injectable({ providedIn: 'root' })
export class Feedback {
  message = signal('');
  failure = signal(false);
  show(message: string, failure = false) {
    this.failure.set(failure);
    this.message.set(message);
  }
  error(error: unknown) {
    this.show(errorMessage(error), true);
  }
  clear() {
    this.message.set('');
  }
}

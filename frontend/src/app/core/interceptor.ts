import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { Auth } from './auth';
import { Feedback } from './feedback';
import { environment } from '../../environments/environment';

export const sessionInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(Auth),
    router = inject(Router),
    feedback = inject(Feedback);
  if (!request.url.startsWith(environment.apiUrl + '/')) return next(request);
  const path = request.url.slice(environment.apiUrl.length).split('?')[0];
  const privateRequest =
    !path.startsWith('/auth/') ||
    ['/auth/session', '/auth/logout', '/auth/password'].includes(path);
  if (!privateRequest || !auth.session()) return next(request);
  const send = (token: string) =>
    next(request.clone({ setHeaders: { Authorization: 'Bearer ' + token } }));
  return from(auth.token()).pipe(
    switchMap(send),
    catchError((error: unknown) => {
      if (!auth.session()) {
        feedback.show('Ta session a expiré. Reconnecte-toi.', true);
        void router.navigateByUrl('/login');
        return throwError(() => error);
      }
      if (error instanceof HttpErrorResponse && error.status === 401 && auth.session()) {
        return from(auth.token(true)).pipe(
          switchMap(send),
          catchError((failure) => {
            // A failed refresh or second 401 ends the session; network errors remain retryable.
            if (
              !(failure instanceof HttpErrorResponse) ||
              [400, 401, 403].includes(failure.status)
            ) {
              auth.clear();
              feedback.show('Ta session a expiré. Reconnecte-toi.', true);
              void router.navigateByUrl('/login');
            }
            return throwError(() => failure);
          }),
        );
      }
      if (error instanceof HttpErrorResponse && error.error?.code === 'ACCOUNT_BLOCKED') {
        auth.clear();
        feedback.error(error);
        void router.navigateByUrl('/login');
      }
      return throwError(() => error);
    }),
  );
};

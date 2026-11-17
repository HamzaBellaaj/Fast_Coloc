import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth';
import { Api } from './api';
import { Feedback } from './feedback';
export const sessionGuard: CanActivateFn = async (_, state) => {
  const auth = inject(Auth),
    router = inject(Router);
  await auth.restore();
  if (auth.session()) return true;
  auth.rememberDestination(state.url);
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
export const registrationGuard: CanActivateFn = async (route) => {
  const auth = inject(Auth),
    router = inject(Router);
  await auth.restore();
  return route.paramMap.get('step') === '1' || auth.session() ? true : router.parseUrl('/login');
};
export const adminGuard: CanActivateFn = async () => {
  const auth = inject(Auth),
    api = inject(Api),
    router = inject(Router),
    feedback = inject(Feedback);
  await auth.restore();
  if (!auth.session()) return router.parseUrl('/login');
  try {
    await api.request('GET', '/admin/access');
    return true;
  } catch (error) {
    feedback.error(error);
    return router.parseUrl('/profile');
  }
};

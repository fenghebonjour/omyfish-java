import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Every protected page in the React app (account, admin, notifications,
 * observations) repeats the same `useEffect(() => { if (!isAuthenticated)
 * router.push('/login') }, [...])` block inside the component itself,
 * because React Router / Next's app router has no first-class "route guard"
 * concept — access control is just a side effect that happens to run in
 * the component.
 *
 * Angular has a dedicated extension point for this: a `CanActivateFn` runs
 * *before* the component is even created, wired once per route in
 * app.routes.ts instead of pasted into every protected page. Returning
 * `false` (or, better, a UrlTree) cancels navigation — the guarded
 * component's constructor never runs at all for a rejected navigation,
 * whereas React's version always mounts the page and then redirects away
 * from it.
 */
export const authGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // Wait out the same refresh-token exchange React's pages wait for via
  // `if (authLoading) return;` before trusting isAuthenticated() — a guard
  // is allowed to be async and the router simply pauses navigation for it,
  // no separate loading state needs to leak into the page component.
  await auth.ready;
  return auth.isAuthenticated() ? true : router.parseUrl('/login');
};

import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

/**
 * Angular twin of the Next.js App Router's file-system routing
 * (app/page.tsx → "/", app/identify/page.tsx → "/identify", ...). Angular
 * has no folder-based convention — routes are one explicit array mapping
 * paths to components, same information Next.js infers from where a
 * page.tsx file sits.
 *
 * `loadComponent` is the direct analog of what Next.js does automatically
 * for every page: each route's component (and everything only it imports)
 * ships in its own lazy-loaded chunk, fetched the first time that route is
 * visited rather than bundled into the initial download.
 *
 * `canActivate: [authGuard]` replaces the `if (!isAuthenticated)
 * router.push('/login')` block repeated inside account/admin/notifications/
 * observations' React components — see core/auth.guard.ts.
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/timing/timing-page').then((m) => m.TimingPage),
  },
  {
    path: 'identify',
    loadComponent: () => import('./features/identify/identify-page').then((m) => m.IdentifyPage),
  },
  {
    path: 'regs',
    loadComponent: () => import('./features/regs/regs-page').then((m) => m.RegsPage),
  },
  {
    path: 'login',
    loadComponent: () => import('./features/login/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    loadComponent: () => import('./features/register/register-page').then((m) => m.RegisterPage),
  },
  {
    path: 'account',
    loadComponent: () => import('./features/account/account-page').then((m) => m.AccountPage),
    canActivate: [authGuard],
  },
  {
    path: 'admin',
    loadComponent: () => import('./features/admin/admin-page').then((m) => m.AdminPage),
    canActivate: [authGuard],
  },
  {
    path: 'notifications',
    loadComponent: () => import('./features/notifications/notifications-page').then((m) => m.NotificationsPage),
    canActivate: [authGuard],
  },
  {
    path: 'observations',
    loadComponent: () => import('./features/observations/observations-page').then((m) => m.ObservationsPage),
    canActivate: [authGuard],
  },
];

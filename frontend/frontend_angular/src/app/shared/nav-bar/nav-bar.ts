import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

/**
 * Angular twin of components/NavBar.tsx.
 *
 * `RouterLink` replaces Next's `<Link>`. The auth-conditional links used
 * `{isAuthenticated && (<>...</>)}` in JSX — Angular's `@if` block below
 * reads the same left-to-right, no fragment (`<>`) needed for a multi-
 * element conditional since `@if` isn't limited to one child like a JSX
 * expression is.
 */
@Component({
  selector: 'app-nav-bar',
  imports: [RouterLink],
  templateUrl: './nav-bar.html',
})
export class NavBar {
  auth = inject(AuthService);
  private router = inject(Router);

  handleLogout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/');
  }
}

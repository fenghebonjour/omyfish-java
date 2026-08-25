import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavBar } from './shared/nav-bar/nav-bar';

/**
 * Angular twin of app/layout.tsx. Next's RootLayout wraps `children` (the
 * matched page) with <AuthProvider><NavBar />{children}</AuthProvider>.
 * Angular has no children-slot layout component by convention — the root
 * component here plays that role directly, and `<router-outlet>` is where
 * the matched route's component is inserted (App Router's implicit
 * `{children}` slot, made explicit). AuthService needs no <AuthProvider>
 * wrapper at all — see core/auth.service.ts for why.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NavBar],
  templateUrl: './app.html',
})
export class App {}

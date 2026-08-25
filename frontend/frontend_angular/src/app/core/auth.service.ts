import { Injectable, inject, signal, computed } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import type { TokenResponse } from './models';

const KEY_TOKEN = 'omyfish_token';
const KEY_REFRESH = 'omyfish_refresh';
const KEY_USER_ID = 'omyfish_userId';
const KEY_EMAIL = 'omyfish_email';

/**
 * Angular twin of contexts/AuthContext.tsx.
 *
 * React needs a Context + <AuthProvider> wrapper component so descendants
 * can useContext() it, plus a useAuth() hook that throws if you forgot the
 * wrapper. Angular's DI container makes a service a singleton just by
 * declaring `providedIn: 'root'` — any component can `inject(AuthService)`
 * directly, no wrapping component in the template and no "did you forget
 * the Provider" runtime check required, because the injector always has one.
 *
 * React's `useState` + `useEffect` becomes Angular `signal()` — a
 * `signal()` read inside a template auto-subscribes that binding, so
 * updating `token.set(...)` re-renders only the DOM that reads `token()`,
 * not the whole component tree (React's default is the opposite: a state
 * update re-renders the whole component function, and you opt out with
 * memoization, not in like Angular does with fine-grained signals).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private api = inject(ApiService);

  // Three separate signals mirror the three separate useState fields
  // React kept in one `AuthState` object.
  token = signal<string | null>(null);
  userId = signal<string | null>(null);
  email = signal<string | null>(null);
  isLoading = signal(true);

  // `computed()` is Angular's useMemo: it only recalculates when a signal
  // it reads (`token`) actually changes, and it's read the same way as a
  // plain signal — `isAuthenticated()` — from templates or code.
  isAuthenticated = computed(() => !!this.token());

  // authGuard awaits this before letting a protected route activate — the
  // guard's replacement for the `if (authLoading) return;` early-out that
  // every protected React page repeats at the top of its useEffect.
  readonly ready: Promise<void>;

  constructor() {
    // A service body runs once, at first injection — the direct analog of
    // AuthProvider's `useEffect(..., [])` mount-only effect.
    const token = localStorage.getItem(KEY_TOKEN);
    const userId = localStorage.getItem(KEY_USER_ID);
    const email = localStorage.getItem(KEY_EMAIL);
    const refreshToken = localStorage.getItem(KEY_REFRESH);

    if (token) {
      this.token.set(token);
      this.userId.set(userId);
      this.email.set(email);
      this.isLoading.set(false);
      this.ready = Promise.resolve();
    } else if (refreshToken) {
      this.ready = firstValueFrom(this.api.auth.refresh(refreshToken))
        .then((resp) => {
          this.persist(resp);
          this.token.set(resp.token);
          this.userId.set(resp.userId);
          this.email.set(resp.email);
        })
        .catch(() => this.clearStorage())
        .finally(() => this.isLoading.set(false));
    } else {
      this.isLoading.set(false);
      this.ready = Promise.resolve();
    }
  }

  private persist(resp: TokenResponse) {
    localStorage.setItem(KEY_TOKEN, resp.token);
    localStorage.setItem(KEY_REFRESH, resp.refreshToken);
    localStorage.setItem(KEY_USER_ID, resp.userId);
    localStorage.setItem(KEY_EMAIL, resp.email);
  }

  private clearStorage() {
    localStorage.removeItem(KEY_TOKEN);
    localStorage.removeItem(KEY_REFRESH);
    localStorage.removeItem(KEY_USER_ID);
    localStorage.removeItem(KEY_EMAIL);
  }

  async login(email: string, password: string): Promise<void> {
    // firstValueFrom subscribes to the Observable and hands back a Promise
    // for exactly one value — the bridge Angular code reaches for whenever
    // it wants React-style `await api.call()` instead of `.subscribe()`.
    const resp = await firstValueFrom(this.api.auth.login(email, password));
    this.persist(resp);
    this.token.set(resp.token);
    this.userId.set(resp.userId);
    this.email.set(resp.email);
  }

  logout(): void {
    this.clearStorage();
    this.token.set(null);
    this.userId.set(null);
    this.email.set(null);
  }
}

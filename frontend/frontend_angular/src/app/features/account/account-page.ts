import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ApiService } from '../../core/api.service';
import type { SubscriptionDto } from '../../core/models';
import { errorMessage } from '../../core/http-error.util';

const PLAN_LABELS: Record<string, string> = {
  monthly: '5 CAD / month',
  yearly: '29 CAD / year',
};

/**
 * Angular twin of app/account/page.tsx.
 *
 * React's version does the auth check itself:
 * `if (authLoading) return; if (!isAuthenticated) router.push('/login')`
 * inside a useEffect. That's gone here — the `authGuard` on this route in
 * app.routes.ts already guarantees the component never even constructs
 * unless the user is authenticated, so the constructor below can go
 * straight to loading the subscription.
 */
@Component({
  selector: 'app-account-page',
  imports: [DatePipe],
  templateUrl: './account-page.html',
})
export class AccountPage {
  auth = inject(AuthService);
  private api = inject(ApiService);

  sub = signal<SubscriptionDto | null>(null);
  error = signal<string | null>(null);
  busy = signal(false);
  planLabels = PLAN_LABELS;
  plans: Array<'monthly' | 'yearly'> = ['monthly', 'yearly'];

  constructor() {
    firstValueFrom(this.api.billing.me(this.auth.token()!))
      .then((s) => this.sub.set(s))
      .catch((e) => this.error.set(errorMessage(e)));
  }

  async subscribe(plan: 'monthly' | 'yearly'): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      const { checkoutUrl } = await firstValueFrom(this.api.billing.checkout(plan, this.auth.token()!));
      window.location.href = checkoutUrl;
    } catch (e) {
      // React detects this by string-matching "503" inside the Error's
      // message (apiFetch's `${res.status}: ${text}` text). HttpErrorResponse
      // carries the status as a real number instead, so no string-sniffing needed.
      this.error.set(
        e instanceof HttpErrorResponse && e.status === 503
          ? 'Payments are not configured on this deployment.'
          : errorMessage(e),
      );
      this.busy.set(false);
    }
  }

  trialDaysLeft(): number {
    const trialEnd = this.sub()?.trialEnd;
    return trialEnd ? Math.max(0, Math.ceil((new Date(trialEnd).getTime() - Date.now()) / 86_400_000)) : 0;
  }
}

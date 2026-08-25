import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ApiService } from '../../core/api.service';
import type { AdminStats, AdminSubscriptionRow } from '../../core/models';
import { errorMessage } from '../../core/http-error.util';

const STATUS_STYLES: Record<string, string> = {
  active: 'bg-green-50 text-green-700 border-green-200',
  trialing: 'bg-blue-50 text-blue-700 border-blue-200',
  expired: 'bg-amber-50 text-amber-700 border-amber-200',
  canceled: 'bg-gray-100 text-gray-500 border-gray-200',
};

/**
 * Angular twin of app/admin/page.tsx. `authGuard` replaces the redirect
 * half of the React version; the 403-vs-other-error split (`forbidden` vs
 * `error`) is real business logic (not routing), so it stays here — but
 * checked via `HttpErrorResponse.status === 403` rather than React's
 * `String(e).startsWith('403')`, since HttpErrorResponse carries the status
 * as a real number instead of something to string-sniff out of a message.
 */
@Component({
  selector: 'app-admin-page',
  imports: [DatePipe],
  templateUrl: './admin-page.html',
})
export class AdminPage {
  private auth = inject(AuthService);
  private api = inject(ApiService);

  stats = signal<AdminStats | null>(null);
  subs = signal<AdminSubscriptionRow[]>([]);
  forbidden = signal(false);
  error = signal<string | null>(null);
  statusStyles = STATUS_STYLES;

  constructor() {
    this.load();
  }

  private load(): void {
    const token = this.auth.token()!;
    Promise.all([firstValueFrom(this.api.admin.stats(token)), firstValueFrom(this.api.admin.subscriptions(token))])
      .then(([s, rows]) => {
        this.stats.set(s);
        this.subs.set(rows);
      })
      .catch((e) => {
        if (e instanceof HttpErrorResponse && e.status === 403) this.forbidden.set(true);
        else this.error.set(errorMessage(e));
      });
  }

  async act(fn: () => Promise<unknown>): Promise<void> {
    this.error.set(null);
    try {
      await fn();
      this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  grant(userId: string): void {
    this.act(() => firstValueFrom(this.api.admin.grant(userId, this.auth.token()!)));
  }

  extendTrial(userId: string): void {
    this.act(() => firstValueFrom(this.api.admin.extendTrial(userId, this.auth.token()!)));
  }

  revoke(userId: string): void {
    this.act(() => firstValueFrom(this.api.admin.revoke(userId, this.auth.token()!)));
  }
}

import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ApiService } from '../../core/api.service';
import type { NotificationDto } from '../../core/models';
import { errorMessage } from '../../core/http-error.util';

/** Angular twin of app/notifications/page.tsx — protected by authGuard, see admin-page.ts. */
@Component({
  selector: 'app-notifications-page',
  imports: [DatePipe],
  templateUrl: './notifications-page.html',
})
export class NotificationsPage {
  private auth = inject(AuthService);
  private api = inject(ApiService);

  notifications = signal<NotificationDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  unread = computed(() => this.notifications().filter((n) => !n.isRead).length);

  constructor() {
    firstValueFrom(this.api.notifications.getAll(this.auth.token()!))
      .then((ns) => this.notifications.set(ns))
      .catch((err) => this.error.set(errorMessage(err)))
      .finally(() => this.loading.set(false));
  }

  async handleRead(id: string): Promise<void> {
    try {
      await firstValueFrom(this.api.notifications.markRead(id, this.auth.token()!));
      this.notifications.update((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)));
    } catch {
      // ignore
    }
  }
}

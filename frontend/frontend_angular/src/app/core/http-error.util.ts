import { HttpErrorResponse } from '@angular/common/http';

/**
 * React's fetch-based apiFetch (lib/api.ts) always throws a plain `Error`
 * on a failed request, so every catch block in the React app can safely do
 * `e instanceof Error ? e.message : "fallback"`. Angular's HttpClient
 * throws an `HttpErrorResponse` instead — a different class that is *not*
 * `instanceof Error` — so a straight copy of that check silently falls
 * through to the fallback branch on every real HTTP failure (prints
 * "[object Object]" if you're doing `String(e)`). This is the one-place
 * fix: unwrap HttpErrorResponse first, then Error, then give up.
 */
export function errorMessage(e: unknown, fallback = 'Something went wrong'): string {
  if (e instanceof HttpErrorResponse) {
    if (typeof e.error === 'string' && e.error) return e.error;
    if (e.error?.message) return e.error.message;
    return e.message;
  }
  if (e instanceof Error) return e.message;
  return fallback;
}

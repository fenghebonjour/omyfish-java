import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { routes } from './app.routes';

// Angular has no build-in fetch wrapper like the React app's lib/api.ts —
// HttpClient is provided once here (like a global fetch) and injected
// wherever ApiService needs it, instead of every module importing `fetch`.
//
// Note what's *not* here: no zone.js, no provideZoneChangeDetection(). This
// scaffold is zoneless by default — the CLI no longer patches every async
// browser API to auto-detect changes. Instead, a component only re-renders
// when a *signal* it reads changes (see core/auth.service.ts,
// features/timing/timing-page.ts, ...), which is conceptually the closest
// Angular gets to React's own model: nothing re-runs until something
// explicitly says its value changed, rather than after every event/timer/
// promise tick app-wide.
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
  ]
};

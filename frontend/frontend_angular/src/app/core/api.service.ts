import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import * as M from './models';

/**
 * Angular twin of src/lib/api.ts. Same method names, same paths, same
 * per-call token parameter (no global auth interceptor — kept explicit to
 * mirror the React version line for line). The one structural difference
 * that matters for comparison:
 *
 *   React `fetch`             Angular `HttpClient`
 *   -----------------------   ------------------------------------------
 *   returns a Promise<T>      returns an Observable<T>
 *   fires the instant you     fires only once something calls
 *     call it (eager/"hot")     .subscribe() (lazy/"cold") — an
 *                                Observable you never subscribe to never
 *                                runs, unlike a Promise you never await.
 *   you must check res.ok     non-2xx responses are pushed down the
 *     and throw yourself        error channel automatically as an
 *                                HttpErrorResponse — no manual ok-check.
 *   body must be response      response body is parsed for you based on
 *     .json()'d by hand          the inferred/declared generic type.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = environment.apiBase;

  private authHeaders(token?: string): HttpHeaders | undefined {
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : undefined;
  }

  auth = {
    login: (email: string, password: string): Observable<M.TokenResponse> =>
      this.http.post<M.TokenResponse>(`${this.base}/api/v1/auth/login`, { email, password }),

    register: (email: string, password: string, displayName?: string): Observable<M.UserDto> =>
      this.http.post<M.UserDto>(`${this.base}/api/v1/auth/register`, { email, password, displayName }),

    refresh: (refreshToken: string): Observable<M.TokenResponse> =>
      this.http.post<M.TokenResponse>(`${this.base}/api/v1/auth/refresh`, { refreshToken }),

    me: (token: string): Observable<M.UserDto> =>
      this.http.get<M.UserDto>(`${this.base}/api/v1/auth/me`, { headers: this.authHeaders(token) }),
  };

  species = {
    identify: (image: File, topK = 5, token?: string): Observable<M.IdentifyFishResult> => {
      const form = new FormData();
      form.append('image', image);
      form.append('topK', String(topK));
      return this.http.post<M.IdentifyFishResult>(`${this.base}/api/v1/species/identify`, form, {
        headers: this.authHeaders(token),
      });
    },

    getAll: (northAmericanFreshwater?: boolean): Observable<M.PredictionDto[]> => {
      let params = new HttpParams();
      if (northAmericanFreshwater !== undefined) {
        params = params.set('northAmericanFreshwater', String(northAmericanFreshwater));
      }
      return this.http.get<M.PredictionDto[]>(`${this.base}/api/v1/species`, { params });
    },
  };

  biteScore = {
    // species accepts a profile key or any common/scientific name from a
    // confirmed fish ID — the backend resolves it (general fallback).
    today: (lat: number, lon: number, species = 'general'): Observable<M.BiteForecast> =>
      this.http.get<M.BiteForecast>(`${this.base}/api/v1/species/bite-score/today`, {
        params: { lat, lon, species },
      }),

    forecast: (lat: number, lon: number, species = 'general', hours = 336): Observable<M.BiteForecast> =>
      this.http.get<M.BiteForecast>(`${this.base}/api/v1/species/bite-score/forecast`, {
        params: { lat, lon, species, hours },
      }),
  };

  // Quebec fishing regs/consumption advisor — proxied from omyfish-ai by
  // every backend at the same path, chatbot/retrieval logic lives there only.
  regs = {
    limits: (lat: number, lon: number, species = 'general'): Observable<M.RegsLimits> =>
      this.http.get<M.RegsLimits>(`${this.base}/api/v1/species/regs/limits`, { params: { lat, lon, species } }),

    zonesGeoJson: (): Observable<GeoJSON.FeatureCollection> =>
      this.http.get<GeoJSON.FeatureCollection>(`${this.base}/api/v1/species/regs/zones/geojson`),

    consumptionStations: (lat: number, lon: number, limit = 5): Observable<M.RegsStation[]> =>
      this.http.get<M.RegsStation[]>(`${this.base}/api/v1/species/regs/consumption/stations`, {
        params: { lat, lon, limit },
      }),

    consumption: (lat: number, lon: number, species = 'general'): Observable<M.RegsConsumption> =>
      this.http.get<M.RegsConsumption>(`${this.base}/api/v1/species/regs/consumption`, {
        params: { lat, lon, species },
      }),

    ask: (question: string): Observable<M.RegsAskResponse> =>
      this.http.post<M.RegsAskResponse>(`${this.base}/api/v1/species/regs/ask`, { question }),
  };

  notifications = {
    getAll: (token: string): Observable<M.NotificationDto[]> =>
      this.http.get<M.NotificationDto[]>(`${this.base}/api/v1/notifications`, { headers: this.authHeaders(token) }),

    markRead: (id: string, token: string): Observable<void> =>
      this.http
        .put(`${this.base}/api/v1/notifications/${id}/read`, null, {
          headers: this.authHeaders(token),
          responseType: 'text',
        })
        .pipe(map(() => undefined)),
  };

  billing = {
    me: (token: string): Observable<M.SubscriptionDto> =>
      this.http.get<M.SubscriptionDto>(`${this.base}/api/v1/billing/me`, { headers: this.authHeaders(token) }),

    checkout: (plan: 'monthly' | 'yearly', token: string): Observable<{ checkoutUrl: string }> =>
      this.http.post<{ checkoutUrl: string }>(
        `${this.base}/api/v1/billing/checkout`,
        { plan },
        { headers: this.authHeaders(token) },
      ),
  };

  admin = {
    stats: (token: string): Observable<M.AdminStats> =>
      this.http.get<M.AdminStats>(`${this.base}/api/v1/admin/stats`, { headers: this.authHeaders(token) }),

    subscriptions: (token: string): Observable<M.AdminSubscriptionRow[]> =>
      this.http.get<M.AdminSubscriptionRow[]>(`${this.base}/api/v1/admin/subscriptions`, {
        headers: this.authHeaders(token),
      }),

    grant: (userId: string, token: string, days = 365, plan = 'yearly'): Observable<M.SubscriptionDto> =>
      this.http.post<M.SubscriptionDto>(
        `${this.base}/api/v1/admin/subscriptions/${userId}/grant`,
        { days, plan },
        { headers: this.authHeaders(token) },
      ),

    revoke: (userId: string, token: string): Observable<M.SubscriptionDto> =>
      this.http.post<M.SubscriptionDto>(
        `${this.base}/api/v1/admin/subscriptions/${userId}/revoke`,
        null,
        { headers: this.authHeaders(token) },
      ),

    extendTrial: (userId: string, token: string, days = 7): Observable<M.SubscriptionDto> =>
      this.http.post<M.SubscriptionDto>(
        `${this.base}/api/v1/admin/subscriptions/${userId}/extend-trial`,
        { days },
        { headers: this.authHeaders(token) },
      ),
  };

  observations = {
    getAll: (token: string, myOnly = true): Observable<M.ObservationDto[]> =>
      this.http.get<M.ObservationDto[]>(`${this.base}/api/v1/observations`, {
        headers: this.authHeaders(token),
        params: { myOnly },
      }),

    getById: (id: string, token: string): Observable<M.ObservationDto> =>
      this.http.get<M.ObservationDto>(`${this.base}/api/v1/observations/${id}`, {
        headers: this.authHeaders(token),
      }),

    delete: (id: string, token: string): Observable<void> =>
      this.http
        .delete(`${this.base}/api/v1/observations/${id}`, {
          headers: this.authHeaders(token),
          responseType: 'text',
        })
        .pipe(map(() => undefined)),

    getGeoJson: (): Observable<object> => this.http.get<object>(`${this.base}/api/v1/observations/geojson`),
  };
}

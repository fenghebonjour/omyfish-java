import { Component, effect, inject, signal, computed } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import type { BiteForecast, BiteHourlyScore } from '../../core/models';
import { FACTORS, dayWindowMean, isoDateOf, type Factor } from '../../core/bite-score.util';
import { errorMessage } from '../../core/http-error.util';
import { ActivityChart, type ChartPoint, type HourRange, type SunMark } from './activity-chart/activity-chart';
import { ActivityGauge } from './activity-gauge/activity-gauge';
import { CalendarPopover } from './calendar-popover/calendar-popover';
import { DayStrip } from './day-strip/day-strip';
import { WindowList } from './window-list/window-list';
import {
  gaugeLabel,
  formatTime,
  formatMs,
  windowsForDay,
  hourOfDay,
  toHourRange,
  safetyAlerts,
  type FactorTab as UtilFactorTab,
} from './timing-format.util';

type FactorTab = 'overall' | Factor;

const REVERSE_GEOCODE_URL = 'https://api.bigdatacloud.net/data/reverse-geocode-client';

/**
 * Angular twin of app/page.tsx (the Timing landing page) — the biggest
 * comparison point in this port, since the React original leans hard on
 * useState/useMemo/useEffect. Every `useState` below becomes a `signal()`;
 * every `useMemo` becomes a `computed()` (same "recompute only when an
 * input changed" contract, but computed()s don't need a dependency array —
 * Angular tracks which signals a computed reads automatically, where React
 * needs you to list `[coords]` etc. by hand and can go stale if you get
 * the list wrong).
 */
@Component({
  selector: 'app-timing-page',
  imports: [ActivityChart, ActivityGauge, CalendarPopover, DayStrip, WindowList],
  templateUrl: './timing-page.html',
})
export class TimingPage {
  private api = inject(ApiService);

  coords = signal<{ lat: number; lon: number } | null>(null);
  geoError = signal<string | null>(null);
  locationName = signal<string | null>(null);
  forecast = signal<BiteForecast | null>(null);
  fetchError = signal<string | null>(null);
  selectedDay = signal<string | null>(null);
  factorTab = signal<FactorTab>('overall');
  calendarOpen = signal(false);

  factorTabs: FactorTab[] = ['overall', ...FACTORS];
  gaugeLabel = (score: number | null) => gaugeLabel(this.factorTab() as UtilFactorTab, score);
  formatMs = formatMs;

  constructor() {
    // Runs once at component construction — the analog of React's
    // `useEffect(requestLocation, [])` mount-only effect.
    this.requestLocation();

    // A reactive effect: re-runs whenever any signal it reads (`coords`)
    // changes, same trigger as React's `useEffect(fn, [coords])`. The
    // `cancelled`-style race guard React needs (its effect's cleanup
    // function) is reproduced here with a token, since two coordinate
    // updates in quick succession could otherwise let a slow first fetch
    // overwrite a fast second one.
    let requestToken = 0;
    effect(() => {
      const coords = this.coords();
      if (!coords) return;
      const token = ++requestToken;
      this.forecast.set(null);
      this.fetchError.set(null);

      firstValueFrom(this.api.biteScore.forecast(coords.lat, coords.lon))
        .then((f) => {
          if (token !== requestToken) return;
          this.forecast.set(f);
          this.selectedDay.update((d) => d ?? (f.hourly[0] ? isoDateOf(f.hourly[0].timestamp) : null));
        })
        .catch((e) => {
          if (token !== requestToken) return;
          this.fetchError.set(errorMessage(e));
        });

      // A plain third-party call — not every HTTP request needs to go
      // through ApiService/HttpClient; native fetch works fine here too,
      // exactly as the React version uses it directly.
      fetch(`${REVERSE_GEOCODE_URL}?latitude=${coords.lat}&longitude=${coords.lon}&localityLanguage=en`)
        .then((r) => r.json())
        .then((g) => {
          if (token !== requestToken) return;
          this.locationName.set(g.city || g.locality || null);
        })
        .catch(() => {});
    });
  }

  requestLocation(): void {
    this.geoError.set(null);
    if (!navigator.geolocation) {
      this.geoError.set('This browser does not support geolocation.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => this.coords.set({ lat: pos.coords.latitude, lon: pos.coords.longitude }),
      () => this.geoError.set('Location access is needed to forecast fish activity for your area.'),
    );
  }

  // 14 days of data: strip shows the first 7, the calendar popover all 14.
  days = computed(() => {
    const forecast = this.forecast();
    if (!forecast) return [];
    const seen: string[] = [];
    for (const h of forecast.hourly) {
      const d = isoDateOf(h.timestamp);
      if (!seen.includes(d)) seen.push(d);
      if (seen.length === 14) break;
    }
    return seen;
  });

  stripDays = computed(() => this.days().slice(0, 7));

  hoursByDay = computed(() => {
    const map = new Map<string, BiteHourlyScore[]>();
    for (const h of this.forecast()?.hourly ?? []) {
      const d = isoDateOf(h.timestamp);
      map.set(d, [...(map.get(d) ?? []), h]);
    }
    return map;
  });

  private valueOf = computed<(h: BiteHourlyScore) => number>(() => {
    const tab = this.factorTab();
    return tab === 'overall' ? (h) => h.score : (h) => h.breakdown[tab] ?? 0;
  });

  // Daily aggregate = mean over the 4:00-20:00 window; the calendar always
  // shows the overall score regardless of the active factor tab.
  dailyScores = computed(() => {
    const map = new Map<string, number | null>();
    const hoursByDay = this.hoursByDay();
    for (const d of this.days()) map.set(d, dayWindowMean(hoursByDay.get(d) ?? [], (h) => h.score));
    return map;
  });

  dayHours = computed(() => {
    const day = this.selectedDay();
    return day ? (this.hoursByDay().get(day) ?? []) : [];
  });

  gaugeScore = computed(() => dayWindowMean(this.dayHours(), this.valueOf()));

  chartPoints = computed<ChartPoint[]>(() =>
    this.dayHours().map((h) => ({
      hour: new Date(h.timestamp).getHours(),
      time: formatTime(h.timestamp),
      value: this.valueOf()(h),
    })),
  );

  majorToday = computed(() => {
    const day = this.selectedDay();
    const forecast = this.forecast();
    return day && forecast ? windowsForDay(forecast.majorWindows, day) : [];
  });

  minorToday = computed(() => {
    const day = this.selectedDay();
    const forecast = this.forecast();
    return day && forecast ? windowsForDay(forecast.minorWindows, day) : [];
  });

  majorRanges = computed<HourRange[]>(() => {
    const day = this.selectedDay();
    return day ? this.majorToday().map((w) => toHourRange(w, day)) : [];
  });

  minorRanges = computed<HourRange[]>(() => {
    const day = this.selectedDay();
    return day ? this.minorToday().map((w) => toHourRange(w, day)) : [];
  });

  dayAlerts = computed(() => safetyAlerts(this.dayHours()));

  nowAlert = computed(() => {
    const current = this.forecast()?.current;
    if (!current || (!current.isStorm && !current.isHeavyPrecip)) return null;
    return current.isStorm
      ? 'Storm at your location right now — do not fish through lightning.'
      : 'Heavy rain at your location right now — fishing is not recommended.';
  });

  sunMarks = computed<SunMark[]>(() => {
    const day = this.selectedDay();
    const sunToday = day ? this.forecast()?.sunTimes.find((s) => s.date === day) : undefined;
    if (!sunToday || !day) return [];
    return [
      { x: hourOfDay(sunToday.sunrise, day), label: `☀ ${formatTime(sunToday.sunrise)}` },
      { x: hourOfDay(sunToday.sunset, day), label: `☀ ${formatTime(sunToday.sunset)}` },
    ].filter((m) => m.x >= 0 && m.x <= 24);
  });

  formatCoords(): string {
    const c = this.coords();
    return c ? `${c.lat.toFixed(2)}, ${c.lon.toFixed(2)}` : 'Locating…';
  }

  formatSelectedDay(): string {
    const day = this.selectedDay();
    if (!day) return '';
    return new Date(`${day}T12:00:00`).toLocaleDateString(undefined, {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
    });
  }
}

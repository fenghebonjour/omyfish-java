import { Component, effect, inject, input, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../../core/api.service';
import type { BiteForecast, BiteHourlyScore } from '../../../core/models';
import { FACTORS, barColor, scoreColor } from '../../../core/bite-score.util';
import { errorMessage } from '../../../core/http-error.util';

function windowLabel(w: BiteHourlyScore): string {
  return new Date(w.timestamp).toLocaleString(undefined, { weekday: 'short', hour: 'numeric' });
}

/**
 * Angular twin of components/BiteScorePanel.tsx. React re-fetches inside a
 * `useEffect(fn, [lat, lon, species])` any time one of those three props
 * changes. The `effect()` below reads `lat()`, `lon()`, `species()` — all
 * signal inputs — so Angular tracks that same dependency set automatically
 * by watching what the effect actually reads, instead of a hand-maintained
 * array.
 */
@Component({
  selector: 'app-bite-score-panel',
  templateUrl: './bite-score-panel.html',
})
export class BiteScorePanel {
  private api = inject(ApiService);

  lat = input.required<number>();
  lon = input.required<number>();
  species = input<string>();

  forecast = signal<BiteForecast | null>(null);
  error = signal<string | null>(null);
  factors = FACTORS;
  barColor = barColor;
  scoreColor = scoreColor;
  windowLabel = windowLabel;

  constructor() {
    let requestToken = 0;
    effect(() => {
      const lat = this.lat();
      const lon = this.lon();
      const species = this.species() ?? 'general';
      const token = ++requestToken;
      this.forecast.set(null);
      this.error.set(null);

      firstValueFrom(this.api.biteScore.today(lat, lon, species))
        .then((f) => {
          if (token === requestToken) this.forecast.set(f);
        })
        .catch((e) => {
          if (token === requestToken) this.error.set(errorMessage(e));
        });
    });
  }

  // The forecast is anchored at local midnight, so find the current hour.
  now(): BiteHourlyScore | null {
    const forecast = this.forecast();
    if (!forecast) return null;
    const currentHour = new Date();
    currentHour.setMinutes(0, 0, 0);
    return (
      forecast.hourly.find((h) => new Date(h.timestamp).getTime() === currentHour.getTime()) ??
      forecast.hourly[forecast.hourly.length - 1] ??
      null
    );
  }

  breakdownValue(now: BiteHourlyScore, factor: string): number {
    return now.breakdown[factor] ?? 0;
  }

  round(v: number): number {
    return Math.round(v);
  }
}

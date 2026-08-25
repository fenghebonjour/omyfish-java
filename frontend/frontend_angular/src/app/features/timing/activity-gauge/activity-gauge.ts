import { Component, computed, input } from '@angular/core';

const RADIUS = 62;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/**
 * Angular twin of components/timing/ActivityGauge.tsx. Pure hand-rolled SVG
 * in both versions — no charting library needed here, so the port is
 * almost mechanical. The only real difference: React receives `score` and
 * `label` as function-call props (`<ActivityGauge score={x} label={y} />`);
 * Angular declares them with `input()` (a signal-backed prop) and reads
 * them the same way a local signal is read, `score()`, in the template.
 */
@Component({
  selector: 'app-activity-gauge',
  templateUrl: './activity-gauge.html',
})
export class ActivityGauge {
  score = input.required<number | null>();
  label = input.required<string>();

  radius = RADIUS;
  circumference = CIRCUMFERENCE;

  // computed() re-derives only when `score` changes — the Angular analog of
  // inlining `const fraction = ...` in the middle of a React render (React
  // recomputes it every render; computed() caches until a dependency shifts).
  fraction = computed(() => {
    const s = this.score();
    return s === null ? 0 : Math.max(0, Math.min(100, s)) / 100;
  });

  dashArray = computed(() => `${this.fraction() * this.circumference} ${this.circumference}`);
  displayScore = computed(() => (this.score() === null ? '–' : Math.round(this.score()!)));
}

import { Component, input, output } from '@angular/core';

/**
 * Angular twin of components/timing/DayStrip.tsx. React passes a callback
 * prop (`onSelect: (day: string) => void`) and calls it directly. Angular's
 * convention is `output()` — declared like an input but emitted with
 * `.emit(value)` — and bound in the parent template with `(select)="..."`
 * instead of passing a function value through a prop.
 */
@Component({
  selector: 'app-day-strip',
  templateUrl: './day-strip.html',
})
export class DayStrip {
  days = input.required<string[]>(); // ISO dates, first is today
  dailyScores = input.required<Map<string, number | null>>();
  selected = input.required<string>();
  select = output<string>();

  dateOf(day: string): Date {
    return new Date(`${day}T12:00:00`);
  }

  scoreOf(day: string): number | null {
    return this.dailyScores().get(day) ?? null;
  }

  roundScore(day: string): string {
    const s = this.scoreOf(day);
    return s === null ? '–' : `${Math.round(s)}%`;
  }

  weekday(date: Date): string {
    return date.toLocaleDateString(undefined, { weekday: 'short' });
  }
}

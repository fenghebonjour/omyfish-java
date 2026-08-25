import { Component, input, output } from '@angular/core';

/**
 * Angular twin of components/timing/CalendarPopover.tsx. React's
 * `onSelect(day)` then `onClose()` sequence in one click handler becomes
 * two separate `.emit()` calls in `pick()` below — Angular has no
 * shorthand for "call these two output bindings in order" beyond writing
 * the method, same as React needed a small arrow function for the same
 * two-call sequence.
 */
@Component({
  selector: 'app-calendar-popover',
  templateUrl: './calendar-popover.html',
})
export class CalendarPopover {
  days = input.required<string[]>(); // ISO dates — 14-day window (the strip shows the first 7)
  dailyScores = input.required<Map<string, number | null>>();
  selected = input.required<string>();
  select = output<string>();
  close = output<void>();

  pick(day: string): void {
    this.select.emit(day);
    this.close.emit();
  }

  dateOf(day: string): Date {
    return new Date(`${day}T12:00:00`);
  }

  roundScore(day: string): string {
    const s = this.dailyScores().get(day) ?? null;
    return s === null ? '–' : `${Math.round(s)}%`;
  }
}

import type { BiteHourlyScore, TimeWindow } from '../../core/models';
import { activityBand } from '../../core/bite-score.util';
import type { HourRange } from './activity-chart/activity-chart';

// React defines these as plain functions at the top of app/page.tsx and
// reaches for them from that one file only. Angular splits the page into
// several components (TimingPage, WindowList, ...), so the same functions
// move to a shared util module both files import — the framework didn't
// change the logic, just where a multi-file version of it has to live.

export type FactorTab = 'overall' | string;

export function gaugeLabel(tab: FactorTab, score: number | null): string {
  if (score === null) return 'No data for this day';
  const band = activityBand(score);
  if (tab === 'overall') return `${band} fish activity`;
  const quality = band === 'High' ? 'Favorable' : band === 'Medium' ? 'Fair' : 'Poor';
  return `${quality} ${tab} conditions`;
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
}

export function formatWindow(w: TimeWindow): string {
  const start = formatTime(w.start);
  const end = formatTime(w.end);
  const suffix = / (AM|PM)$/;
  const sameMeridiem = start.match(suffix)?.[1] === end.match(suffix)?.[1];
  return sameMeridiem ? `${start.replace(suffix, '')}–${end}` : `${start} – ${end}`;
}

export function windowsForDay(windows: TimeWindow[], day: string): TimeWindow[] {
  const dayStart = new Date(`${day}T00:00:00`).getTime();
  const dayEnd = dayStart + 24 * 3600 * 1000;
  return windows.filter((w) => new Date(w.start).getTime() < dayEnd && new Date(w.end).getTime() > dayStart);
}

export function hourOfDay(iso: string, day: string): number {
  return (new Date(iso).getTime() - new Date(`${day}T00:00:00`).getTime()) / 3_600_000;
}

/** Map a major/minor window onto the day's 0-24 hour axis, clipped to the day. */
export function toHourRange(w: TimeWindow, day: string): HourRange {
  return {
    x1: Math.max(0, hourOfDay(w.start, day)),
    x2: Math.min(24, hourOfDay(w.end, day)),
  };
}

export interface SafetyAlert {
  message: string;
  startMs: number;
  endMs: number; // exclusive end of the last flagged hour block
}

/** Group consecutive flagged hours of a day into ranges, per flag message. */
export function safetyAlerts(hours: BiteHourlyScore[]): SafetyAlert[] {
  const alerts: SafetyAlert[] = [];
  let open: SafetyAlert | null = null;
  for (const h of hours) {
    const startMs = new Date(h.timestamp).getTime();
    if (h.safetyFlag && open && open.message === h.safetyFlag && open.endMs === startMs) {
      open.endMs = startMs + 3_600_000;
    } else {
      if (open) alerts.push(open);
      open = h.safetyFlag ? { message: h.safetyFlag, startMs, endMs: startMs + 3_600_000 } : null;
    }
  }
  if (open) alerts.push(open);
  return alerts;
}

export function formatMs(ms: number): string {
  return new Date(ms).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
}

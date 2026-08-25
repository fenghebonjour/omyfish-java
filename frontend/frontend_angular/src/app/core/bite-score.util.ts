import type { BiteHourlyScore } from './models';

// Pure functions — no framework dependency in the React version either, so
// this file ports over unchanged. This is the case for the argument that
// framework choice should only touch the presentation layer: business logic
// like this doesn't care whether it's called from a React hook or an
// Angular signal/computed.

export const FACTORS = ['pressure', 'temperature', 'wind', 'water', 'solunar', 'sky'] as const;
export type Factor = (typeof FACTORS)[number];

export const BAND_THRESHOLDS = { high: 70, medium: 40 } as const;

export type ActivityBand = 'High' | 'Medium' | 'Low';

export function activityBand(score: number): ActivityBand {
  if (score >= BAND_THRESHOLDS.high) return 'High';
  if (score >= BAND_THRESHOLDS.medium) return 'Medium';
  return 'Low';
}

export function scoreColor(score: number) {
  if (score >= BAND_THRESHOLDS.high) return 'text-green-600';
  if (score >= BAND_THRESHOLDS.medium) return 'text-yellow-600';
  return 'text-red-500';
}

export function barColor(score: number) {
  if (score >= BAND_THRESHOLDS.high) return 'bg-green-500';
  if (score >= BAND_THRESHOLDS.medium) return 'bg-yellow-400';
  return 'bg-red-400';
}

export const DAY_START_HOUR = 4;
export const DAY_END_HOUR = 20;

export function isoDateOf(timestamp: string): string {
  return timestamp.slice(0, 10);
}

export function dayWindowMean(
  hours: BiteHourlyScore[],
  getValue: (h: BiteHourlyScore) => number,
): number | null {
  const inWindow = hours.filter((h) => {
    const hr = new Date(h.timestamp).getHours();
    return hr >= DAY_START_HOUR && hr <= DAY_END_HOUR;
  });
  if (inWindow.length === 0) return null;
  return inWindow.reduce((sum, h) => sum + getValue(h), 0) / inWindow.length;
}

import { Component, ElementRef, computed, input, signal, viewChild } from '@angular/core';
import { BAND_THRESHOLDS } from '../../../core/bite-score.util';

export interface ChartPoint {
  hour: number; // 0..23
  time: string; // "4:00 AM"
  value: number; // 0..100
}

/** A major/minor peak window mapped onto the day's 0-24 hour axis. */
export interface HourRange {
  x1: number;
  x2: number;
}

export interface SunMark {
  x: number;
  label: string;
}

const WIDTH = 640;
const HEIGHT = 224;
const MARGIN = { top: 18, right: 12, bottom: 20, left: 52 };
const PLOT_W = WIDTH - MARGIN.left - MARGIN.right;
const PLOT_H = HEIGHT - MARGIN.top - MARGIN.bottom;

function formatHour(hour: number): string {
  if (hour === 0 || hour === 24) return '12 AM';
  if (hour === 12) return '12 PM';
  return hour < 12 ? `${hour} AM` : `${hour - 12} PM`;
}

/**
 * Angular twin of components/timing/ActivityChart.tsx.
 *
 * The React version leans on Recharts — <LineChart>, <ReferenceArea>,
 * <Tooltip> — a declarative charting library that owns scales, hit-testing
 * and the tooltip DOM for you. Per this port's brief (raw libs, no wrapper
 * library on either side), this version hand-rolls the same picture as
 * plain SVG: xScale()/yScale() below are exactly what Recharts computes
 * internally from `domain`/`ticks`, just made explicit; the hover tooltip
 * is a manual (mousemove) handler finding the nearest point instead of
 * Recharts' built-in hit-testing. The line itself is drawn with straight
 * segments rather than Recharts' `type="monotone"` cubic smoothing — a
 * deliberate simplification, since matching that curve algorithm by hand
 * isn't worth it for hourly (24-point) data where the visual difference is
 * negligible.
 */
@Component({
  selector: 'app-activity-chart',
  templateUrl: './activity-chart.html',
})
export class ActivityChart {
  points = input.required<ChartPoint[]>();
  majorRanges = input.required<HourRange[]>();
  minorRanges = input.required<HourRange[]>();
  sunMarks = input.required<SunMark[]>(); // sunrise/sunset — the dawn/dusk boost

  private svgRef = viewChild<ElementRef<SVGSVGElement>>('svg');
  hoverIndex = signal<number | null>(null);

  width = WIDTH;
  height = HEIGHT;
  margin = MARGIN;
  xTicks = [0, 4, 8, 12, 16, 20, 24];
  yTicks = [20, 55, 85];
  bandMedium = BAND_THRESHOLDS.medium;
  bandHigh = BAND_THRESHOLDS.high;
  formatHour = formatHour;

  xScale(hour: number): number {
    return MARGIN.left + (hour / 24) * PLOT_W;
  }

  yScale(value: number): number {
    return MARGIN.top + (1 - value / 100) * PLOT_H;
  }

  yTickLabel(v: number): string {
    return v >= 85 ? 'High' : v >= 55 ? 'Medium' : 'Low';
  }

  linePath = computed(() => {
    const pts = this.points();
    if (pts.length === 0) return '';
    return pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${this.xScale(p.hour)},${this.yScale(p.value)}`).join(' ');
  });

  hoveredPoint = computed(() => {
    const i = this.hoverIndex();
    return i === null ? null : (this.points()[i] ?? null);
  });

  // Recharts wires this up internally on <LineChart>; here it's a plain
  // (mousemove) handler that converts the pointer's screen position into
  // the chart's own SVG coordinate space, then picks the nearest hour.
  onMouseMove(event: MouseEvent): void {
    const svg = this.svgRef()?.nativeElement;
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    const fraction = (event.clientX - rect.left) / rect.width;
    const hourAtCursor = fraction * 24;

    const pts = this.points();
    let nearest = 0;
    let best = Infinity;
    pts.forEach((p, i) => {
      const d = Math.abs(p.hour - hourAtCursor);
      if (d < best) {
        best = d;
        nearest = i;
      }
    });
    this.hoverIndex.set(pts.length > 0 ? nearest : null);
  }

  onMouseLeave(): void {
    this.hoverIndex.set(null);
  }

  round(v: number): number {
    return Math.round(v);
  }
}

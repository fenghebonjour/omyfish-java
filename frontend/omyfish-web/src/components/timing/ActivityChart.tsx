"use client";

import {
  Line,
  LineChart,
  ReferenceArea,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { BAND_THRESHOLDS, DAY_END_HOUR, DAY_START_HOUR } from "@/lib/biteScore";

export interface ChartPoint {
  hour: number; // 4..20
  time: string; // "4:00 AM"
  value: number; // 0..100
}

function formatHour(hour: number) {
  if (hour === 12) return "12 PM";
  return hour < 12 ? `${hour} AM` : `${hour - 12} PM`;
}

/** Contiguous runs of points in the High band — the shaded peak windows. */
function peakRuns(points: ChartPoint[]): Array<{ x1: number; x2: number }> {
  const runs: Array<{ x1: number; x2: number }> = [];
  let start: number | null = null;
  for (const p of points) {
    if (p.value >= BAND_THRESHOLDS.high) {
      start = start ?? p.hour;
    } else if (start !== null) {
      runs.push({ x1: start, x2: p.hour - 1 });
      start = null;
    }
  }
  if (start !== null) runs.push({ x1: start, x2: points[points.length - 1].hour });
  // Widen by half an hour so single-hour peaks stay visible.
  return runs.map((r) => ({
    x1: Math.max(DAY_START_HOUR, r.x1 - 0.5),
    x2: Math.min(DAY_END_HOUR, r.x2 + 0.5),
  }));
}

export function ActivityChart({ points }: { points: ChartPoint[] }) {
  if (points.length === 0) {
    return (
      <p className="text-sm text-gray-400 py-10 text-center">
        No hours left in today&apos;s 4 AM–8 PM window.
      </p>
    );
  }

  return (
    <div className="h-56 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={points} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
          {/* Low / Medium / High horizontal bands */}
          <ReferenceArea y1={0} y2={BAND_THRESHOLDS.medium} fill="#f8fafc" fillOpacity={1} />
          <ReferenceArea y1={BAND_THRESHOLDS.medium} y2={BAND_THRESHOLDS.high} fill="#eff6ff" fillOpacity={1} />
          <ReferenceArea y1={BAND_THRESHOLDS.high} y2={100} fill="#dbeafe" fillOpacity={0.7} />
          {peakRuns(points).map((r) => (
            <ReferenceArea key={r.x1} x1={r.x1} x2={r.x2} fill="#3b82f6" fillOpacity={0.15} />
          ))}
          <XAxis
            dataKey="hour"
            type="number"
            domain={[DAY_START_HOUR, DAY_END_HOUR]}
            ticks={[4, 8, 12, 16, 20]}
            tickFormatter={formatHour}
            tickLine={false}
            axisLine={{ stroke: "#e5e7eb" }}
            tick={{ fontSize: 11, fill: "#9ca3af" }}
          />
          <YAxis
            domain={[0, 100]}
            ticks={[20, 55, 85]}
            tickFormatter={(v: number) => (v >= 85 ? "High" : v >= 55 ? "Medium" : "Low")}
            tickLine={false}
            axisLine={false}
            width={52}
            tick={{ fontSize: 11, fill: "#9ca3af" }}
          />
          <Tooltip
            cursor={{ stroke: "#2563eb", strokeWidth: 1, strokeDasharray: "4 2" }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const p = payload[0].payload as ChartPoint;
              return (
                <div className="bg-white border border-blue-200 rounded-lg shadow-sm px-3 py-1.5 text-xs">
                  <span className="font-medium text-gray-700">{p.time}</span>
                  <span className="text-blue-600 font-semibold ml-2">{Math.round(p.value)}</span>
                </div>
              );
            }}
          />
          <Line
            type="monotone"
            dataKey="value"
            stroke="#2563eb"
            strokeWidth={2.5}
            dot={false}
            activeDot={{ r: 5, fill: "#2563eb", stroke: "#fff", strokeWidth: 2 }}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

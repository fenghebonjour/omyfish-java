"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getObservations, type Observation } from "@/lib/api";
import { isLoggedIn } from "@/lib/auth";
import { ObservationMap } from "@/components/ObservationMap";

export default function ObservationsPage() {
  const router = useRouter();
  const [observations, setObservations] = useState<Observation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isLoggedIn()) {
      router.replace("/login");
      return;
    }
    getObservations()
      .then(setObservations)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [router]);

  if (loading) return <LoadingState />;

  return (
    <main className="min-h-screen bg-gray-50 py-10">
      <div className="max-w-4xl mx-auto px-4 flex flex-col gap-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">My Observations</h1>
          <p className="text-sm text-gray-500 mt-1">{observations.length} total</p>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">{error}</div>
        )}

        {observations.length > 0 && <ObservationMap observations={observations} />}

        {observations.length === 0 && !error ? (
          <div className="text-center py-20 text-gray-400">
            <p className="text-lg">No observations yet</p>
            <p className="text-sm mt-1">Identify a fish on the home page and save it</p>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {observations.map((obs) => (
              <ObservationCard key={obs.id} obs={obs} />
            ))}
          </div>
        )}
      </div>
    </main>
  );
}

function ObservationCard({ obs }: { obs: Observation }) {
  const pct = Math.round(obs.topConfidence * 100);
  const barColor = pct >= 85 ? "bg-green-500" : pct >= 50 ? "bg-yellow-400" : "bg-red-400";

  return (
    <div className="bg-white border border-gray-200 rounded-xl p-4 flex flex-col gap-2 shadow-sm">
      <div className="flex justify-between items-start">
        <div>
          <p className="font-semibold text-gray-900">{obs.speciesName}</p>
          <p className="text-sm text-gray-500 italic">{obs.scientificName}</p>
        </div>
        <div className="text-right">
          <span className="text-sm font-medium text-gray-700">{pct}%</span>
          <p className="text-xs text-gray-400">{new Date(obs.observedAt).toLocaleDateString()}</p>
        </div>
      </div>
      <div className="w-full bg-gray-100 rounded-full h-1.5">
        <div className={`h-1.5 rounded-full ${barColor}`} style={{ width: `${pct}%` }} />
      </div>
      {obs.latitude != null && obs.longitude != null && (
        <a
          href={`https://www.openstreetmap.org/?mlat=${obs.latitude}&mlon=${obs.longitude}&zoom=12`}
          target="_blank"
          rel="noopener noreferrer"
          className="text-xs text-blue-500 hover:underline"
        >
          {obs.latitude.toFixed(4)}, {obs.longitude.toFixed(4)}
        </a>
      )}
    </div>
  );
}

function LoadingState() {
  return (
    <main className="min-h-screen bg-gray-50 flex items-center justify-center">
      <p className="text-gray-400 animate-pulse">Loading observations...</p>
    </main>
  );
}

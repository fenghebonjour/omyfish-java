import { getToken } from "./auth";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

function authHeaders(): HeadersInit {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function checkOk(res: Response) {
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res;
}

// ── Auth ──────────────────────────────────────────────────────────────────────

export async function register(email: string, password: string) {
  const res = await fetch(`${API_URL}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  await checkOk(res);
  return res.json() as Promise<{ userId: string; email: string }>;
}

export async function login(email: string, password: string) {
  const res = await fetch(`${API_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  await checkOk(res);
  return res.json() as Promise<{ token: string; userId: string; email: string; role: string }>;
}

// ── Species identification ────────────────────────────────────────────────────

export interface PredictionResult {
  speciesName: string;
  scientificName: string;
  confidence: number;
  rank: number;
  conservationStatus?: string;
}

export interface IdentificationResponse {
  predictions: PredictionResult[];
  uncertain: boolean;
  imageKey: string;
}

export async function identifyFish(image: File, topK = 5): Promise<IdentificationResponse> {
  const form = new FormData();
  form.append("image", image);
  form.append("topK", String(topK));
  const res = await fetch(`${API_URL}/api/v1/species/identify`, { method: "POST", body: form });
  await checkOk(res);
  return res.json();
}

// ── Observations ──────────────────────────────────────────────────────────────

export interface Observation {
  id: string;
  userId: string;
  speciesName: string;
  scientificName: string;
  topConfidence: number;
  imageStorageKey: string;
  latitude: number | null;
  longitude: number | null;
  notes: string | null;
  observedAt: string;
  createdAt: string;
}

export async function getObservations(): Promise<Observation[]> {
  const res = await fetch(`${API_URL}/api/v1/observations`, {
    headers: authHeaders(),
  });
  await checkOk(res);
  return res.json();
}

export async function createObservation(body: {
  speciesName: string;
  scientificName: string;
  topConfidence: number;
  imageStorageKey: string;
  latitude?: number | null;
  longitude?: number | null;
  notes?: string | null;
}): Promise<Observation> {
  const res = await fetch(`${API_URL}/api/v1/observations`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(body),
  });
  await checkOk(res);
  return res.json();
}

// ── Notifications ─────────────────────────────────────────────────────────────

export interface Notification {
  id: string;
  userId: string;
  type: string;
  title: string;
  body: string | null;
  read: boolean;
  createdAt: string;
}

export async function getNotifications(): Promise<Notification[]> {
  const res = await fetch(`${API_URL}/api/v1/notifications`, {
    headers: authHeaders(),
  });
  await checkOk(res);
  return res.json();
}

export async function markNotificationRead(id: string): Promise<void> {
  const res = await fetch(`${API_URL}/api/v1/notifications/${id}/read`, {
    method: "PUT",
    headers: authHeaders(),
  });
  await checkOk(res);
}

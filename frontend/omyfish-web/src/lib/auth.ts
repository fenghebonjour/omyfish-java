const TOKEN_KEY = "omyfish_token";

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

interface JwtPayload {
  sub: string;
  email: string;
  role: string;
  exp: number;
}

function parseJwt(token: string): JwtPayload | null {
  try {
    const [, payload] = token.split(".");
    return JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
  } catch {
    return null;
  }
}

export function isLoggedIn(): boolean {
  const token = getToken();
  if (!token) return false;
  const payload = parseJwt(token);
  return !!payload && payload.exp * 1000 > Date.now();
}

export function getUserId(): string | null {
  return parseJwt(getToken() ?? "")?.sub ?? null;
}

export function getUserEmail(): string | null {
  return parseJwt(getToken() ?? "")?.email ?? null;
}

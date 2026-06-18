const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export async function identifyFish(image: File, topK = 5) {
  const form = new FormData();
  form.append("image", image);
  form.append("topK", String(topK));
  const res = await fetch(`${API_URL}/api/v1/species/identify`, { method: "POST", body: form });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

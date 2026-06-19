# CORS — What It Is and How It Works

## The nightclub analogy

Your browser is a driver dropping off passengers (HTTP requests). A server is a nightclub.

- **Same origin** — the passenger lives in the club's own building. The bouncer waves them through instantly.
- **Cross-origin** — the passenger comes from a different address. The bouncer stops and calls the club owner: *"Someone from `localhost:3000` wants in — is that allowed?"*

That phone call is the **OPTIONS preflight request**. The browser sends it automatically before the real request.

The club owner posts a sign on the door with the rules:

```
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
```

If the sign says the origin is allowed, the bouncer lets the request through. If the sign is missing or says no, the browser blocks the request — and you see **"Failed to fetch"** or a CORS error in the console.

---

## What "origin" means

Origin = **scheme + host + port**. All three must match to be "same origin."

| URL A | URL B | Same origin? |
|---|---|---|
| `http://localhost:3000` | `http://localhost:3000` | Yes |
| `http://localhost:3000` | `http://localhost:8080` | No — different port |
| `http://localhost:3000` | `https://localhost:3000` | No — different scheme |
| `http://localhost:3000` | `http://example.com:3000` | No — different host |

---

## The request lifecycle

```
Browser                          API Gateway
   |                                  |
   |-- OPTIONS /api/v1/species/... -->|   ← preflight: "may I?"
   |<-- 200 Access-Control-Allow-* --|   ← gateway says yes
   |                                  |
   |-- POST /api/v1/species/... ----->|   ← real request
   |<-- 200 { predictions: [...] } --|
```

If the preflight returns no CORS headers (or 403), the browser **never sends the POST**.

---

## Why curl works but the browser doesn't

`curl` is not a browser — it skips the preflight entirely. CORS is enforced **only by the browser** to protect users. Server-to-server calls and CLI tools are not affected.

---

## How it's configured in this project

**`services/api-gateway/src/main/resources/application.yml`**

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:3000"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders:
              - "*"
            allowCredentials: false
```

The gateway handles the preflight for all downstream services — individual services don't need their own CORS config.

For production, replace `http://localhost:3000` with your real frontend domain.

---

## Quick checklist when you see a CORS error

1. Check the browser console for the exact blocked origin.
2. Confirm the server is returning `Access-Control-Allow-Origin` on OPTIONS requests (`curl -I -X OPTIONS <url> -H "Origin: <frontend-url>"`).
3. If missing, add CORS config to the gateway (or service, if no gateway).
4. Remember: changing CORS config requires a redeploy — the browser cache doesn't matter here, the server response does.

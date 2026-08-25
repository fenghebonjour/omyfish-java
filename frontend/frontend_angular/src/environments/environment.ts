// Angular's build-time config mechanism — the equivalent of Next.js reading
// `process.env.NEXT_PUBLIC_API_URL` at build time (see lib/api.ts in the
// React twin). Angular has no env-var convention baked into the bundler;
// this plain exported object, swapped via angular.json fileReplacements
// per configuration, is the idiomatic stand-in.
export const environment = {
  apiBase: 'http://localhost:8080',
};

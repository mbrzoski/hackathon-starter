# frontend

Angular SPA (standalone, signals, SCSS, Angular Material + CDK), no SSR (WEB-01). The same code base is later wrapped in Capacitor for Android (FE-01).

```bash
npm install
npm start        # generate:api, then all three apps (below), each with proxy.conf.json (/api and /ws to :8080)
npm run start:senior | start:listen | start:family   # one app only
npm test         # generate:api, then Vitest
npm run build    # generate:api, then dist/senior, dist/listen, dist/family
```

The scripts are plain Node (`scripts/*.mjs`), so they work the same in PowerShell or cmd on Windows and in a macOS terminal; no bash, `make` or `npx` is needed. `generate:api` needs Java on the `PATH` (the OpenAPI generator is a Java tool). `npm start` stops all three servers when one of them exits (e.g. its port is taken).

Three apps, three devices, one code base (FE-01). Each build swaps `src/app/target/app-target.ts` (fileReplacements in `angular.json`), so a bundle contains only its own screens:

| App | Device | Dev port | Routes | Build |
|---|---|---|---|---|
| senior | senior's phone/tablet (later Capacitor) | 4201 | `/senior` | `dist/senior` |
| listen | tablet next to the landline | 4202 | `/listen` | `dist/listen` |
| family | browser of family, jury, team | 4203 | `/family`, `/setup`, `/audit` | `dist/family` |

- `npm run generate:api` generates `src/app/api/` from `../contracts/openapi.yaml` (typescript-angular) and compiles the `EventEnvelope` validator with Ajv into `src/app/api/event-envelope.validator.js` (standalone code, so the browser needs no `eval`: CSP of WEB-05). Git-ignored: never edit it by hand (FE-02). Models come from `components.schemas`, HTTP services from `paths` (`api/api/alerts.service.ts`, `api/api/demo.service.ts`). Import from `api/model/models`, `api/api/*` and `api/provide-api`.
- `core/events.service.ts` is the only client of `/ws/events`. The role follows the route (`/senior`, `/listen` → senior, `/family`, `/setup` → family, `/audit` → audit); every message is validated against `EventEnvelope` with the compiled validator (FE-13).
- In each app `''` and unknown paths redirect to its home route.
- To see events without audio, run the backend with the `dev` profile and POST an `EventEnvelope` to `/api/dev/emit`.
- Demo behind HTTPS: `deploy/README.md`. The production build does not inline critical CSS (`angular.json`), because that adds an inline script the CSP blocks.
- `npm run e2e` runs UC-01 (docs/use-cases.md) in the installed Chrome against the dev servers, `npm run e2e:demo` against `https://localhost` (deploy). No Playwright download: `e2e/cdp.mjs` drives Chrome over the DevTools protocol (`CHROME=/path` if it is not found). After `git pull`, restart `npm start`: `generate:api` rewrites `src/app/api` and the running dev servers keep serving the old code.

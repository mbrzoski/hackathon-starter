# frontend

Angular SPA (standalone, signals, SCSS, Angular Material + CDK), no SSR (WEB-01). The same code base is later wrapped in Capacitor for Android (FE-01).

```bash
npm install
npm start        # generate:api, then all three apps (below), each with proxy.conf.json (/api and /ws to :8080)
npm run start:senior | start:listen | start:family   # one app only
npm test         # generate:api, then Vitest
npm run build    # generate:api, then dist/senior, dist/listen, dist/family
```

Three apps, three devices, one code base (FE-01). Each build swaps `src/app/target/app-target.ts` (fileReplacements in `angular.json`), so a bundle contains only its own screens:

| App | Device | Dev port | Routes | Build |
|---|---|---|---|---|
| senior | senior's phone/tablet (later Capacitor) | 4201 | `/senior` | `dist/senior` |
| listen | tablet next to the landline | 4202 | `/listen` | `dist/listen` |
| family | browser of family, jury, team | 4203 | `/family`, `/setup`, `/audit` | `dist/family` |

- `npm run generate:api` generates `src/app/api/` from `../contracts/openapi.yaml` (typescript-angular) and copies the contract schemas to `public/assets/contracts/openapi-schemas.json`. Both are git-ignored: never edit them by hand (FE-02). The contract has no `paths` yet, so only models are generated; HTTP services appear once paths are added. Import from `api/model/models` and `api/provide-api` (the generated `index.ts` re-exports a missing `api/api` until then).
- `core/events.service.ts` is the only client of `/ws/events`. The role follows the route (`/senior`, `/listen` → senior, `/family`, `/setup` → family, `/audit` → audit); every message is validated with Ajv against `EventEnvelope` (FE-13).
- In each app `''` and unknown paths redirect to its home route.
- To see events without audio, run the backend with the `dev` profile and POST an `EventEnvelope` to `/api/dev/emit`.
- Design references: `references/*.pdf`.

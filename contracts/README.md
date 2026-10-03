# contracts

`openapi.yaml` is an OpenAPI 3.1 document (JSON Schema 2020-12 dialect) with every contract object under `components.schemas`. It has no `paths`. It is the single source of truth for backend (Java), frontend (Angular) and Android. `StageHits` is snake_case on purpose (Claude response); everything else is camelCase.

Java records in `backend/.../contracts/` are written by hand to match it. When you change a schema, update the matching record, and the TS types in the frontend, in the same commit.

Lint: `npx @redocly/cli lint contracts/openapi.yaml` (warnings about unused components are expected, there are no paths).

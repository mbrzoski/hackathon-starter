// Generates src/app/api/ from ../contracts/openapi.yaml (FE-02): the typescript-angular models and services, plus the
// EventEnvelope validator for /ws/events (FE-13), compiled here with Ajv so the browser never runs eval or
// new Function: the CSP of WEB-05 (script-src 'self') would block that. Generated: never edit by hand.
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';

const require = createRequire(import.meta.url);
const Ajv2020 = require('ajv/dist/2020').default;
const addFormats = require('ajv-formats').default;
const standaloneCode = require('ajv/dist/standalone').default;

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const spec = resolve(root, '../contracts/openapi.yaml');
const apiDir = resolve(root, 'src/app/api');

rmSync(apiDir, { recursive: true, force: true });
// Runs the CLI with this node, not through npx: Windows cannot spawn npx(.cmd) without a shell (FF-06).
execFileSync(
  process.execPath,
  [
    resolve(root, 'node_modules/@openapitools/openapi-generator-cli/main.js'),
    'generate',
    '-g',
    'typescript-angular',
    // Relative to cwd: the CLI hands these to java through a shell unquoted, so a path with a space would split.
    '-i',
    relative(root, spec),
    '-o',
    relative(root, apiDir),
    '--additional-properties=fileNaming=kebab-case,stringEnums=true,enumPropertyNaming=original',
  ],
  { cwd: root, stdio: 'inherit' },
);

const doc = parse(readFileSync(spec, 'utf8'));
const ajv = new Ajv2020({ allErrors: true, code: { source: true, esm: true } });
addFormats(ajv);
// The contract keeps schemas under the OpenAPI "components" container, which is not a JSON Schema keyword.
ajv.addKeyword('components');
// $refs in the contract are "#/components/schemas/<Name>", so keep that shape under one $id.
ajv.addSchema({ $id: 'aniol-stroz-contract', components: { schemas: doc.components.schemas } });
const validate = ajv.getSchema('aniol-stroz-contract#/components/schemas/EventEnvelope');
// Ajv's ESM output still calls require() for its runtime helpers: turn those into imports for the bundler.
const imports = new Map();
const code = standaloneCode(ajv, validate).replace(/require\(("[^"]+")\)/g, (_, spec) => {
  if (!imports.has(spec)) imports.set(spec, `__dep${imports.size}`);
  return imports.get(spec);
});
const header = [...imports].map(([spec, name]) => `import * as ${name} from ${spec};`).join('\n');
writeFileSync(resolve(apiDir, 'event-envelope.validator.js'), `${header}\n${code}\n`);
writeFileSync(
  resolve(apiDir, 'event-envelope.validator.d.ts'),
  `export interface ValidateFunction {\n  (data: unknown): boolean;\n  errors?: unknown[] | null;\n}\n` +
    `export declare const validate: ValidateFunction;\nexport default validate;\n`,
);
console.log('EventEnvelope validator compiled to src/app/api/event-envelope.validator.js');

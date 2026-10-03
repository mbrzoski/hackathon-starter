// Generates src/app/api/ from ../contracts/openapi.yaml (FE-02) and copies the contract schemas
// to public/assets/contracts/ so EventsService can validate /ws/events messages at runtime (FE-13).
// Both outputs are generated: never edit them by hand.
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const spec = resolve(root, '../contracts/openapi.yaml');
const apiDir = resolve(root, 'src/app/api');
const schemaFile = resolve(root, 'public/assets/contracts/openapi-schemas.json');

rmSync(apiDir, { recursive: true, force: true });
execFileSync(
  'npx',
  [
    'openapi-generator-cli',
    'generate',
    '-g',
    'typescript-angular',
    '-i',
    spec,
    '-o',
    apiDir,
    '--additional-properties=fileNaming=kebab-case,stringEnums=true,enumPropertyNaming=original',
  ],
  { cwd: root, stdio: 'inherit' },
);

const doc = parse(readFileSync(spec, 'utf8'));
mkdirSync(dirname(schemaFile), { recursive: true });
// $refs in the contract are "#/components/schemas/<Name>", so keep that shape under one $id.
writeFileSync(
  schemaFile,
  JSON.stringify({ $id: 'aniol-stroz-contract', components: { schemas: doc.components.schemas } }, null, 2),
);
console.log(`Contract schemas copied to ${schemaFile}`);

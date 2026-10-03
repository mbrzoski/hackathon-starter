// Generates src/app/api/ from ../contracts/openapi.yaml (FE-02) and copies the contract schemas
// to public/assets/contracts/ so EventsService can validate /ws/events messages at runtime (FE-13).
// Both outputs are generated: never edit them by hand.
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const spec = resolve(root, '../contracts/openapi.yaml');
const apiDir = resolve(root, 'src/app/api');
const schemaFile = resolve(root, 'public/assets/contracts/openapi-schemas.json');

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
mkdirSync(dirname(schemaFile), { recursive: true });
// $refs in the contract are "#/components/schemas/<Name>", so keep that shape under one $id.
writeFileSync(
  schemaFile,
  JSON.stringify({ $id: 'aniol-stroz-contract', components: { schemas: doc.components.schemas } }, null, 2),
);
console.log(`Contract schemas copied to ${schemaFile}`);

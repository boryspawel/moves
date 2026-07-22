import { readFile, writeFile } from 'node:fs/promises';

const runtimePath = new URL('../src/app/api/generated/src/runtime.ts', import.meta.url);
const source = await readFile(runtimePath, 'utf8');
const patched = source.replace("constructor(public cause: Error, msg?: string)", "constructor(public override cause: Error, msg?: string)");

if (patched === source) {
  throw new Error('OpenAPI runtime no longer contains the expected FetchError cause declaration.');
}

await writeFile(runtimePath, patched);

import { readFile, writeFile } from 'node:fs/promises';

const runtimePath = new URL('../src/app/api/generated/src/runtime.ts', import.meta.url);
const source = await readFile(runtimePath, 'utf8');
const patched = source.replace("constructor(public cause: Error, msg?: string)", "constructor(public override cause: Error, msg?: string)");

if (patched === source) {
  throw new Error('OpenAPI runtime no longer contains the expected FetchError cause declaration.');
}

await writeFile(runtimePath, patched);

// Springdoc represents the sealed Java hierarchy as allOf in addition to its oneOf
// discriminator. The TypeScript-fetch generator then emits Dose & StrengthDose (and
// equivalents), a recursive alias rejected by strict TypeScript. The discriminator
// wrapper in Dose.ts owns `type`; concrete schemas must remain leaf shapes.
for (const name of ['StrengthDose', 'IsometricDose', 'MobilityDose', 'StretchDose', 'BreathingDose', 'AerobicDose']) {
  const path = new URL(`../src/app/api/generated/src/models/${name}.ts`, import.meta.url);
  const model = await readFile(path, 'utf8');
  const leaf = model
    .replace("import type { Dose } from './Dose';\n", '')
    .replace(/import \{ DoseFromJSON, DoseFromJSONTyped, DoseToJSON, DoseToJSONTyped \} from '\.\/Dose';\n/, '')
    .replace(`export type ${name} = Dose & {`, `export type ${name} = {`)
    .replace('    ...DoseFromJSONTyped(json, true),\n', '')
    .replace('    ...DoseToJSONTyped(value, true),\n', '');
  if (leaf === model) throw new Error(`OpenAPI ${name} shape no longer matches the expected allOf output.`);
  await writeFile(path, leaf);
}

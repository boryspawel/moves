# PrescriptionView

## Properties

| Name                    | Type   |
| ----------------------- | ------ |
| `id`                    | string |
| `exerciseVersionId`     | string |
| `position`              | number |
| `targetSets`            | number |
| `targetRepetitions`     | number |
| `targetDurationSeconds` | number |
| `targetLoadKg`          | number |
| `notes`                 | string |

## Example

```typescript
import type { PrescriptionView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  exerciseVersionId: null,
  position: null,
  targetSets: null,
  targetRepetitions: null,
  targetDurationSeconds: null,
  targetLoadKg: null,
  notes: null,
} satisfies PrescriptionView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PrescriptionView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

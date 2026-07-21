# CorrectionCommand

## Properties

| Name                         | Type    |
| ---------------------------- | ------- |
| `reason`                     | string  |
| `correctedPainLevel`         | number  |
| `correctedDifficultyLevel`   | number  |
| `exercisePrescriptionId`     | string  |
| `correctedSets`              | number  |
| `correctedRepetitions`       | number  |
| `correctedDurationSeconds`   | number  |
| `correctedContacts`          | number  |
| `correctedExternalLoadValue` | number  |
| `correctedExternalLoadUnit`  | string  |
| `correctedSide`              | string  |
| `correctedModified`          | boolean |
| `correctedSkipped`           | boolean |
| `observationMode`            | string  |

## Example

```typescript
import type { CorrectionCommand } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  reason: null,
  correctedPainLevel: null,
  correctedDifficultyLevel: null,
  exercisePrescriptionId: null,
  correctedSets: null,
  correctedRepetitions: null,
  correctedDurationSeconds: null,
  correctedContacts: null,
  correctedExternalLoadValue: null,
  correctedExternalLoadUnit: null,
  correctedSide: null,
  correctedModified: null,
  correctedSkipped: null,
  observationMode: null,
} satisfies CorrectionCommand;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CorrectionCommand;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

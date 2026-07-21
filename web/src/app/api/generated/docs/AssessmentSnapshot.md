# AssessmentSnapshot

## Properties

| Name                     | Type                                             |
| ------------------------ | ------------------------------------------------ |
| `id`                     | string                                           |
| `participantAccountId`   | string                                           |
| `revisionId`             | string                                           |
| `loadSnapshotId`         | string                                           |
| `loadCalculationVersion` | string                                           |
| `rulesetCode`            | string                                           |
| `rulesetVersion`         | number                                           |
| `recordedResult`         | string                                           |
| `effectiveResult`        | string                                           |
| `assessedAt`             | Date                                             |
| `factors`                | [Array&lt;FactorSnapshot&gt;](FactorSnapshot.md) |

## Example

```typescript
import type { AssessmentSnapshot } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  participantAccountId: null,
  revisionId: null,
  loadSnapshotId: null,
  loadCalculationVersion: null,
  rulesetCode: null,
  rulesetVersion: null,
  recordedResult: null,
  effectiveResult: null,
  assessedAt: null,
  factors: null,
} satisfies AssessmentSnapshot;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AssessmentSnapshot;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

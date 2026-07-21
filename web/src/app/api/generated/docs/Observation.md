# Observation

## Properties

| Name                | Type   |
| ------------------- | ------ |
| `prescriptionId`    | string |
| `exerciseVersionId` | string |
| `contributionId`    | string |
| `sessionId`         | string |
| `microcycleId`      | string |
| `cycleId`           | string |
| `structureId`       | string |
| `side`              | string |
| `channel`           | string |
| `observationFamily` | string |
| `unit`              | string |
| `low`               | number |
| `high`              | number |
| `confidence`        | string |
| `evidenceGrade`     | string |
| `doseSource`        | string |
| `observationMode`   | string |

## Example

```typescript
import type { Observation } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  prescriptionId: null,
  exerciseVersionId: null,
  contributionId: null,
  sessionId: null,
  microcycleId: null,
  cycleId: null,
  structureId: null,
  side: null,
  channel: null,
  observationFamily: null,
  unit: null,
  low: null,
  high: null,
  confidence: null,
  evidenceGrade: null,
  doseSource: null,
  observationMode: null,
} satisfies Observation;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Observation;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

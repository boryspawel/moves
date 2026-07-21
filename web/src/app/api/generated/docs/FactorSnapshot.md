# FactorSnapshot

## Properties

| Name                 | Type    |
| -------------------- | ------- |
| `id`                 | string  |
| `result`             | string  |
| `ruleCode`           | string  |
| `targetRef`          | string  |
| `channel`            | string  |
| `observedLow`        | number  |
| `observedHigh`       | number  |
| `thresholdLow`       | number  |
| `thresholdHigh`      | number  |
| `unit`               | string  |
| `explanationCode`    | string  |
| `evidenceGrade`      | string  |
| `overridable`        | boolean |
| `activelyOverridden` | boolean |

## Example

```typescript
import type { FactorSnapshot } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  result: null,
  ruleCode: null,
  targetRef: null,
  channel: null,
  observedLow: null,
  observedHigh: null,
  thresholdLow: null,
  thresholdHigh: null,
  unit: null,
  explanationCode: null,
  evidenceGrade: null,
  overridable: null,
  activelyOverridden: null,
} satisfies FactorSnapshot;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FactorSnapshot;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

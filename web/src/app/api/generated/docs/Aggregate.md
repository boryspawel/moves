# Aggregate

## Properties

| Name                | Type   |
| ------------------- | ------ |
| `scope`             | string |
| `scopeKey`          | string |
| `structureId`       | string |
| `side`              | string |
| `channel`           | string |
| `observationFamily` | string |
| `unit`              | string |
| `low`               | number |
| `high`              | number |

## Example

```typescript
import type { Aggregate } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  scope: null,
  scopeKey: null,
  structureId: null,
  side: null,
  channel: null,
  observationFamily: null,
  unit: null,
  low: null,
  high: null,
} satisfies Aggregate;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Aggregate;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

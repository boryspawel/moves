# Microcycle

## Properties

| Name             | Type   |
| ---------------- | ------ |
| `id`             | string |
| `cycleId`        | string |
| `sequenceNumber` | number |
| `name`           | string |

## Example

```typescript
import type { Microcycle } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  cycleId: null,
  sequenceNumber: null,
  name: null,
} satisfies Microcycle;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Microcycle;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

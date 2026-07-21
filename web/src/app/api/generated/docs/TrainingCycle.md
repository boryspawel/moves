# TrainingCycle

## Properties

| Name             | Type   |
| ---------------- | ------ |
| `id`             | string |
| `planId`         | string |
| `sequenceNumber` | number |
| `name`           | string |

## Example

```typescript
import type { TrainingCycle } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  planId: null,
  sequenceNumber: null,
  name: null,
} satisfies TrainingCycle;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TrainingCycle;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

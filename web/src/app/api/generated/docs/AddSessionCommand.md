# AddSessionCommand

## Properties

| Name                      | Type   |
| ------------------------- | ------ |
| `expectedVersion`         | number |
| `microcycleId`            | string |
| `title`                   | string |
| `scheduledDate`           | Date   |
| `availableFrom`           | Date   |
| `availableTo`             | Date   |
| `expectedDurationMinutes` | number |

## Example

```typescript
import type { AddSessionCommand } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  expectedVersion: null,
  microcycleId: null,
  title: null,
  scheduledDate: null,
  availableFrom: null,
  availableTo: null,
  expectedDurationMinutes: null,
} satisfies AddSessionCommand;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AddSessionCommand;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

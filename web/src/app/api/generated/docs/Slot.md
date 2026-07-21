# Slot

## Properties

| Name        | Type   |
| ----------- | ------ |
| `dayOfWeek` | string |
| `startTime` | string |
| `endTime`   | string |
| `timeZone`  | string |

## Example

```typescript
import type { Slot } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  dayOfWeek: null,
  startTime: null,
  endTime: null,
  timeZone: null,
} satisfies Slot;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Slot;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

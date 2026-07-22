# RecoveryChoiceCommand

## Properties

| Name               | Type   |
| ------------------ | ------ |
| `offerId`          | string |
| `aggregateVersion` | number |
| `path`             | string |

## Example

```typescript
import type { RecoveryChoiceCommand } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  offerId: null,
  aggregateVersion: null,
  path: null,
} satisfies RecoveryChoiceCommand;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RecoveryChoiceCommand;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

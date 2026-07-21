# ActivationOutcome

## Properties

| Name                   | Type    |
| ---------------------- | ------- |
| `revisionId`           | string  |
| `planId`               | string  |
| `supersededRevisionId` | string  |
| `repeated`             | boolean |
| `activatedAt`          | Date    |

## Example

```typescript
import type { ActivationOutcome } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  revisionId: null,
  planId: null,
  supersededRevisionId: null,
  repeated: null,
  activatedAt: null,
} satisfies ActivationOutcome;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ActivationOutcome;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

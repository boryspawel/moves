# AttemptView

## Properties

| Name                  | Type   |
| --------------------- | ------ |
| `attemptId`           | string |
| `plannedSessionId`    | string |
| `planRevisionId`      | string |
| `selectedVariantType` | string |
| `state`               | string |
| `lastActivityAt`      | Date   |
| `updatedAt`           | Date   |

## Example

```typescript
import type { AttemptView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  attemptId: null,
  plannedSessionId: null,
  planRevisionId: null,
  selectedVariantType: null,
  state: null,
  lastActivityAt: null,
  updatedAt: null,
} satisfies AttemptView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AttemptView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

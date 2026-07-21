# ReviewView

## Properties

| Name                | Type   |
| ------------------- | ------ |
| `id`                | string |
| `revisionId`        | string |
| `reviewerAccountId` | string |
| `status`            | string |
| `requestReference`  | string |
| `decisionReference` | string |
| `requestedAt`       | Date   |
| `decidedAt`         | Date   |

## Example

```typescript
import type { ReviewView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  revisionId: null,
  reviewerAccountId: null,
  status: null,
  requestReference: null,
  decisionReference: null,
  requestedAt: null,
  decidedAt: null,
} satisfies ReviewView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ReviewView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

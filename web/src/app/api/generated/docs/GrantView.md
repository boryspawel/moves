# GrantView

## Properties

| Name            | Type              |
| --------------- | ----------------- |
| `id`            | string            |
| `participantId` | string            |
| `recipientId`   | string            |
| `purpose`       | string            |
| `scopes`        | Set&lt;string&gt; |
| `status`        | string            |
| `validFrom`     | Date              |
| `validTo`       | Date              |

## Example

```typescript
import type { GrantView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  participantId: null,
  recipientId: null,
  purpose: null,
  scopes: null,
  status: null,
  validFrom: null,
  validTo: null,
} satisfies GrantView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as GrantView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

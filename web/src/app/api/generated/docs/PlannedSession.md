# PlannedSession

## Properties

| Name                   | Type   |
| ---------------------- | ------ |
| `id`                   | string |
| `microcycleId`         | string |
| `participantAccountId` | string |
| `title`                | string |
| `kind`                 | string |
| `status`               | string |
| `assignedAt`           | Date   |

## Example

```typescript
import type { PlannedSession } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  microcycleId: null,
  participantAccountId: null,
  title: null,
  kind: null,
  status: null,
  assignedAt: null,
} satisfies PlannedSession;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PlannedSession;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

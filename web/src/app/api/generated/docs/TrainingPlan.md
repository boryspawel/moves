# TrainingPlan

## Properties

| Name                   | Type   |
| ---------------------- | ------ |
| `id`                   | string |
| `goalId`               | string |
| `participantAccountId` | string |
| `createdByAccountId`   | string |
| `name`                 | string |
| `mode`                 | string |
| `status`               | string |
| `createdAt`            | Date   |

## Example

```typescript
import type { TrainingPlan } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  goalId: null,
  participantAccountId: null,
  createdByAccountId: null,
  name: null,
  mode: null,
  status: null,
  createdAt: null,
} satisfies TrainingPlan;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TrainingPlan;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

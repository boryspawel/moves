# GoalSnapshot

## Properties

| Name          | Type                                                       |
| ------------- | ---------------------------------------------------------- |
| `id`          | string                                                     |
| `perspective` | string                                                     |
| `category`    | string                                                     |
| `title`       | string                                                     |
| `priority`    | number                                                     |
| `status`      | string                                                     |
| `targetDate`  | Date                                                       |
| `outcomes`    | [Array&lt;GoalOutcomeSnapshot&gt;](GoalOutcomeSnapshot.md) |

## Example

```typescript
import type { GoalSnapshot } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  perspective: null,
  category: null,
  title: null,
  priority: null,
  status: null,
  targetDate: null,
  outcomes: null,
} satisfies GoalSnapshot;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as GoalSnapshot;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

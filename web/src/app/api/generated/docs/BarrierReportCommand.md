# BarrierReportCommand

## Properties

| Name               | Type   |
| ------------------ | ------ |
| `plannedSessionId` | string |
| `sessionAttemptId` | string |
| `category`         | string |
| `selectedAction`   | string |

## Example

```typescript
import type { BarrierReportCommand } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  plannedSessionId: null,
  sessionAttemptId: null,
  category: null,
  selectedAction: null,
} satisfies BarrierReportCommand;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as BarrierReportCommand;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

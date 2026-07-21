# CreatePlanCommand

## Properties

| Name                   | Type                                                       |
| ---------------------- | ---------------------------------------------------------- |
| `participantAccountId` | string                                                     |
| `goalName`             | string                                                     |
| `planName`             | string                                                     |
| `cycleName`            | string                                                     |
| `microcycleName`       | string                                                     |
| `sessionTitle`         | string                                                     |
| `sessionKind`          | string                                                     |
| `prescriptions`        | [Array&lt;PrescriptionCommand&gt;](PrescriptionCommand.md) |

## Example

```typescript
import type { CreatePlanCommand } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  participantAccountId: null,
  goalName: null,
  planName: null,
  cycleName: null,
  microcycleName: null,
  sessionTitle: null,
  sessionKind: null,
  prescriptions: null,
} satisfies CreatePlanCommand;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CreatePlanCommand;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

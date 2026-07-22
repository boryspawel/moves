# ParticipantIssueCommand

## Properties

| Name          | Type   |
| ------------- | ------ |
| `problemCode` | string |
| `shortText`   | string |

## Example

```typescript
import type { ParticipantIssueCommand } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  problemCode: null,
  shortText: null,
} satisfies ParticipantIssueCommand;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ParticipantIssueCommand;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

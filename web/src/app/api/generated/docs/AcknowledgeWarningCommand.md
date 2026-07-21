# AcknowledgeWarningCommand

## Properties

| Name            | Type                              |
| --------------- | --------------------------------- |
| `factorIds`     | Set&lt;string&gt;                 |
| `rationale`     | string                            |
| `actingContext` | [ActingContext](ActingContext.md) |

## Example

```typescript
import type { AcknowledgeWarningCommand } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  factorIds: null,
  rationale: null,
  actingContext: null,
} satisfies AcknowledgeWarningCommand;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AcknowledgeWarningCommand;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

# AcknowledgementView

## Properties

| Name           | Type              |
| -------------- | ----------------- |
| `revisionId`   | string            |
| `assessmentId` | string            |
| `factorIds`    | Set&lt;string&gt; |

## Example

```typescript
import type { AcknowledgementView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  revisionId: null,
  assessmentId: null,
  factorIds: null,
} satisfies AcknowledgementView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AcknowledgementView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

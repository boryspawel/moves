# PublicationResult

## Properties

| Name                | Type                |
| ------------------- | ------------------- |
| `exerciseVersionId` | string              |
| `status`            | string              |
| `publishedAt`       | Date                |
| `version`           | number              |
| `unmetRequirements` | Array&lt;string&gt; |

## Example

```typescript
import type { PublicationResult } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  exerciseVersionId: null,
  status: null,
  publishedAt: null,
  version: null,
  unmetRequirements: null,
} satisfies PublicationResult;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PublicationResult;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

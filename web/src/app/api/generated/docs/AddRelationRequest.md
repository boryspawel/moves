# AddRelationRequest

## Properties

| Name           | Type   |
| -------------- | ------ |
| `parentId`     | string |
| `childId`      | string |
| `relationType` | string |

## Example

```typescript
import type { AddRelationRequest } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  parentId: null,
  childId: null,
  relationType: null,
} satisfies AddRelationRequest;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AddRelationRequest;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

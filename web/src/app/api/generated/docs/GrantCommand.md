# GrantCommand

## Properties

| Name                | Type              |
| ------------------- | ----------------- |
| `recipientId`       | string            |
| `purpose`           | string            |
| `templateVersionId` | string            |
| `dataScopes`        | Set&lt;string&gt; |
| `validFrom`         | Date              |
| `validTo`           | Date              |

## Example

```typescript
import type { GrantCommand } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  recipientId: null,
  purpose: null,
  templateVersionId: null,
  dataScopes: null,
  validFrom: null,
  validTo: null,
} satisfies GrantCommand;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as GrantCommand;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

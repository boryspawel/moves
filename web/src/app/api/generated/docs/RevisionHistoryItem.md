# RevisionHistoryItem

## Properties

| Name                | Type   |
| ------------------- | ------ |
| `revisionId`        | string |
| `revisionNumber`    | number |
| `basedOnRevisionId` | string |
| `status`            | string |
| `migrationOrigin`   | string |
| `assessmentStatus`  | string |
| `version`           | number |
| `createdAt`         | Date   |

## Example

```typescript
import type { RevisionHistoryItem } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  revisionId: null,
  revisionNumber: null,
  basedOnRevisionId: null,
  status: null,
  migrationOrigin: null,
  assessmentStatus: null,
  version: null,
  createdAt: null,
} satisfies RevisionHistoryItem;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RevisionHistoryItem;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

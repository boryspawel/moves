# IssueView

## Properties

| Name              | Type   |
| ----------------- | ------ |
| `id`              | string |
| `recordId`        | string |
| `rowNumber`       | number |
| `sourceRecordKey` | string |
| `code`            | string |
| `stage`           | string |
| `severity`        | string |
| `jsonPointer`     | string |
| `message`         | string |
| `createdAt`       | Date   |
| `resolvedAt`      | Date   |

## Example

```typescript
import type { IssueView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  recordId: null,
  rowNumber: null,
  sourceRecordKey: null,
  code: null,
  stage: null,
  severity: null,
  jsonPointer: null,
  message: null,
  createdAt: null,
  resolvedAt: null,
} satisfies IssueView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as IssueView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

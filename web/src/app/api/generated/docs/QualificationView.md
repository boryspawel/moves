# QualificationView

## Properties

| Name                | Type   |
| ------------------- | ------ |
| `ledgerEntryId`     | string |
| `sourceExecutionId` | string |
| `points`            | number |
| `outcome`           | string |
| `ruleVersion`       | string |

## Example

```typescript
import type { QualificationView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  ledgerEntryId: null,
  sourceExecutionId: null,
  points: null,
  outcome: null,
  ruleVersion: null,
} satisfies QualificationView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as QualificationView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

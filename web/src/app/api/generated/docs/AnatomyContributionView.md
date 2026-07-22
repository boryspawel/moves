# AnatomyContributionView

## Properties

| Name               | Type                                         |
| ------------------ | -------------------------------------------- |
| `code`             | string                                       |
| `displayName`      | string                                       |
| `structureType`    | string                                       |
| `role`             | string                                       |
| `loadChannel`      | string                                       |
| `contributionBand` | string                                       |
| `confidenceClass`  | string                                       |
| `evidenceGrade`    | string                                       |
| `evidence`         | [Array&lt;EvidenceView&gt;](EvidenceView.md) |

## Example

```typescript
import type { AnatomyContributionView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  code: null,
  displayName: null,
  structureType: null,
  role: null,
  loadChannel: null,
  contributionBand: null,
  confidenceClass: null,
  evidenceGrade: null,
  evidence: null,
} satisfies AnatomyContributionView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AnatomyContributionView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

# RestrictionView

## Properties

| Name                      | Type                        |
| ------------------------- | --------------------------- |
| `id`                      | string                      |
| `rootId`                  | string                      |
| `revisionNumber`          | number                      |
| `supersedesRestrictionId` | string                      |
| `participantId`           | string                      |
| `sourceType`              | string                      |
| `semanticType`            | string                      |
| `status`                  | string                      |
| `validFrom`               | Date                        |
| `validTo`                 | Date                        |
| `authorCapability`        | string                      |
| `participantExplanation`  | string                      |
| `target`                  | [TargetView](TargetView.md) |

## Example

```typescript
import type { RestrictionView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  rootId: null,
  revisionNumber: null,
  supersedesRestrictionId: null,
  participantId: null,
  sourceType: null,
  semanticType: null,
  status: null,
  validFrom: null,
  validTo: null,
  authorCapability: null,
  participantExplanation: null,
  target: null,
} satisfies RestrictionView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RestrictionView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

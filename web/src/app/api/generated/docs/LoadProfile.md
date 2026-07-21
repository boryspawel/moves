# LoadProfile

## Properties

| Name                    | Type                                       |
| ----------------------- | ------------------------------------------ |
| `snapshotId`            | string                                     |
| `revisionId`            | string                                     |
| `inputChecksum`         | string                                     |
| `algorithmVersion`      | string                                     |
| `configurationVersion`  | string                                     |
| `catalogProfileVersion` | string                                     |
| `calculatedAt`          | Date                                       |
| `observations`          | [Array&lt;Observation&gt;](Observation.md) |
| `aggregates`            | [Array&lt;Aggregate&gt;](Aggregate.md)     |

## Example

```typescript
import type { LoadProfile } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  snapshotId: null,
  revisionId: null,
  inputChecksum: null,
  algorithmVersion: null,
  configurationVersion: null,
  catalogProfileVersion: null,
  calculatedAt: null,
  observations: null,
  aggregates: null,
} satisfies LoadProfile;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as LoadProfile;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

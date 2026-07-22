# CandidateView

## Properties

| Name               | Type                    |
| ------------------ | ----------------------- |
| `id`               | string                  |
| `exerciseId`       | string                  |
| `rank`             | number                  |
| `score`            | number                  |
| `reasons`          | [JsonNode](JsonNode.md) |
| `algorithmVersion` | string                  |
| `decision`         | string                  |
| `decidedBySubject` | string                  |
| `decidedAt`        | Date                    |
| `version`          | number                  |

## Example

```typescript
import type { CandidateView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  exerciseId: null,
  rank: null,
  score: null,
  reasons: null,
  algorithmVersion: null,
  decision: null,
  decidedBySubject: null,
  decidedAt: null,
  version: null,
} satisfies CandidateView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CandidateView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

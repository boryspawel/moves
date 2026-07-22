# ReviewItem

## Properties

| Name              | Type   |
| ----------------- | ------ |
| `id`              | string |
| `area`            | string |
| `decision`        | string |
| `comment`         | string |
| `reviewerSubject` | string |
| `reviewedAt`      | Date   |
| `version`         | number |

## Example

```typescript
import type { ReviewItem } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  area: null,
  decision: null,
  comment: null,
  reviewerSubject: null,
  reviewedAt: null,
  version: null,
} satisfies ReviewItem;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ReviewItem;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

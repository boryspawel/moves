# RecordPage

## Properties

| Name            | Type                                           |
| --------------- | ---------------------------------------------- |
| `content`       | [Array&lt;RecordSummary&gt;](RecordSummary.md) |
| `page`          | number                                         |
| `size`          | number                                         |
| `totalElements` | number                                         |

## Example

```typescript
import type { RecordPage } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  content: null,
  page: null,
  size: null,
  totalElements: null,
} satisfies RecordPage;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RecordPage;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

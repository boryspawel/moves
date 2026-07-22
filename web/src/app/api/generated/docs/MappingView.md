# MappingView

## Properties

| Name               | Type   |
| ------------------ | ------ |
| `id`               | string |
| `sourceId`         | string |
| `dictionaryType`   | string |
| `sourceValue`      | string |
| `canonicalValue`   | string |
| `status`           | string |
| `decidedBySubject` | string |
| `decidedAt`        | Date   |
| `version`          | number |

## Example

```typescript
import type { MappingView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  sourceId: null,
  dictionaryType: null,
  sourceValue: null,
  canonicalValue: null,
  status: null,
  decidedBySubject: null,
  decidedAt: null,
  version: null,
} satisfies MappingView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as MappingView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

# SessionVariantSnapshot

## Properties

| Name                      | Type                                                                     |
| ------------------------- | ------------------------------------------------------------------------ |
| `id`                      | string                                                                   |
| `type`                    | string                                                                   |
| `expectedDurationMinutes` | number                                                                   |
| `items`                   | [Array&lt;SessionVariantItemSnapshot&gt;](SessionVariantItemSnapshot.md) |

## Example

```typescript
import type { SessionVariantSnapshot } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  type: null,
  expectedDurationMinutes: null,
  items: null,
} satisfies SessionVariantSnapshot;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SessionVariantSnapshot;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

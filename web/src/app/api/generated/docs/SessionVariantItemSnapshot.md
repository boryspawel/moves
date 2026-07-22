# SessionVariantItemSnapshot

## Properties

| Name                      | Type   |
| ------------------------- | ------ |
| `id`                      | string |
| `basePrescriptionId`      | string |
| `position`                | number |
| `overrideSets`            | number |
| `overrideRepetitions`     | number |
| `overrideDurationSeconds` | number |
| `overrideContacts`        | number |

## Example

```typescript
import type { SessionVariantItemSnapshot } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  basePrescriptionId: null,
  position: null,
  overrideSets: null,
  overrideRepetitions: null,
  overrideDurationSeconds: null,
  overrideContacts: null,
} satisfies SessionVariantItemSnapshot;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SessionVariantItemSnapshot;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

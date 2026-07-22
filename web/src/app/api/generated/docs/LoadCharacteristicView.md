# LoadCharacteristicView

## Properties

| Name                 | Type   |
| -------------------- | ------ |
| `id`                 | string |
| `movementPlane`      | string |
| `contractionType`    | string |
| `rangeOfMotion`      | string |
| `characteristicType` | string |

## Example

```typescript
import type { LoadCharacteristicView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  movementPlane: null,
  contractionType: null,
  rangeOfMotion: null,
  characteristicType: null,
} satisfies LoadCharacteristicView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as LoadCharacteristicView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

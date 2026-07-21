# PublishedExerciseVersionSnapshot

## Properties

| Name                   | Type                                                                     |
| ---------------------- | ------------------------------------------------------------------------ |
| `exerciseId`           | string                                                                   |
| `canonicalName`        | string                                                                   |
| `versionId`            | string                                                                   |
| `versionNumber`        | number                                                                   |
| `profileSchemaVersion` | number                                                                   |
| `movementPatterns`     | Set&lt;string&gt;                                                        |
| `requiredEquipment`    | Set&lt;string&gt;                                                        |
| `contributions`        | [Array&lt;ContributionSnapshot&gt;](ContributionSnapshot.md)             |
| `loadCharacteristics`  | [Array&lt;LoadCharacteristicSnapshot&gt;](LoadCharacteristicSnapshot.md) |

## Example

```typescript
import type { PublishedExerciseVersionSnapshot } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  exerciseId: null,
  canonicalName: null,
  versionId: null,
  versionNumber: null,
  profileSchemaVersion: null,
  movementPatterns: null,
  requiredEquipment: null,
  contributions: null,
  loadCharacteristics: null,
} satisfies PublishedExerciseVersionSnapshot;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PublishedExerciseVersionSnapshot;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

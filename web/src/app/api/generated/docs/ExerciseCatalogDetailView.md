# ExerciseCatalogDetailView

## Properties

| Name                   | Type                                                                           |
| ---------------------- | ------------------------------------------------------------------------------ |
| `exerciseId`           | string                                                                         |
| `versionId`            | string                                                                         |
| `versionNumber`        | number                                                                         |
| `canonicalName`        | string                                                                         |
| `instruction`          | string                                                                         |
| `movementPatterns`     | Array&lt;string&gt;                                                            |
| `stimulusType`         | string                                                                         |
| `fatigueProfile`       | string                                                                         |
| `technicalLevel`       | string                                                                         |
| `environment`          | string                                                                         |
| `requiredEquipment`    | Array&lt;string&gt;                                                            |
| `loadCharacteristics`  | [Array&lt;PublicLoadCharacteristicView&gt;](PublicLoadCharacteristicView.md)   |
| `anatomyContributions` | [Array&lt;PublicAnatomyContributionView&gt;](PublicAnatomyContributionView.md) |
| `evidence`             | [Array&lt;PublicEvidenceView&gt;](PublicEvidenceView.md)                       |

## Example

```typescript
import type { ExerciseCatalogDetailView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  exerciseId: null,
  versionId: null,
  versionNumber: null,
  canonicalName: null,
  instruction: null,
  movementPatterns: null,
  stimulusType: null,
  fatigueProfile: null,
  technicalLevel: null,
  environment: null,
  requiredEquipment: null,
  loadCharacteristics: null,
  anatomyContributions: null,
  evidence: null,
} satisfies ExerciseCatalogDetailView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ExerciseCatalogDetailView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

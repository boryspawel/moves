# VersionView

## Properties

| Name                   | Type              |
| ---------------------- | ----------------- |
| `exerciseId`           | string            |
| `canonicalName`        | string            |
| `versionId`            | string            |
| `versionNumber`        | number            |
| `status`               | string            |
| `movementPatterns`     | Set&lt;string&gt; |
| `instruction`          | string            |
| `mediaReference`       | string            |
| `stimulusType`         | string            |
| `fatigueProfile`       | string            |
| `technicalLevel`       | string            |
| `environment`          | string            |
| `requiredEquipment`    | Set&lt;string&gt; |
| `profileSchemaVersion` | number            |
| `reviewedBySubject`    | string            |
| `reviewedAt`           | Date              |
| `publishedAt`          | Date              |
| `withdrawnAt`          | Date              |

## Example

```typescript
import type { VersionView } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  exerciseId: null,
  canonicalName: null,
  versionId: null,
  versionNumber: null,
  status: null,
  movementPatterns: null,
  instruction: null,
  mediaReference: null,
  stimulusType: null,
  fatigueProfile: null,
  technicalLevel: null,
  environment: null,
  requiredEquipment: null,
  profileSchemaVersion: null,
  reviewedBySubject: null,
  reviewedAt: null,
  publishedAt: null,
  withdrawnAt: null,
} satisfies VersionView;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as VersionView;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

# ContributionSnapshot

## Properties

| Name                    | Type                                                 |
| ----------------------- | ---------------------------------------------------- |
| `id`                    | string                                               |
| `anatomicalStructureId` | string                                               |
| `role`                  | string                                               |
| `loadChannel`           | string                                               |
| `contributionBand`      | string                                               |
| `coefficientLow`        | number                                               |
| `coefficientHigh`       | number                                               |
| `confidenceClass`       | string                                               |
| `evidenceGrade`         | string                                               |
| `calculationRole`       | string                                               |
| `variantCondition`      | string                                               |
| `sideRule`              | string                                               |
| `evidence`              | [Array&lt;EvidenceSnapshot&gt;](EvidenceSnapshot.md) |

## Example

```typescript
import type { ContributionSnapshot } from '@moves/api-client';

// TODO: Update the object below with actual values
const example = {
  id: null,
  anatomicalStructureId: null,
  role: null,
  loadChannel: null,
  contributionBand: null,
  coefficientLow: null,
  coefficientHigh: null,
  confidenceClass: null,
  evidenceGrade: null,
  calculationRole: null,
  variantCondition: null,
  sideRule: null,
  evidence: null,
} satisfies ContributionSnapshot;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ContributionSnapshot;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

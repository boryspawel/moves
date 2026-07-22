
# PublicAnatomyContributionView


## Properties

Name | Type
------------ | -------------
`code` | string
`displayName` | string
`structureType` | string
`role` | string
`loadChannel` | string
`contributionBand` | string
`coefficientLow` | number
`coefficientHigh` | number
`confidenceClass` | string
`evidenceGrade` | string
`variantCondition` | string
`sideRule` | string
`evidence` | [Array&lt;PublicEvidenceView&gt;](PublicEvidenceView.md)

## Example

```typescript
import type { PublicAnatomyContributionView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "code": null,
  "displayName": null,
  "structureType": null,
  "role": null,
  "loadChannel": null,
  "contributionBand": null,
  "coefficientLow": null,
  "coefficientHigh": null,
  "confidenceClass": null,
  "evidenceGrade": null,
  "variantCondition": null,
  "sideRule": null,
  "evidence": null,
} satisfies PublicAnatomyContributionView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PublicAnatomyContributionView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

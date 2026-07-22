
# ResultView


## Properties

Name | Type
------------ | -------------
`exercisePrescriptionId` | string
`exerciseVersionId` | string
`actualSets` | number
`actualRepetitions` | number
`actualDurationSeconds` | number
`actualContacts` | number
`actualDistanceMeters` | number
`actualLoadKg` | number
`actualExternalLoadValue` | number
`actualExternalLoadUnit` | string
`actualIntensityType` | string
`actualIntensityValue` | number
`actualIntensityZone` | string
`side` | string
`modified` | boolean
`skipped` | boolean
`observationMode` | string

## Example

```typescript
import type { ResultView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "exercisePrescriptionId": null,
  "exerciseVersionId": null,
  "actualSets": null,
  "actualRepetitions": null,
  "actualDurationSeconds": null,
  "actualContacts": null,
  "actualDistanceMeters": null,
  "actualLoadKg": null,
  "actualExternalLoadValue": null,
  "actualExternalLoadUnit": null,
  "actualIntensityType": null,
  "actualIntensityValue": null,
  "actualIntensityZone": null,
  "side": null,
  "modified": null,
  "skipped": null,
  "observationMode": null,
} satisfies ResultView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ResultView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

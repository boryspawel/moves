
# AddPrescriptionCommand


## Properties

Name | Type
------------ | -------------
`expectedVersion` | number
`sessionId` | string
`exerciseVersionId` | string
`position` | number
`side` | string
`doseType` | string
`sets` | number
`repetitions` | number
`durationSeconds` | number
`distanceMeters` | number
`contacts` | number
`externalLoadValue` | number
`externalLoadUnit` | string
`intensityType` | string
`intensityValue` | number
`intensityZone` | string
`tempo` | string
`rangeOfMotion` | string
`restSeconds` | number
`substituteGroup` | string
`notes` | string

## Example

```typescript
import type { AddPrescriptionCommand } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "expectedVersion": null,
  "sessionId": null,
  "exerciseVersionId": null,
  "position": null,
  "side": null,
  "doseType": null,
  "sets": null,
  "repetitions": null,
  "durationSeconds": null,
  "distanceMeters": null,
  "contacts": null,
  "externalLoadValue": null,
  "externalLoadUnit": null,
  "intensityType": null,
  "intensityValue": null,
  "intensityZone": null,
  "tempo": null,
  "rangeOfMotion": null,
  "restSeconds": null,
  "substituteGroup": null,
  "notes": null,
} satisfies AddPrescriptionCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AddPrescriptionCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

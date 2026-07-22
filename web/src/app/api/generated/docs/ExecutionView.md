
# ExecutionView


## Properties

Name | Type
------------ | -------------
`id` | string
`plannedSessionId` | string
`participantAccountId` | string
`declaredCompletion` | boolean
`recordedAt` | Date
`painLevel` | number
`difficultyLevel` | number
`techniqueConfidenceLevel` | number
`note` | string
`sessionRpe` | number
`observationMode` | string
`results` | [Array&lt;ResultView&gt;](ResultView.md)
`corrections` | [Array&lt;CorrectionView&gt;](CorrectionView.md)
`alerts` | Array&lt;string&gt;
`safetyAlerts` | [Array&lt;AlertData&gt;](AlertData.md)

## Example

```typescript
import type { ExecutionView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "plannedSessionId": null,
  "participantAccountId": null,
  "declaredCompletion": null,
  "recordedAt": null,
  "painLevel": null,
  "difficultyLevel": null,
  "techniqueConfidenceLevel": null,
  "note": null,
  "sessionRpe": null,
  "observationMode": null,
  "results": null,
  "corrections": null,
  "alerts": null,
  "safetyAlerts": null,
} satisfies ExecutionView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ExecutionView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

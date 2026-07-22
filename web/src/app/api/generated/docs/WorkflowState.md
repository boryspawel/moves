
# WorkflowState


## Properties

Name | Type
------------ | -------------
`revisionId` | string
`planId` | string
`participantId` | string
`ownerId` | string
`mode` | string
`revisionStatus` | string
`revisionVersion` | number
`validationChecksum` | string
`loadSnapshotId` | string
`assessmentId` | string
`currentRevisionId` | string

## Example

```typescript
import type { WorkflowState } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "revisionId": null,
  "planId": null,
  "participantId": null,
  "ownerId": null,
  "mode": null,
  "revisionStatus": null,
  "revisionVersion": null,
  "validationChecksum": null,
  "loadSnapshotId": null,
  "assessmentId": null,
  "currentRevisionId": null,
} satisfies WorkflowState

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowState
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

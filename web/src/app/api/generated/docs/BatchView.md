
# BatchView


## Properties

Name | Type
------------ | -------------
`id` | string
`sourceId` | string
`requestKey` | string
`status` | string
`forcedFromBatchId` | string
`submittedBySubject` | string
`submittedAt` | Date
`startedAt` | Date
`completedAt` | Date
`totalCount` | number
`validCount` | number
`invalidCount` | number
`blockedCount` | number
`draftedCount` | number
`unchangedCount` | number
`version` | number
`artifact` | [ArtifactView](ArtifactView.md)

## Example

```typescript
import type { BatchView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "sourceId": null,
  "requestKey": null,
  "status": null,
  "forcedFromBatchId": null,
  "submittedBySubject": null,
  "submittedAt": null,
  "startedAt": null,
  "completedAt": null,
  "totalCount": null,
  "validCount": null,
  "invalidCount": null,
  "blockedCount": null,
  "draftedCount": null,
  "unchangedCount": null,
  "version": null,
  "artifact": null,
} satisfies BatchView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as BatchView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

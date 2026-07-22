
# RecordDetail


## Properties

Name | Type
------------ | -------------
`id` | string
`batchId` | string
`rowNumber` | number
`sourceRecordKey` | string
`status` | string
`raw` | [JsonNode](JsonNode.md)
`normalized` | [JsonNode](JsonNode.md)
`rawSha256` | string
`normalizedSha256` | string
`normalizationVersion` | string
`matchedExerciseId` | string
`draftVersionId` | string
`createdAt` | Date
`updatedAt` | Date
`version` | number
`issues` | [Array&lt;IssueView&gt;](IssueView.md)
`matchCandidates` | [Array&lt;CandidateView&gt;](CandidateView.md)

## Example

```typescript
import type { RecordDetail } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "batchId": null,
  "rowNumber": null,
  "sourceRecordKey": null,
  "status": null,
  "raw": null,
  "normalized": null,
  "rawSha256": null,
  "normalizedSha256": null,
  "normalizationVersion": null,
  "matchedExerciseId": null,
  "draftVersionId": null,
  "createdAt": null,
  "updatedAt": null,
  "version": null,
  "issues": null,
  "matchCandidates": null,
} satisfies RecordDetail

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RecordDetail
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

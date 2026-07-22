
# RecordSummary


## Properties

Name | Type
------------ | -------------
`id` | string
`rowNumber` | number
`sourceRecordKey` | string
`status` | string
`rawSha256` | string
`normalizedSha256` | string
`matchedExerciseId` | string
`draftVersionId` | string
`version` | number

## Example

```typescript
import type { RecordSummary } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "rowNumber": null,
  "sourceRecordKey": null,
  "status": null,
  "rawSha256": null,
  "normalizedSha256": null,
  "matchedExerciseId": null,
  "draftVersionId": null,
  "version": null,
} satisfies RecordSummary

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RecordSummary
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

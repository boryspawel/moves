
# VersionDiff


## Properties

Name | Type
------------ | -------------
`versionId` | string
`exerciseId` | string
`versionNumber` | number
`status` | string
`draftSemanticSha256` | string
`normalizedSource` | string
`currentPublishedSemanticSha256` | string

## Example

```typescript
import type { VersionDiff } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "versionId": null,
  "exerciseId": null,
  "versionNumber": null,
  "status": null,
  "draftSemanticSha256": null,
  "normalizedSource": null,
  "currentPublishedSemanticSha256": null,
} satisfies VersionDiff

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as VersionDiff
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

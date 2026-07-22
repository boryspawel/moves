
# AttemptDetailView


## Properties

Name | Type
------------ | -------------
`attemptId` | string
`plannedSessionId` | string
`planRevisionId` | string
`selectedVariantType` | string
`state` | string
`startedAt` | Date
`lastActivityAt` | Date
`abandonmentReason` | string
`progress` | [Array&lt;ProgressView&gt;](ProgressView.md)

## Example

```typescript
import type { AttemptDetailView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "attemptId": null,
  "plannedSessionId": null,
  "planRevisionId": null,
  "selectedVariantType": null,
  "state": null,
  "startedAt": null,
  "lastActivityAt": null,
  "abandonmentReason": null,
  "progress": null,
} satisfies AttemptDetailView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AttemptDetailView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


# WorklistItemView


## Properties

Name | Type
------------ | -------------
`id` | string
`participantAccountId` | string
`planRevisionId` | string
`category` | string
`priority` | string
`reasonCode` | string
`minimalData` | string
`policyVersion` | string
`status` | string
`snoozedUntil` | Date
`issueText` | string
`replies` | [Array&lt;ReplyView&gt;](ReplyView.md)

## Example

```typescript
import type { WorklistItemView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "participantAccountId": null,
  "planRevisionId": null,
  "category": null,
  "priority": null,
  "reasonCode": null,
  "minimalData": null,
  "policyVersion": null,
  "status": null,
  "snoozedUntil": null,
  "issueText": null,
  "replies": null,
} satisfies WorklistItemView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorklistItemView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

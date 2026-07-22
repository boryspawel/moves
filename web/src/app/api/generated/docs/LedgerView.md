
# LedgerView


## Properties

Name | Type
------------ | -------------
`id` | string
`sourceExecutionId` | string
`type` | string
`points` | number
`reason` | string
`reversesEntryId` | string
`occurredAt` | Date

## Example

```typescript
import type { LedgerView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "sourceExecutionId": null,
  "type": null,
  "points": null,
  "reason": null,
  "reversesEntryId": null,
  "occurredAt": null,
} satisfies LedgerView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as LedgerView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

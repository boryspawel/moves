
# AlertData


## Properties

Name | Type
------------ | -------------
`id` | string
`sessionExecutionId` | string
`alertType` | string
`priority` | string
`ownerAccountId` | string
`status` | string
`dueAt` | Date
`sourceResponseId` | string
`createdAt` | Date

## Example

```typescript
import type { AlertData } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "sessionExecutionId": null,
  "alertType": null,
  "priority": null,
  "ownerAccountId": null,
  "status": null,
  "dueAt": null,
  "sourceResponseId": null,
  "createdAt": null,
} satisfies AlertData

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AlertData
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

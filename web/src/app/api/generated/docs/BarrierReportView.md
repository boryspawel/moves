
# BarrierReportView


## Properties

Name | Type
------------ | -------------
`id` | string
`plannedSessionId` | string
`sessionAttemptId` | string
`category` | string
`proposedOptions` | Array&lt;string&gt;
`selectedAction` | string
`actionOutcome` | string
`ruleVersion` | string
`reportedAt` | Date

## Example

```typescript
import type { BarrierReportView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "plannedSessionId": null,
  "sessionAttemptId": null,
  "category": null,
  "proposedOptions": null,
  "selectedAction": null,
  "actionOutcome": null,
  "ruleVersion": null,
  "reportedAt": null,
} satisfies BarrierReportView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as BarrierReportView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

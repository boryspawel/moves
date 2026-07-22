
# PlanRevisionSnapshot


## Properties

Name | Type
------------ | -------------
`revisionId` | string
`planId` | string
`participantAccountId` | string
`revisionNumber` | number
`basedOnRevisionId` | string
`revisionVersion` | number
`status` | string
`authorAccountId` | string
`authorCapability` | string
`createdAt` | Date
`migrationOrigin` | string
`assessmentStatus` | string
`phaseIntent` | string
`validFrom` | Date
`validTo` | Date
`goals` | [Array&lt;GoalSnapshot&gt;](GoalSnapshot.md)
`cycles` | [Array&lt;CycleSnapshot&gt;](CycleSnapshot.md)
`loadBudgets` | [Array&lt;LoadBudgetSnapshot&gt;](LoadBudgetSnapshot.md)

## Example

```typescript
import type { PlanRevisionSnapshot } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "revisionId": null,
  "planId": null,
  "participantAccountId": null,
  "revisionNumber": null,
  "basedOnRevisionId": null,
  "revisionVersion": null,
  "status": null,
  "authorAccountId": null,
  "authorCapability": null,
  "createdAt": null,
  "migrationOrigin": null,
  "assessmentStatus": null,
  "phaseIntent": null,
  "validFrom": null,
  "validTo": null,
  "goals": null,
  "cycles": null,
  "loadBudgets": null,
} satisfies PlanRevisionSnapshot

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PlanRevisionSnapshot
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

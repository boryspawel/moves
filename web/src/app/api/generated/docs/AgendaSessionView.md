
# AgendaSessionView


## Properties

Name | Type
------------ | -------------
`sessionId` | string
`title` | string
`expectedDurationMinutes` | number
`scheduledDate` | Date
`availableFrom` | Date
`availableTo` | Date
`executionState` | string
`doseSummary` | string
`safetyState` | string
`nextAction` | string
`sortAt` | Date

## Example

```typescript
import type { AgendaSessionView } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "sessionId": null,
  "title": null,
  "expectedDurationMinutes": null,
  "scheduledDate": null,
  "availableFrom": null,
  "availableTo": null,
  "executionState": null,
  "doseSummary": null,
  "safetyState": null,
  "nextAction": null,
  "sortAt": null,
} satisfies AgendaSessionView

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AgendaSessionView
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

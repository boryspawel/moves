
# CycleSnapshot


## Properties

Name | Type
------------ | -------------
`id` | string
`sequenceNumber` | number
`name` | string
`startDate` | Date
`endDate` | Date
`phaseIntent` | string
`phaseGoal` | string
`microcycles` | [Array&lt;MicrocycleSnapshot&gt;](MicrocycleSnapshot.md)

## Example

```typescript
import type { CycleSnapshot } from '@moves/api-client'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "sequenceNumber": null,
  "name": null,
  "startDate": null,
  "endDate": null,
  "phaseIntent": null,
  "phaseGoal": null,
  "microcycles": null,
} satisfies CycleSnapshot

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CycleSnapshot
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
